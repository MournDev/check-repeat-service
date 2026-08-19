package com.abin.checkrepeatservice.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指导老师任务列表 VO（前端展示专用，隐藏敏感字段）
 */
@Data
public class PaperAdvisorTaskVO {
    // 论文基础信息
    private Long paperId; // 论文ID
    private String paperTitle; // 论文标题
    private String paperType; // 论文类型（如“毕业论文”“课程论文”）
    private Integer submitStatus; // 任务状态（1=待指导，2=指导中，3=指导完成）
    private String submitStatusDesc; // 状态描述（如“指导中”，供前端直接展示）
    private String paperKeyword;

    // 学生信息（关联 sys_user 表）
    private Long Id; // 学生ID
    private String userName; // 学生姓名
    private Long majorId;// 专业ID

    //老师信息（关联 sys_user 表）
    private Long advisorId; // 指导老师ID
    private String researchDirection;// 研究方向
    private Integer currentTaskCount;
    private String advisorName;

    // 时间信息
    private LocalDateTime submitTime; // 论文提交时间（原字段）
    private String submitTimeStr; // 格式化时间（如“2024-05-20 14:30:00”）
    private LocalDateTime advisorAssignTime; // 分配时间（原字段）
    private String advisorAssignTimeStr; // 格式化分配时间

    // 指导信息
    private String advisorSuggestion; // 指导意见（教师填写的内容）
    private Integer advisorScore; // 指导评分（可选）
}
