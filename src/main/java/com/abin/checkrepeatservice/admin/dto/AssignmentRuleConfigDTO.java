package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

/**
 * 分配规则配置DTO
 */
@Data
public class AssignmentRuleConfigDTO {
    
    /**
     * 每个教师最大指导学生数
     */
    private Integer maxLoadPerTeacher;
    
    /**
     * 平衡策略
     */
    private String balanceStrategy;
    
    /**
     * 是否自动匹配研究兴趣
     */
    private Boolean autoMatchInterests;
    
    /**
     * 是否优先同部门分配
     */
    private Boolean departmentPriority;
    
    /**
     * 是否启用智能推荐
     */
    private Boolean smartRecommendation;
    
    /**
     * 最小指导学生数
     */
    private Integer minLoadPerTeacher;
    
    /**
     * 跨专业分配限制
     */
    private Boolean crossMajorLimit;
}