package com.abin.checkrepeatservice.common.enums;

import lombok.Getter;

/**
 * 用户角色枚举（统一管理角色，避免硬编码）
 */
@Getter
public enum UserTypeEnum {
    ADMIN(0, "管理员"),    // 角色：0=管理员
    STUDENT(1, "学生"),    // 角色：1=学生
    TEACHER(2, "教师"),    //角色：2=教师
    TEACHING_SECRETARY(3, "教学秘书"); // 新增角色：3=教学秘书

    private final Integer code; // 对应 user_type 字段值
    private final String desc;  // 角色描述

    UserTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // 根据 code 获取枚举（方便从 getUserType() 结果转换为枚举）
    public static UserTypeEnum getByCode(Integer code) {
        for (UserTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的用户角色码：" + code);
    }
}
