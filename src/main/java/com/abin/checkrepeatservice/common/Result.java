package com.abin.checkrepeatservice.common;

import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应VO（适配ResultCode枚举，确保格式一致）
 * 响应格式说明：
 * - 成功：code=200，errorCode=null，message=操作成功，data=业务数据
 * - 失败：code=HTTP状态码（如400），errorCode=业务错误码（如40001），message=错误信息，data=额外数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    // HTTP状态码（成功=200，失败=对应HTTP状态码如400/403/404）
    private Integer code;
    // 业务错误码（成功=null，失败=ResultCode枚举的code如40001）
    private Integer errorCode;
    // 提示信息（成功=“操作成功”，失败=具体错误描述）
    private String message;
    // 响应数据（成功=业务数据，失败=额外信息如错误参数列表）
    private T data;

    // ====================== 1. 成功响应（简化构造） ======================
    /**
     * 基础成功响应（无业务数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(200, null, "操作成功", null);
    }

    /**
     * 带业务数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, null, "操作成功", data);
    }

    /**
     * 自定义成功提示+业务数据
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, null, message, data);
    }

    /**
     * 自定义成功提示（无数据）
     */
    public static <T> Result<T> success(String message) {
        return new Result<>(200, null, message, null);
    }

    // ====================== 2. 失败响应（与ResultCode强绑定） ======================
    /**
     * 基于ResultCode的失败响应（使用枚举默认提示+无额外数据）
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(
                resultCode.getHttpStatus().value(),  // HTTP状态码（如400）
                resultCode.getCode(),                // 业务错误码（如40001）
                resultCode.getDefaultMsg(),          // 枚举默认错误提示
                null                                 // 额外数据（无）
        );
    }

    /**
     * 基于ResultCode的失败响应（自定义提示+无额外数据）
     */
    public static <T> Result<T> error(ResultCode resultCode, String customMsg) {
        return new Result<>(
                resultCode.getHttpStatus().value(),
                resultCode.getCode(),
                customMsg,
                null
        );
    }

    /**
     * 基于ResultCode的失败响应（自定义提示+额外数据）
     * （最常用：如参数错误时返回错误参数列表，资源错误时返回资源ID）
     */
    public static <T> Result<T> error(ResultCode resultCode, String customMsg, T extraData) {
        return new Result<>(
                resultCode.getHttpStatus().value(),
                resultCode.getCode(),
                customMsg,
                extraData
        );
    }

    /**
     * 简化：仅传入业务错误码+提示+额外数据（内部自动匹配HTTP状态码）
     * （适配全局异常处理器中“从异常获取错误码”的场景）
     */
    public static <T> Result<T> error(Integer errorCode, String message, T extraData) {
        // 从业务错误码推导HTTP状态码（规则：错误码前3位=HTTP状态码，如40001→400）
        Integer httpCode = Integer.parseInt(errorCode.toString().substring(0, 3));
        return new Result<>(httpCode, errorCode, message, extraData);
    }

    /**
     * 简化：仅传入业务错误码+提示（无额外数据）
     */
    public static <T> Result<T> error(Integer errorCode, String message) {
        Integer httpCode = Integer.parseInt(errorCode.toString().substring(0, 3));
        return new Result<>(httpCode, errorCode, message, null);
    }

     public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
}