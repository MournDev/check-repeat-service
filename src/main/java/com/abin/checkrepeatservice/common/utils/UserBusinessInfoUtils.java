package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.user.service.Impl.UserDetailsServiceImpl;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // ==================== 内置角色与用户操作守护栏（P0） ====================

    /** 内置角色编码：由 补初始账号.sql 播种，是整条鉴权链的锚点，不允许通过接口增删改。 */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";

    private static final Set<String> BUILT_IN_ROLE_CODES =
            Set.of("STUDENT", "TEACHER", "ADMIN", "SUPER_ADMIN");

    /**
     * 是否系统内置角色编码（忽略大小写）。
     *
     * 内置角色是鉴权链的锚：UserDetailsServiceImpl 的超管 ADMIN 注入、
     * SecurityConfig 的 hasAuthority、本类的 isAdmin()/isStudent()/isTeacher()
     * 全部按这些编码字面量匹配 —— 改码或删除等于静默改权限且不留审计痕迹。
     */
    public static boolean isBuiltInRoleCode(String roleCode) {
        return roleCode != null && BUILT_IN_ROLE_CODES.contains(roleCode.toUpperCase());
    }

    /**
     * 按角色ID查角色编码（角色不存在或已软删返回 null）。
     *
     * 守护栏专用：**直查数据库、不走 roleCodeCache** —— 那个缓存没有失效机制，
     * 而这里是"能不能动这个账号"的安全决策，宁可多一次查库，不能吃到改码前的旧值。
     */
    public static String getRoleCodeById(Long roleId) {
        if (roleId == null) {
            return null;
        }
        SysRole role = SpringContextUtil.getBean(SysRoleMapper.class).selectById(roleId);
        return (role == null || role.getIsDeleted() == 1) ? null : role.getRoleCode();
    }

    /**
     * 当前操作人是否超级管理员。角色取不到时按非超管处理（保守放行方向：拒绝）。
     */
    public static boolean isSuperAdminOperator() {
        return ROLE_SUPER_ADMIN.equals(getCurrentUserRoleCode());
    }

    /**
     * 用户操作守护栏 · 规则 1+2（增删改/改密/改状态/改角色前统一调用）：
     *   规则 1 —— 任何人都不可操作超级管理员账号。超管是应急通道，只能由 SQL
     *     引导产生（补初始账号.sql），接口层面碰都不要碰，否则改码/删号即断通道；
     *   规则 2 —— 只有超级管理员可以管理管理员账号。否则任意 ADMIN 都能删同僚、
     *     改同僚密码/角色，管理面被单个管理员横向拿捏（横向提权）。
     *
     * @return null=放行；非 null=拒绝原因（调用方转为 403 返回）
     */
    public static String checkCanManageUser(SysUser target) {
        String targetRoleCode = getRoleCodeById(target.getRoleId());
        if (ROLE_SUPER_ADMIN.equals(targetRoleCode)) {
            return "不允许操作超级管理员账号";
        }
        if (ROLE_ADMIN.equals(targetRoleCode) && !isSuperAdminOperator()) {
            return "仅超级管理员可管理管理员账号";
        }
        return null;
    }

    /**
     * 用户操作守护栏 · 规则 3：把 target 禁用/删除/降级后，系统里是否再无
     * 「启用的管理员」。目标不是 ADMIN、或本来就未启用时恒返回 false（放行）。
     *
     * 没有这条规则，最后一个管理员可以被一次性操作清空，后台整体自我锁死，
     * 只能进库救。超管如确需下线最后一个管理员，先建一个新的管理员即可通过。
     */
    public static boolean disablesLastActiveAdmin(SysUser target) {
        return disablesAllActiveAdmins(List.of(target));
    }

    /**
     * 规则 3 的批量形态：把这批用户同时禁用/删除/降级后，系统里是否再无
     * 「启用的管理员」。逐个单独校验会漏判"两个管理员一批删掉"的场景，
     * 批量操作必须用本方法整批校验。
     */
    public static boolean disablesAllActiveAdmins(Collection<SysUser> targets) {
        if (targets == null || targets.isEmpty()) {
            return false;
        }
        // 只关心"启用中的 ADMIN 目标"：已禁用/已删除的目标不改变启用管理员数
        List<Long> affectedIds = new ArrayList<>();
        for (SysUser target : targets) {
            if (target == null || target.getId() == null) {
                continue;
            }
            if (target.getIsDeleted() != null && target.getIsDeleted() == 1) {
                continue;
            }
            if (target.getStatus() == null || target.getStatus() != 1) {
                continue;
            }
            if (!ROLE_ADMIN.equals(getRoleCodeById(target.getRoleId()))) {
                continue;
            }
            affectedIds.add(target.getId());
        }
        if (affectedIds.isEmpty()) {
            return false;
        }
        SysRole adminRole = SpringContextUtil.getBean(SysRoleMapper.class).selectOne(
                Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getRoleCode, ROLE_ADMIN)
                        .eq(SysRole::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (adminRole == null) {
            return false;
        }
        Long remaining = SpringContextUtil.getBean(SysUserMapper.class).selectCount(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getRoleId, adminRole.getId())
                        .eq(SysUser::getStatus, 1)
                        .eq(SysUser::getIsDeleted, 0)
                        .notIn(SysUser::getId, affectedIds));
        return remaining == null || remaining == 0;
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
