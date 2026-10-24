package com.abin.checkrepeatservice.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码枚举（覆盖所有业务异常场景）
 * 规则：错误码=HTTP状态码*100 + 业务序号（如 40001=HTTP400+参数格式错）
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    // ====================== 1. 参数相关错误（HTTP 400） ======================
    PARAM_EMPTY(40001, HttpStatus.BAD_REQUEST, "参数不能为空"),
    PARAM_TYPE_ERROR(40002, HttpStatus.BAD_REQUEST, "参数类型错误"),
    PARAM_FORMAT_ERROR(40003, HttpStatus.BAD_REQUEST, "参数格式错误（如轮次需≥1）"),
    PARAM_VALUE_INVALID(40004, HttpStatus.BAD_REQUEST, "参数值无效（如负数、空字符串）"),
    PARAM_ERROR(40005, HttpStatus.BAD_REQUEST, "参数错误"),

    NOT_LOGIN(401, HttpStatus.UNAUTHORIZED, "未登录"),

    // ====================== 2. 资源相关错误（HTTP 404） ======================
    RESOURCE_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在"),
    RESOURCE_DELETED(40402, HttpStatus.NOT_FOUND, "资源已删除"),
    RESOURCE_NO_PERMISSION(40403, HttpStatus.NOT_FOUND, "无权限访问该资源"),

    // ====================== 3. 权限相关错误（HTTP 403） ======================
    PERMISSION_ADMIN_ONLY(40301, HttpStatus.FORBIDDEN, "仅管理员可执行此操作"),
    PERMISSION_STUDENT_OWNER(40302, HttpStatus.FORBIDDEN, "仅学生本人可操作自己的论文"),
    PERMISSION_TEACHER_OWNER(40303, HttpStatus.FORBIDDEN, "仅指导该任务的教师可操作"),
    PERMISSION_NO_ACCESS(40304, HttpStatus.FORBIDDEN, "无权限执行此操作"),
    PERMISSION_NOT_STATUS(40305, HttpStatus.FORBIDDEN, "该状态下不可操作"),


    // ====================== 4. 业务逻辑错误（HTTP 409） ======================
    BUSINESS_TASK_ASSIGNED(40901, HttpStatus.CONFLICT, "该状态下不允许分配老师"),
    BUSINESS_TASK_NOT_ASSIGNED(40902, HttpStatus.CONFLICT, "论文未分配指导老师，无法执行此操作"),
    BUSINESS_ROUND_INVALID(40903, HttpStatus.CONFLICT, "指导轮次无效（如小于已存在轮次）"),
    BUSINESS_NO_ADVISOR(40904, HttpStatus.CONFLICT, "暂无可用指导老师"),
    BUSINESS_NOT_ROUNDS(40905, HttpStatus.CONFLICT, "无有效指导轮次"),
    BUSINESS_NO_TASK(40906, HttpStatus.CONFLICT, "无有效任务"),
    BUSINESS_NO_SAFE(40907, HttpStatus.CONFLICT, "请慎用该操作"),
    BUSINESS_NO_COUNT(40908, HttpStatus.CONFLICT, "无可用次数"),
    BUSINESS_NO_REPEAT(40909, HttpStatus.CONFLICT, "无需重复操作"),
    BUSINESS_ILLEGAL(40910, HttpStatus.CONFLICT, "指导老师不合法"),
    BUSINESS_NO_TASK_MAX(40911, HttpStatus.CONFLICT, "当前老师可指导学生人数已达上限"),
    BUSINESS_CHECK_NOT_SIGNOFF(40912, HttpStatus.CONFLICT, "查重报告未经导师签核，不可进入评阅分配"),
    BUSINESS_CHECK_ALREADY_SIGNOFF(40913, HttpStatus.CONFLICT, "该报告已签核，请勿重复操作"),
    BUSINESS_SCORE_NO_GUIDANCE(40914, HttpStatus.CONFLICT, "指导评分缺失，无法合成总评成绩"),
    BUSINESS_WEIGHT_INVALID(40915, HttpStatus.CONFLICT, "成绩权重配置非法（三项权重合计必须等于100）"),
    BUSINESS_QUOTA_EXCEEDED(40916, HttpStatus.CONFLICT, "本学院当月查重额度不足，请联系管理员"),
    BUSINESS_EVAL_NOT_READY(40917, HttpStatus.CONFLICT, "论文尚未通过终期答辩，暂不可评价导师"),
    BUSINESS_EVAL_ALREADY_SUBMITTED(40918, HttpStatus.CONFLICT, "您已提交过导师评价，请勿重复提交"),
    BUSINESS_EVAL_WINDOW_CLOSED(40919, HttpStatus.CONFLICT, "导师评价窗口已关闭"),
    DATA_CONFLICT(40920, HttpStatus.CONFLICT, "数据已存在，请勿重复提交"),

    // ====================== 4.7 文件过大（HTTP 413） ======================
    FILE_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大"),

    // ====================== 4.6 限流（HTTP 429） ======================
    LOGIN_TOO_MANY_ATTEMPTS(42901, HttpStatus.TOO_MANY_REQUESTS, "登录尝试次数过多，请稍后再试"),

    // ====================== 5. 系统错误（HTTP 500） ======================
    SYSTEM_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试"),
    SYSTEM_TIMEOUT(50002, HttpStatus.INTERNAL_SERVER_ERROR, "系统请求超时"),
    SYSTEM_DB_ERROR(50003, HttpStatus.INTERNAL_SERVER_ERROR, "数据库操作异常");

    // 错误码（如 40001）
    private final Integer code;
    // 对应的HTTP状态码（如 400、404）
    private final HttpStatus httpStatus;
    // 默认错误信息（可根据业务场景覆盖）
    private final String defaultMsg;
}