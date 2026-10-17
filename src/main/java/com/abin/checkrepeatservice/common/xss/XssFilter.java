package com.abin.checkrepeatservice.common.xss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 全局 XSS 输入过滤器（安全修复 S9）。
 *
 * <p>在 Spring Security 链最前端（JwtAuthenticationFilter 之前）执行，对匿名接口
 * （登录/注册/找回密码/报告验真）同样生效——这些恰恰是匿名攻击者唯一能触达的写入面。</p>
 *
 * <p>分流策略：</p>
 * <ul>
 *   <li><b>application/json</b>（POST/PUT/PATCH/DELETE）：读入 body → Jackson 树
 *       递归清洗每个字符串节点（按其字段名选策略）→ 重写 body 放行；</li>
 *   <li><b>查询串 / form-urlencoded</b>：由 {@link XssHttpServletRequestWrapper}
 *       在 getParameter 时清洗；</li>
 *   <li><b>multipart/form-data</b>：整个跳过。包装输入流会破坏容器对文件分片的
 *       解析；唯一的富文本 multipart 字段（教师评阅意见 reviewOpinion）已在
 *       TeacherReviewServiceImpl 用同款 Jsoup 白名单清洗；</li>
 *   <li><b>超限 / 非法 JSON</b>：原样放行。超限为避免大包放大堆内存；非法 JSON
 *       交给 Spring MVC 的 Jackson 绑定正常返回 400，过滤器不吞请求、不改语义。</li>
 * </ul>
 *
 * <p>失败兜底：清洗过程出现任何异常均放行原始请求（fail-open）并记录 error 日志——
 * 安全组件故障不应导致全站接口不可用；输出侧（前端插值转义）仍是独立防线。</p>
 */
@Slf4j
@Component
public class XssFilter extends OncePerRequestFilter {

    /** 仅这些方法可能携带需要清洗的 body */
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * JSON 读写静态单例：过滤器处于每个请求的热路径，禁止每次 new。
     * 数字统一按 BigDecimal/BigInteger 承载，树重写后数值与原 JSON 等价
     * （极端格式可能以科学计数法写回，JSON.parse 结果不变）。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);

    @Resource
    private XssProperties properties;

    @Resource
    private XssCleaner cleaner;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        // multipart 整体跳过（理由见类注释）
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return true;
        }
        // 路径前缀白名单（servletPath 相对 context-path）
        String servletPath = request.getServletPath();
        if (servletPath != null) {
            for (String excluded : properties.getExcludes()) {
                if (servletPath.startsWith(excluded)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws jakarta.servlet.ServletException, java.io.IOException {
        byte[] cleanedBody = null;
        try {
            cleanedBody = sanitizeJsonBodyIfNeeded(request);
        } catch (Exception e) {
            // 任何意外都不能阻断业务：放行原始请求（fail-open）
            log.error("XSS 过滤器处理 JSON body 异常，本次放行原始请求 - path: {}",
                    request.getServletPath(), e);
            cleanedBody = null;
        }
        filterChain.doFilter(
                new XssHttpServletRequestWrapper(request, cleaner, cleanedBody),
                response);
    }

    /**
     * 若为 JSON body 请求则读入并递归清洗，返回重写后的字节；
     * 其余情况（GET、form、无 body、超限、非法 JSON）返回 null。
     */
    private byte[] sanitizeJsonBodyIfNeeded(HttpServletRequest request) throws Exception {
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE)
                || !BODY_METHODS.contains(request.getMethod().toUpperCase())) {
            return null;
        }

        // Content-Length 已知且超限时直接不处理（chunked 请求读入后再判）
        int declaredLength = request.getContentLength();
        if (declaredLength > properties.getMaxBodySize()) {
            log.debug("JSON body 超过 XSS 缓存上限，跳过 body 清洗 - length: {}, path: {}",
                    declaredLength, request.getServletPath());
            return null;
        }

        byte[] rawBody = StreamUtils.copyToByteArray(request.getInputStream());
        if (rawBody.length == 0) {
            return null;
        }
        if (rawBody.length > properties.getMaxBodySize()) {
            log.debug("JSON body（chunked）超过 XSS 缓存上限，跳过 body 清洗 - length: {}, path: {}",
                    rawBody.length, request.getServletPath());
            return null;
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(rawBody);
        } catch (Exception parseError) {
            // 非法 JSON：交给下游 Jackson 绑定返回 400，这里保持原始字节不动
            return null;
        }
        if (root == null || root.isNull()) {
            return null;
        }

        sanitizeNode(root, null);
        return OBJECT_MAPPER.writeValueAsBytes(root);
    }

    /**
     * 递归清洗 JSON 树中的字符串节点。
     *
     * @param node      当前节点（会被原地修改）
     * @param fieldName 该节点对应的字段名；数组元素继承数组字段名（同一 DTO 字段的集合，
     *                  策略应一致，例如 tags 数组内每个元素都按 tags 策略处理）
     */
    private void sanitizeNode(JsonNode node, String fieldName) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode child = entry.getValue();
                if (child.isTextual()) {
                    objectNode.put(name, cleaner.clean(child.asText(), name));
                } else if (child.isValueNode()) {
                    // 数字/布尔/null 原样
                } else {
                    sanitizeNode(child, name);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                if (child.isTextual()) {
                    arrayNode.set(i, TextNode.valueOf(cleaner.clean(child.asText(), fieldName)));
                } else if (!child.isValueNode()) {
                    sanitizeNode(child, fieldName);
                }
            }
        }
    }
}
