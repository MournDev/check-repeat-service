package com.abin.checkrepeatservice.common.Exception;

import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.Getter;

/**
 * 基础业务异常（父类）：绑定ResultCode枚举，统一错误码来源
 */
@Getter
public class BusinessException extends RuntimeException {
    // 错误码枚举（核心：替代原硬编码errorCode和httpStatus）
    private final ResultCode resultCode;
    // 额外业务数据（如错误参数、资源ID，供前端展示）
    private final Object extraData;

    /**
     * 全参构造器（推荐）：支持自定义错误信息+额外数据
     * @param resultCode 错误码枚举（必传）
     * @param customMsg 自定义错误信息（覆盖枚举默认信息，可为null）
     * @param extraData 额外业务数据（可为null）
     */
    public BusinessException(ResultCode resultCode, String customMsg, Object extraData) {
        super(customMsg != null ? customMsg : resultCode.getDefaultMsg());
        this.resultCode = resultCode;
        this.extraData = extraData;
    }

    /**
     * 简化构造1：使用枚举默认信息+额外数据
     */
    public BusinessException(ResultCode resultCode, Object extraData) {
        this(resultCode, null, extraData);
    }

    /**
     * 简化构造2：使用枚举默认信息，无额外数据
     */
    public BusinessException(ResultCode resultCode) {
        this(resultCode, null, null);
    }
}