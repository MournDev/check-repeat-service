package com.abin.checkrepeatservice.common.handler;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理器：基于ResultCode枚举统一响应格式
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // -------------------------- 1. 自定义业务异常（优先级最高） --------------------------
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        ResultCode resultCode = e.getResultCode();
        // 日志：打印异常信息（包含错误码、额外数据）
        log.info("业务异常：code={}, msg={}, extraData={}",
                resultCode.getCode(), e.getMessage(), e.getExtraData(), e);

        // 构造统一Result响应（直接从枚举获取错误码、HTTP状态码）
        Result<?> result = Result.error(
                resultCode.getCode(),    // 业务错误码（如 40001）
                e.getMessage(),          // 错误信息（自定义或枚举默认）
                e.getExtraData()         // 额外数据（错误参数、资源ID等）
        );

        // 返回：HTTP状态码与枚举绑定（如 400、404）
        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }

    // -------------------------- 2. 框架参数类型异常（参考原有写法） --------------------------
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<String>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{}", e.getMessage(), e);
        // 关联参数类型错误的ResultCode
        ResultCode resultCode = ResultCode.PARAM_TYPE_ERROR;
        String customMsg = String.format("参数【%s】类型错误，需传入【%s】类型",
                e.getName(), e.getRequiredType().getSimpleName());

        // 构造统一Result响应
        Result<String> result = Result.error(
                resultCode.getCode(),
                customMsg,
                null
        );

        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }

    // -------------------------- 3. 请求体参数校验失败（@Valid） --------------------------
    /*
     * 没有本方法时，@Valid 失败抛出的 MethodArgumentNotValidException 会落到下面的
     * Exception 兜底，返回 500 +「系统繁忙，请稍后重试」——把客户端传错参数报成了服务端故障。
     * 例：找回密码漏传验证码。此处取第一条字段错误信息，按 400 返回。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String customMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ResultCode.PARAM_ERROR.getDefaultMsg());
        log.warn("参数校验失败：{}", customMsg);

        ResultCode resultCode = ResultCode.PARAM_ERROR;
        Result<?> result = Result.error(resultCode.getCode(), customMsg, null);
        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }

    // -------------------------- 4. 未映射路径（404 而非 500） --------------------------
    /*
     * Spring Boot 3 / Spring 6 对匹配不到任何 handler 的请求会抛 NoResourceFoundException
     * （消息形如 "No static resource api/admin/monitoring/metrics"）。
     * 没有本方法时它会落到下面的 Exception 兜底，被当成系统异常返回 500 + "系统繁忙"，
     * 掩盖了「接口不存在」这个真正的原因。此处显式映射为 404。
     * 不要改用 spring.mvc.throw-exception-if-no-handler-found + 关闭静态资源映射的方案——
     * 那是为一个 handler 就能解决的问题全局关掉静态资源服务。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getMessage());
        ResultCode resultCode = ResultCode.RESOURCE_NOT_FOUND;
        Result<?> result = Result.error(
                resultCode.getCode(),
                resultCode.getDefaultMsg(),
                null
        );
        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }

    // -------------------------- 5. 方法级权限拒绝（@PreAuthorize → 403 而非 500） --------------------------
    /*
     * @EnableMethodSecurity 的 @PreAuthorize 拒绝时抛 AccessDeniedException。此前没有
     * 对应处理器，会落进下面的 Exception 兜底 → 500 +「系统繁忙」：前端把"无权限"
     * 当成服务端故障展示，排查日志里也只会看到一条误导性的 ERROR。
     *
     * 范围说明：只有「已认证用户触发方法级注解拒绝」才会到这里 —— 匿名请求在
     * 过滤器链上就被拦下走 entry point 了；过滤器链规则的 403 由
     * AccessDeniedHandlerImpl 直接 sendError，同样不走本方法（其 401 冒充问题
     * 已由 SecurityConfig 的 ERROR 转发放行规则修正）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(AccessDeniedException e) {
        log.warn("方法级权限拒绝：{}", e.getMessage());
        ResultCode resultCode = ResultCode.PERMISSION_NO_ACCESS;
        Result<?> result = Result.error(
                resultCode.getCode(),
                resultCode.getDefaultMsg(),
                null
        );
        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }

    // -------------------------- 6. 系统异常（兜底处理） --------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleSystemException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        // 关联系统错误的ResultCode
        ResultCode resultCode = ResultCode.SYSTEM_ERROR;

        Result<?> result = Result.error(
                resultCode.getCode(),
                resultCode.getDefaultMsg(),  // 使用枚举默认信息，避免暴露技术细节
                null
        );

        return new ResponseEntity<>(result, resultCode.getHttpStatus());
    }
}