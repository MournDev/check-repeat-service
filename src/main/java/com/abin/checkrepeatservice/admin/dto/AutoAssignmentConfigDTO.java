package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 自动分配算法配置DTO
 */
@Data
public class AutoAssignmentConfigDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 分配策略
     * comprehensive: 综合评估策略
     * load_balance: 负载均衡策略  
     * major_first: 专业优先策略
     * random: 随机分配策略
     */
    private String strategy = "comprehensive";
    
    /**
     * 教师最大指导学生数
     */
    private Integer maxLoad = 12;
    
    /**
     * 专业匹配权重 (0-100)
     */
    private Integer majorWeight = 40;
    
    /**
     * 研究兴趣匹配权重 (0-100)
     */
    private Integer interestWeight = 25;
    
    /**
     * 负载均衡权重 (0-100)
     */
    private Integer loadWeight = 20;
    
    /**
     * 经验权重 (0-100)
     */
    private Integer experienceWeight = 15;
    
    /**
     * 是否排除满负荷教师
     */
    private Boolean excludeFullTeachers = true;
    
    /**
     * 是否允许跨专业分配
     */
    private Boolean allowCrossMajor = false;
    
    /**
     * 最小指导学生数
     */
    private Integer minLoadPerTeacher = 3;
    
    /**
     * 跨专业分配限制
     */
    private Integer crossMajorLimit = 2;
}