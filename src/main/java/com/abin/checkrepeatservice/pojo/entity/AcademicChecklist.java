package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学术诚信检查清单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("academic_checklist")
public class AcademicChecklist extends BaseEntity {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 学生ID
     */
    private Long studentId;
    
    /**
     * 检查项文本
     */
    private String text;
    
    /**
     * 是否已检查
     */
    private Boolean checked;
    
    /**
     * 排序序号
     */
    private Integer sort;
}