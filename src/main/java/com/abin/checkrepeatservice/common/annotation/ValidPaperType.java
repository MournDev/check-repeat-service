package com.abin.checkrepeatservice.common.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 论文类型取值域校验，取值域见 {@link com.abin.checkrepeatservice.common.enums.PaperTypeEnum}。
 *
 * 为什么用 Bean Validation，而不是在 service 里手工校验后抛 BusinessException：
 *   StudentPaperController.submitPaper 末尾有一句 catch (Exception) → Result.error(SYSTEM_ERROR)，
 *   PaperInfoServiceImpl.submitPaperByFileId 内部又先 catch (Exception) 包成了 RuntimeException。
 *   于是 service 里抛出的 BusinessException 根本走不到 GlobalExceptionHandler，
 *   客户端拿到的是 500「论文提交失败: ...」而不是 400 参数错误。
 *   约束在**参数绑定阶段**触发，早于这两处 catch，
 *   由 GlobalExceptionHandler.handleMethodArgumentNotValid 统一转成 400 + PARAM_ERROR，
 *   要改成 service 内校验，得同时给这两处加 BusinessException 直通的护栏 —— 漏一处就静默退回 500。
 *
 * 也不写成 @Pattern(regexp = "graduation|course|other")：那等于把值域复制到 DTO 上，
 * 而本次改造的全部意义就是让这个值域只剩 enum 一份。
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PaperTypeValidator.class)
public @interface ValidPaperType {

    String message() default "论文类型不合法";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
