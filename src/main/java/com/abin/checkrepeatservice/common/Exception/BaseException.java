package com.abin.checkrepeatservice.common.Exception;

/**
 * 基础异常类
 */
public class BaseException extends RuntimeException {
    public BaseException(String message) {
        super(message);
    }

    public BaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
