package com.abin.checkrepeatservice.common.xss;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 请求包装器（安全修复 S9）。
 *
 * <p>两类入口、同一清洗决策（{@link XssCleaner#clean(String, String)}）：</p>
 * <ul>
 *   <li><b>查询串 / 表单参数</b>：覆盖 getParameter / getParameterValues /
 *       getParameterMap，按参数名选策略清洗。对 GET 查询参数与
 *       application/x-www-form-urlencoded 的 POST 均生效（容器先解析表单，
 *       我们只包读取结果，不碰 body 字节流）；</li>
 *   <li><b>JSON 请求体</b>：过滤器已完成「读入 → 递归清洗 → 重写」，本类持有
 *       清洗后的字节，覆盖 getInputStream/getReader 供下游（Jackson 绑定 @RequestBody）
 *       重复读取，并同步覆盖 Content-Length（转义后字节数通常变大）。</li>
 * </ul>
 *
 * <p><b>不做的事</b>：不包装 multipart/form-data（会破坏容器文件上传解析，
 * multipart 整体在 {@link XssFilter} 跳过）；不清洗请求头（Authorization、
 * Cookie 等含任意字符且不会作为 HTML 渲染）。</p>
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /** 清洗后的 JSON body；null 表示本次请求不重写 body（参数模式或 GET） */
    private final byte[] cleanedBody;

    private final XssCleaner cleaner;

    public XssHttpServletRequestWrapper(HttpServletRequest request,
                                        XssCleaner cleaner,
                                        byte[] cleanedBody) {
        super(request);
        this.cleaner = cleaner;
        this.cleanedBody = cleanedBody;
    }

    // ============================ 查询串/表单参数 ============================

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return value == null ? null : cleaner.clean(value, name);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = values[i] == null ? null : cleaner.clean(values[i], name);
        }
        return cleaned;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> original = super.getParameterMap();
        // 返回新 Map，避免修改容器内部不可变/缓存结构
        Map<String, String[]> result = new LinkedHashMap<>(original.size());
        for (Map.Entry<String, String[]> entry : original.entrySet()) {
            result.put(entry.getKey(), getParameterValues(entry.getKey()));
        }
        return result;
    }

    // ============================== JSON body ==============================

    @Override
    public ServletInputStream getInputStream() throws java.io.IOException {
        if (cleanedBody == null) {
            // 非 JSON 重写模式：保持容器原始流（multipart 不会走到这里，已在 filter 跳过）
            return super.getInputStream();
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(cleanedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 同步字节流，不支持异步读监听
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() {
                return bais.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws java.io.IOException {
        if (cleanedBody == null) {
            return super.getReader();
        }
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cleanedBody), charset));
    }

    @Override
    public int getContentLength() {
        return cleanedBody == null ? super.getContentLength() : cleanedBody.length;
    }

    @Override
    public long getContentLengthLong() {
        return cleanedBody == null ? super.getContentLengthLong() : cleanedBody.length;
    }
}
