package com.abin.checkrepeatservice.common.datascope;

/**
 * 角色数据范围取值（sys_role.data_scope）。
 *
 * <p>统一在这里定义字面量，禁止各 Service 自行拼字符串，避免范围值扩散后
 * 出现大小写/拼写不一致导致裁决失效。</p>
 */
public final class DataScope {

    /** 全部数据（超管与存量管理员角色默认值，保持改造前行为） */
    public static final String ALL = "ALL";

    /** 本学院数据 */
    public static final String COLLEGE = "COLLEGE";

    /** 本专业数据 */
    public static final String MAJOR = "MAJOR";

    /** 本人指导的论文/学生 */
    public static final String TEACHER = "TEACHER";

    /** 本人数据 */
    public static final String SELF = "SELF";

    private DataScope() {
    }

    /**
     * 是否为合法取值
     */
    public static boolean isValid(String scope) {
        return ALL.equals(scope) || COLLEGE.equals(scope) || MAJOR.equals(scope)
                || TEACHER.equals(scope) || SELF.equals(scope);
    }

    /**
     * 空值/非法值一律回落 ALL（存量角色无配置时保持改造前可见范围）
     */
    public static String normalize(String scope) {
        if (scope == null) {
            return ALL;
        }
        String trimmed = scope.trim().toUpperCase();
        return isValid(trimmed) ? trimmed : ALL;
    }
}
