package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.user.service.Impl.UserDetailsServiceImpl;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务信息工具类：用于获取用户业务相关信息
 */
@Slf4j
@Service
public class UserBusinessInfoUtils {
    // 角色编码缓存（角色ID -> 角色编码）
    private static final Map<Long, String> roleCodeCache = new ConcurrentHashMap<>();

    /**
     * 获取当前登录用户完整信息（SysUser实体）
     * @return 登录用户实体
     */
    public static SysUser getCurrentSysUser() {
        String username = AuthInfoUtils.getCurrentUsername();
        UserDetailsServiceImpl userDetailsService = SpringContextUtil.getBean(UserDetailsServiceImpl.class);
        return userDetailsService.findSysUserByUsername(username);
    }

    /**
     * 获取当前登录用户ID
     * @return 用户ID
     */
    public static Long getCurrentUserId() {
        SysUser currentUser = getCurrentSysUser();
        Assert.notNull(currentUser.getId(), "用户ID不能为空");
        return currentUser.getId();
    }

    /**
     * 获取当前登录用户真实姓名
     * @return 真实姓名
     */
    public static String getCurrentRealName() {
        SysUser currentUser = getCurrentSysUser();
        Assert.hasText(currentUser.getRealName(), "用户真实姓名不能为空");
        return currentUser.getRealName();
    }

    /**
     * 获取当前登录用户角色ID
     * @return 角色ID
     */
    public static Long getCurrentUserRoleId() {
        SysUser currentUser = getCurrentSysUser();
        Assert.notNull(currentUser.getRoleId(), "用户角色ID不能为空");
        return currentUser.getRoleId();
    }

    /**
     * 获取当前登录用户角色编码
     * @return 角色编码
     */
    public static String getCurrentUserRoleCode() {
        SysUser currentUser = getCurrentSysUser();
        Long roleId = currentUser.getRoleId();
        if (roleId != null) {
            // 先从缓存获取
            String cachedRoleCode = roleCodeCache.get(roleId);
            if (StringUtils.hasText(cachedRoleCode)) {
                return cachedRoleCode;
            }

            // 缓存未命中，查询数据库
            SysRoleMapper roleMapper = SpringContextUtil.getBean(SysRoleMapper.class);
            SysRole role = roleMapper.selectById(roleId);
            if (role != null && StringUtils.hasText(role.getRoleCode())) {
                String roleCode = role.getRoleCode();
                roleCodeCache.put(roleId, roleCode);
                return roleCode;
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户所属专业
     * @return 专业名称
     */
    public static String getCurrentUserMajor() {
        SysUser currentUser = getCurrentSysUser();
        return currentUser.getMajor();
    }

    /**
     * 获取当前登录用户所属班级
     * @return 班级名称
     */
    public static String getCurrentUserClassName() {
        SysUser currentUser = getCurrentSysUser();
        return currentUser.getClassName();
    }

    /**
     * 校验当前用户是否为管理员
     * @return true=管理员，false=非管理员
     */
    public static boolean isAdmin() {
        String roleCode = getCurrentUserRoleCode();
        // 超级管理员同样是管理员：它在 UserDetailsServiceImpl 里也持有 ADMIN 权限。
        // 若这里只认 ADMIN，超管会出现"接口调得通、业务校验却说无权"的半通状态
        // （本方法被查重报告/查重任务的访问控制使用，超管查看他人报告会被拒）。
        return "ADMIN".equals(roleCode) || "SUPER_ADMIN".equals(roleCode);
    }

    /**
     * 校验当前用户是否为学生
     * @return true=学生，false=非学生
     */
    public static boolean isStudent() {
        String roleCode = getCurrentUserRoleCode();
        return "STUDENT".equals(roleCode);
    }

    /**
     * 校验当前用户是否为教师
     * @return true=教师，false=非教师
     */
    public static boolean isTeacher() {
        String roleCode = getCurrentUserRoleCode();
        return "TEACHER".equals(roleCode);
    }
    // 在 UserBusinessInfoUtils.java 中新增如下方法：

    public static void setAuditField(Object entity, boolean isNew) {
        if (entity == null) return;

        SysUser currentUser = getCurrentSysUser();
        Long userId = (currentUser != null) ? currentUser.getId() : null;
        LocalDateTime now = LocalDateTime.now();

        try {
            Class<?> clazz = entity.getClass();
            if (isNew) {
                // 设置创建人和创建时间
                Field createdByField = getField(clazz, "createdBy");
                if (createdByField != null) {
                    createdByField.setAccessible(true);
                    createdByField.set(entity, userId);
                }

                Field createdTimeField = getField(clazz, "createdTime");
                if (createdTimeField != null) {
                    createdTimeField.setAccessible(true);
                    createdTimeField.set(entity, now);
                }
            }

            // 设置更新人和更新时间
            Field updatedByField = getField(clazz, "updatedBy");
            if (updatedByField != null) {
                updatedByField.setAccessible(true);
                updatedByField.set(entity, userId);
            }

            Field updatedTimeField = getField(clazz, "updatedTime");
            if (updatedTimeField != null) {
                updatedTimeField.setAccessible(true);
                updatedTimeField.set(entity, now);
            }
        } catch (Exception e) {
            log.warn("填充审计字段失败，忽略处理。实体类：{}", entity.getClass().getSimpleName(), e);
        }
    }

    // 辅助方法：递归查找字段（包括父类）
    private static Field getField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

}
