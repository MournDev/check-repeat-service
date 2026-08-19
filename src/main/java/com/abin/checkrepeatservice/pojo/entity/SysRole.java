package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色权限实体：对应sys_role表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role") // 绑定数据库表名
public class SysRole extends BaseEntity {
    /**
     * 角色名称（如：学生、教师、教务管理员）
     */
    private String roleName;

    /**
     * 角色编码（唯一，如：STUDENT、TEACHER、ADMIN）
     */
    private String roleCode;

    /**
     * 权限列表（JSON格式字符串，如：["paper:upload","report:view"]）
     */
    private String permissions;

    /**
     * 角色描述
     */
    private String description;
}
