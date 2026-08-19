package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 学生列表请求DTO
 */
@Data
public class StudentListRequestDTO {
    /**
     * 教师ID
     */
    private Long teacherId;
    
    /**
     * 当前页码
     */
    private Integer page;
    
    /**
     * 每页条数
     */
    private Integer pageSize;
    
    /**
     * 搜索关键词
     */
    private String search;
    
    /**
     * 论文状态筛选
     */
    private String status;
    
    /**
     * 学院筛选
     */
    private String college;
}