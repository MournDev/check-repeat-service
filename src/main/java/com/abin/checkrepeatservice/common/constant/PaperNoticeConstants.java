package com.abin.checkrepeatservice.common.constant;

/**
 * 论文通知类型常量
 */
public class PaperNoticeConstants {
    // 论文提交相关
    public static final String NOTICE_TYPE_PAPER_SUBMIT_SUCCESS = "paper_submit_success";
    public static final String NOTICE_TYPE_PAPER_SUBMIT_FAILED = "paper_submit_failed";

    // 查重相关
    public static final String NOTICE_TYPE_CHECK_SUCCESS = "check_success";
    public static final String NOTICE_TYPE_CHECK_FAILED = "check_failed";

    // 审核相关
    public static final String NOTICE_TYPE_AUDIT_SUCCESS = "audit_success";
    public static final String NOTICE_TYPE_AUDIT_FAILED = "audit_failed";

    //指导老师相关
    public static final String NOTICE_TYPE_ADVISOR_ASSIGN_SUCCESS = "advisor_assign_success";
    public static final String NOTICE_TYPE_ADVISOR_ASSIGN_FAILED = "advisor_assign_failed";
    
    // 论文撤回相关
    public static final String NOTICE_TYPE_PAPER_WITHDRAW_SUCCESS = "paper_withdraw_success";
    public static final String NOTICE_TYPE_PAPER_WITHDRAW_FAILED = "paper_withdraw_failed";
    
    // 论文修改申请相关
    public static final String NOTICE_TYPE_PAPER_MODIFY_REQUEST = "paper_modify_request";
    public static final String NOTICE_TYPE_PAPER_MODIFY_APPROVED = "paper_modify_approved";
    public static final String NOTICE_TYPE_PAPER_MODIFY_REJECTED = "paper_modify_rejected";
}
