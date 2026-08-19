package com.abin.checkrepeatservice.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 多轮指导历史 VO（前端展示专用）
 */
@Data
public class PaperAdvisorRoundVO {
    // 关联记录基础信息
    private Long relId; // 关联记录ID（paper_advisor_rel.id）
    private Integer advisorRound; // 指导轮次
    private String relStatusDesc; // 关联状态描述（如“有效”“无效”）
    private LocalDateTime createTime;// 分配时间（轮次开始时间）
    private Long studentMajorId;// 论文所属专业ID
    private String researchDirection;
    private String paperKeyword;

    // 论文关联详情（避免前端额外查询）
    private Long paperId;
    private String paperTitle; // 论文标题（关联 paper_submit.paper_title）
    private String paperType; // 论文类型（如“毕业论文”）

    // 指导老师关联详情（避免前端额外查询）
    private Long advisorId;
    private String realName; // 指导老师姓名（关联 sys_user.real_name）
    private String advisorMajor; // 指导老师所属专业（关联 major.major_name）
}
