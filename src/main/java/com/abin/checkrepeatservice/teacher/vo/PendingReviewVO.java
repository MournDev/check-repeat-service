package com.abin.checkrepeatservice.teacher.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核论文列表VO
 */
@Data
public class PendingReviewVO {
    
    /**
     * 论文ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String paperId;
    
    /**
     * 论文标题
     */
    private String paperTitle;
    
    /**
     * 学生姓名
     */
    private String studentName;
    
    /**
     * 学生ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String studentId;
    
    /**
     * 学号
     */
    private String studentNo;
    
    /**
     * 学院
     */
    private String college;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 头像
     */
    private String avatar;
    
    /**
     * 提交时间
     */
    private String submitTime;
    
    /**
     * 截止时间
     */
    private String deadline;
    
    /**
     * 相似度
     */
    private Integer similarity;
    
    /**
     * 等待时间（天）
     */
    private Integer waitingTime;
    
    /**
     * 优先级
     */
    private String priority;
    
    /**
     * 版本
     */
    private String version;
    
    /**
     * 字数
     */
    private Integer wordCount;
    
    /**
     * 页数
     */
    private Integer pageCount;
    
    /**
     * 论文基础信息
     */
    private PaperBaseInfoVO paperBaseInfo;
    
    /**
     * 任务基础信息
     */
    private TaskBaseInfoVO taskBaseInfo;
    
    /**
     * 论文基础信息VO
     */
    @Data
    public static class PaperBaseInfoVO {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private String paperId;
        private String paperTitle;
        private String studentName;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private String studentId;
        private String createTime;
    }
    
    /**
     * 任务基础信息VO
     */
    @Data
    public static class TaskBaseInfoVO {
        private String deadline;
        private Integer reviewDays;
    }
}