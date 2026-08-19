package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生个人资料DTO
 */
@Data
public class StudentProfileDTO {
    
    /**
     * 学生ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    
    /**
     * 学号
     */
    private String studentNo;
    
    /**
     * 姓名
     */
    private String name;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 学院
     */
    private String college;
    
    /**
     * 专业
     */
    private String major;
    
    /**
     * 年级
     */
    private String grade;
    
    /**
     * 班级
     */
    private String className;
    
    /**
     * 头像URL
     */
    private String avatar;
    
    /**
     * 研究兴趣
     */
    private String researchInterest;
    
    /**
     * 导师ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long advisorId;
    
    /**
     * 学术统计
     */
    private AcademicStats academicStats;
    
    /**
     * 导师信息
     */
    private AdvisorInfo advisorInfo;
    
    /**
     * 学术统计内部类
     */
    @Data
    public static class AcademicStats {
        /**
         * 论文总数
         */
        private Integer totalPapers;
        
        /**
         * 已完成论文数
         */
        private Integer completedPapers;
        
        /**
         * 平均相似度
         */
        private BigDecimal avgSimilarity;
        
        /**
         * 最高成绩
         */
        private BigDecimal highestScore;
        
        /**
         * 累计字数
         */
        private Integer totalWords;
    }
    
    /**
     * 导师信息内部类
     */
    @Data
    public static class AdvisorInfo {
        /**
         * 导师ID
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        
        /**
         * 导师姓名
         */
        private String name;
        
        /**
         * 职称
         */
        private String title;
        
        /**
         * 手机号
         */
        private String phone;
        
        /**
         * 邮箱
         */
        private String email;
        
        /**
         * 办公室
         */
        private String office;
        
        /**
         * 头像
         */
        private String avatar;
        
        /**
         * 研究领域
         */
        private String researchField;
        
        /**
         * 专长领域列表
         */
        private List<String> expertise;
    }
}