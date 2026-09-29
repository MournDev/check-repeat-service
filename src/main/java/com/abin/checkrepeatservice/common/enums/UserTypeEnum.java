package com.abin.checkrepeatservice.common.enums;

import lombok.Getter;

/**
 * 用户角色枚举（统一管理角色，避免硬编码）
 */
@Getter
public enum UserTypeEnum {
    ADMIN("ADMIN", "管理员"),
    STUDENT("STUDENT", "学生"),
    TEACHER("TEACHER", "教师"),
    TEACHING_SECRETARY("TEACHING_SECRETARY", "教学秘书");

    private final String code; // 对应 user_type 字段值（sys_user.user_type 是 varchar，存 'ADMIN'/'STUDENT'/'TEACHER'）
    private final String desc; // 角色描述

    UserTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 根据 code 获取枚举（方便从 getUserType() 结果转换为枚举）
    public static UserTypeEnum getByCode(String code) {
        for (UserTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的用户角色码：" + code);
    }
}
