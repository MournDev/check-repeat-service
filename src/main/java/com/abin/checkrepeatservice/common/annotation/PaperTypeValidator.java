package com.abin.checkrepeatservice.common.annotation;

import com.abin.checkrepeatservice.common.enums.PaperTypeEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidPaperType} 的实现。
 *
 * 值域判断一律委托 {@link PaperTypeEnum#isValidCode}，本类不持有任何取值。
 */
public class PaperTypeValidator implements ConstraintValidator<ValidPaperType, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null / 空串 / 纯空白都放行：那是"没填"，由同一个字段上的 @NotBlank 报"不能为空"。
        // 这里也报错的话，前端会同时收到两条消息，而 GlobalExceptionHandler 只取第一条，
        // 结果取决于字段错误的顺序 —— 用户可能看到"论文类型不合法"而实际是没选。
        return value == null || value.isBlank() || PaperTypeEnum.isValidCode(value);
    }
}
