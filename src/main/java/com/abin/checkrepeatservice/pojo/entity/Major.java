package com.abin.checkrepeatservice.pojo.entity;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("major")
public class Major extends BaseEntity {

    /**
     * 专业编码（唯一）
     */
    @TableField("major_code")
    private String majorCode;

    /**
     * 专业名称
     */
    @TableField("major_name")
    private String majorName;

    /**
     * 所属学院ID（关联college.id）
     */
    @TableField("college_id")
    private Long collegeId;

    /**
     * 专业描述（可选）
     */
    @TableField("major_desc")
    private String majorDesc;

}
