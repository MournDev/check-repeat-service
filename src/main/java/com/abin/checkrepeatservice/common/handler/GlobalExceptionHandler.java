package com.abin.checkrepeatservice.common.handler;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    // -------------------------- 3. 系统异常（兜底处理） --------------------------
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