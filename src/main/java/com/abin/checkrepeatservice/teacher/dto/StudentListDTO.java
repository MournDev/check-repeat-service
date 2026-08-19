package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生列表DTO,返回学生列表信息
 */
@Data
public class StudentListDTO {

    /**
     * 学生ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long studentId;

    /**
     * 学生学号
     */
    private String username;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 学生所在学院
     */
    private String collegeName;

    /**
     * 学生专业
     */
    private String major;

    /**
     * 学生年级
     */
    private String grade;

    /**
     * 学生论文状态
     */
    private String paperStatus;

    /**
     * 学生导师姓名
     */
    private String advisorName;

    /**
     * 学生论文提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 学生论文相似度
     */
    private BigDecimal similarity;
    
    /**
     * 学生班级
     */
    private String className;
    
    /**
     * 学生手机号
     */
    private String phone;
    
    /**
     * 学生邮箱
     */
    private String email;
    
    /**
     * 论文ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;
    
    /**
     * 论文标题
     */
    private String paperTitle;
    
    /**
     * 论文类型
     */
    private String paperType;
    
    /**
     * 论文字数
     */
    private Integer wordCount;
    
    /**
     * 论文文件 ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long fileId;


}
