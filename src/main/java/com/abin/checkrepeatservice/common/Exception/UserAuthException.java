package com.abin.checkrepeatservice.common.Exception;


/**
 * 用户认证相关异常
 */
public class UserAuthException extends BaseException {
    public UserAuthException(String message) {
        super(message);
    }

    public UserAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
