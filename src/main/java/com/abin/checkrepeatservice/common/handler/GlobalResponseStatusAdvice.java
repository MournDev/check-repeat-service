package com.abin.checkrepeatservice.common.handler;

import com.abin.checkrepeatservice.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应状态码兜底（阶段 5）。
 * <p>本切面在响应体写出前，把 {@link Result#getCode()}（设计上就是 HTTP 状态码）同步到
 * 真正的 HTTP 状态；已经由 {@code ResponseEntity} 设置过状态或响应已提交时不重复设置。</p>
 */
@ControllerAdvice
public class GlobalResponseStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Result<?> result
                && result.getCode() != null
                && result.getCode() >= 100
                && result.getCode() <= 599
                && response instanceof ServletServerHttpResponse servletResponse) {
            HttpServletResponse httpResponse = servletResponse.getServletResponse();
            if (!httpResponse.isCommitted()) {
                httpResponse.setStatus(result.getCode());
            }
        }
        return body;
    }
}
