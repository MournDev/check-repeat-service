package com.abin.checkrepeatservice.common.enums;

// 通知类型枚举
public enum NoticeType {
    PAPER_SUBMITTED("论文提交完成", "您的论文已成功提交，系统正在处理中"),
    PAPER_CHECK_COMPLETED("论文查重完成", "您的论文查重已完成，请查看结果"),
    PAPER_NEEDS_REVISION("论文需修改", "您的论文需要修改，请查看审核意见"),
    PAPER_APPROVED("论文审核通过", "恭喜！您的论文已通过审核"),
    ADVISOR_ASSIGNED("指导老师分配", "您的论文已分配指导老师"),
    SYSTEM_ANNOUNCEMENT("系统公告", "系统发布重要公告"),
    ACCOUNT_ACTIVATION("账户激活", "请激活您的账户"),
    PASSWORD_RESET("密码重置", "请重置您的密码");


    private final String title;
    private final String defaultContent;

    NoticeType(String title, String defaultContent) {
        this.title = title;
        this.defaultContent = defaultContent;
    }

    public String getTitle() {
        return title;
    }

    public String getDefaultContent() {
        return defaultContent;

    }
}
