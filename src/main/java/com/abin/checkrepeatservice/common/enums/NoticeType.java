package com.abin.checkrepeatservice.common.enums;

// 通知类型枚举
public enum NoticeType {
    PAPER_SUBMITTED("论文提交完成", "您的论文已成功提交，系统正在处理中"),
    PAPER_CHECK_COMPLETED("论文查重完成", "您的论文查重已完成，请查看结果"),
    PAPER_NEEDS_REVISION("论文需修改", "您的论文需要修改，请查看审核意见"),
    PAPER_APPROVED("论文审核通过", "恭喜！您的论文已通过审核"),
    ADVISOR_ASSIGNED("指导老师分配", "您的论文已分配指导老师"),
    GUIDANCE_CREATED("指导记录待确认", "您的指导教师新增了一条指导记录，请及时查看并确认"),
    GUIDANCE_CONFIRMED("指导记录已确认", "学生已确认您的指导记录，可查看学生反馈"),
    REVIEW_ASSIGN("评阅任务通知", "您有新的论文评阅任务，请及时登录系统处理"),
    DEFENSE_RESULT("答辩结论通知", "您的答辩结论已出，请登录系统查看详情"),
    // P5：导师签核 qualified=0（查重报告不合格）时发给学生的退回通知，
    // 提醒学生按导师意见修改后重新提交再检。EmailService 未为其定制模板，
    // 走 buildTaskSubject/buildTaskContent 的 default 分支（取 title + buildDefaultContent）
    CHECK_SIGNOFF_REJECTED("查重报告签核退回", "您的论文查重报告未通过导师签核，请按导师意见修改后重新提交"),
    // P5：终期答辩通过且总评成绩加权合成成功回写 final_score 后发给学生的通知
    SCORE_SYNTHESIZED("总评成绩已合成", "您的论文总评成绩已合成完成，请登录系统查看详情"),
    // P6a：终期答辩 pass 后发给学生的导师评价邀请（软提醒，不卡定稿/归档门槛）。
    // EmailService 未为其定制模板，走 buildTaskSubject/buildTaskContent 的 default 分支
    ADVISOR_EVAL_INVITE("导师评价邀请", "您已通过终期答辩，欢迎对指导教师的指导工作作出评价"),
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
