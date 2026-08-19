package com.abin.checkrepeatservice.common.Exception;

import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.Getter;

import java.util.Map;

/**
 * 参数无效异常：仅关联“参数相关”的ResultCode（如 PARAM_EMPTY、PARAM_FORMAT_ERROR）
 */
@Getter
public class ParamInvalidException extends BusinessException {
    // 错误参数列表（key=参数名，value=参数值，如 Map.of("paperId", null)）
    private final Map<String, Object> invalidParams;

    /**
     * 构造器：强制关联参数相关ResultCode，确保错误类型不越界
     * @param resultCode 仅允许参数相关枚举（如 ResultCode.PARAM_EMPTY）
     * @param invalidParams 错误参数列表（必传，明确哪个参数错）
     * @param customMsg 自定义错误信息（可为null）
     */
    public ParamInvalidException(ResultCode resultCode, Map<String, Object> invalidParams, String customMsg) {
        // 父类：传入枚举、自定义信息、额外数据（错误参数列表）
        super(resultCode, customMsg, invalidParams);
        this.invalidParams = invalidParams;
        // 校验：确保传入的是参数相关枚举，避免错误使用其他类型枚举
        if (!resultCode.getCode().toString().startsWith("400")) {
            throw new IllegalArgumentException("ParamInvalidException 仅支持参数相关ResultCode（400开头）");
        }
    }

    // -------------------------- 快捷构造方法（简化调用） --------------------------
    /**
     * 快捷1：参数为空（如 paperId=null）
     */
    public static ParamInvalidException ofEmpty(String paramName) {
        Map<String, Object> params = Map.of(paramName, null);
        String customMsg = String.format("参数【%s】不能为空", paramName);
        return new ParamInvalidException(ResultCode.PARAM_EMPTY, params, customMsg);
    }

    /**
     * 快捷2：参数格式错误（如 advisorRound=-1）
     */
    public static ParamInvalidException ofFormat(String paramName, Object paramValue, String formatRule) {
        Map<String, Object> params = Map.of(paramName, paramValue);
        String customMsg = String.format("参数【%s】格式错误（当前值：%s，规则：%s）", paramName, paramValue, formatRule);
        return new ParamInvalidException(ResultCode.PARAM_FORMAT_ERROR, params, customMsg);
    }

    /**
     * 快捷3：参数类型错误（如 paperId传入字符串）
     */
    public static ParamInvalidException ofType(String paramName, Object paramValue, Class<?> requiredType) {
        Map<String, Object> params = Map.of(paramName, paramValue);
        String customMsg = String.format("参数【%s】类型错误（当前：%s，需传入：%s）",
                paramName, paramValue.getClass().getSimpleName(), requiredType.getSimpleName());
        return new ParamInvalidException(ResultCode.PARAM_TYPE_ERROR, params, customMsg);
    }
}