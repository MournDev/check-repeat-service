package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.UserInfoDTO;
import com.abin.checkrepeatservice.admin.service.AdminUserService;
import com.abin.checkrepeatservice.admin.vo.UserCreateReq;
import com.abin.checkrepeatservice.admin.vo.UserUpdateReq;
import com.abin.checkrepeatservice.admin.vo.BatchDeleteReq;
import com.abin.checkrepeatservice.admin.vo.ResetPasswordReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.user.mapper.SysLoginLogMapper;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员用户管理服务实现类
 */
@Slf4j
@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Resource
    private SysUserService sysUserService;
    
    @Resource
    private SysLoginLogMapper sysLoginLogMapper;
    
    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private SysRoleMapper sysRoleMapper;

    // 安全修复（S7）：管理员重置密码后吊销目标用户全部旧令牌
    @Resource
    private com.abin.checkrepeatservice.common.jwt.TokenRevocationService tokenRevocationService;

    @Override
    public Result<Page<UserInfoDTO>> getUserList(Integer page, Integer size, String userType, 
                                               Integer status, String keyword) {
        try {
            // 修复：如果size为null或小于等于0，则查询所有用户
            if (size == null || size <= 0) {
                // 获取总用户数
                LambdaQueryWrapper<SysUser> countWrapper = buildUserQueryWrapper(userType, status, keyword);
                long totalUsers = sysUserService.count(countWrapper);
                
                // 设置一个较大的size值来获取所有用户
                size = (int) totalUsers;
                if (size == 0) {
                    size = 10; // 防止除零错误
                }
            }
            
            Page<SysUser> userPage = new Page<>(page, size);
            LambdaQueryWrapper<SysUser> wrapper = buildUserQueryWrapper(userType, status, keyword);
            
            Page<SysUser> resultPage = sysUserService.page(userPage, wrapper);
            
            // 转换为DTO
            Page<UserInfoDTO> dtoPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
            Map<Long, String> roleNames = loadRoleNames();
            List<UserInfoDTO> dtoList = resultPage.getRecords().stream()
                    .map(user -> convertToDTO(user, roleNames))
                    .collect(Collectors.toList());
            dtoPage.setRecords(dtoList);
            
            log.info("获取用户列表成功: page={}, size={}, total={}", page, size, resultPage.getTotal());
            return Result.success("用户列表获取成功", dtoPage);
        } catch (Exception e) {
            log.error("获取用户列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"获取用户列表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> createUser(UserCreateReq createReq) {
        try {
            // 参数校验
            if (createReq.getUsername() == null || createReq.getUsername().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR,"用户名不能为空");
            }
            if (createReq.getPassword() == null || createReq.getPassword().length() < 6) {
                return Result.error(ResultCode.PARAM_ERROR,"密码长度不能少于6位");
            }
            
            // 检查用户名是否已存在
            if (isUsernameExists(createReq.getUsername())) {
                return Result.error(ResultCode.SYSTEM_ERROR,"用户名已存在");
            }

            // 守护栏（P0）：角色指派边界 —— 超管账号只能由 SQL 引导产生（种子数据.sql 段 11），
            // 管理员账号只有超管能创建，防止 ADMIN 横向扩编管理面
            String createRoleCode = UserBusinessInfoUtils.getRoleCodeById(createReq.getRoleId());
            if (UserBusinessInfoUtils.ROLE_SUPER_ADMIN.equals(createRoleCode)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "超级管理员账号仅能通过数据库引导创建");
            }
            if (UserBusinessInfoUtils.ROLE_ADMIN.equals(createRoleCode)
                    && !UserBusinessInfoUtils.isSuperAdminOperator()) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "仅超级管理员可创建管理员账号");
            }

            // 创建新用户
            SysUser newUser = new SysUser();
            newUser.setUsername(createReq.getUsername().trim());
            newUser.setPassword(passwordEncoder.encode(createReq.getPassword()));
            newUser.setRealName(createReq.getRealName());
            newUser.setRoleId(createReq.getRoleId());
            newUser.setEmail(createReq.getEmail());
            newUser.setPhone(createReq.getPhone());
            newUser.setUserType(createReq.getUserType());
            newUser.setStatus(1); // 默认启用
            newUser.setIsDeleted(0);
            newUser.setCollegeName(createReq.getCollegeName());
            newUser.setMajor(createReq.getMajor());
            newUser.setGrade(createReq.getGrade());
            newUser.setClassName(createReq.getClassName());
            
            boolean saved = sysUserService.save(newUser);
            if (!saved) {
                return Result.error(ResultCode.SYSTEM_ERROR,"用户创建失败");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", newUser.getId());
            result.put("username", newUser.getUsername());
            result.put("message", "用户创建成功");
            
            log.info("管理员创建用户成功: username={}, userId={}", createReq.getUsername(), newUser.getId());
            return Result.success("用户创建成功", result);
        } catch (Exception e) {
            log.error("创建用户失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateUser(Long userId, UserUpdateReq updateReq) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.SYSTEM_ERROR,"用户不存在");
        }

        // 自助更新例外：以 JWT 解析出的当前登录人 ID 为准（不信请求体里的 userId/role），
        // 路径上的目标就是本人时，允许维护自己的姓名/邮箱/手机等资料。checkCanManageUser
        // 防的是"一个管理员横向拿捏其他管理员"，不能把本人改自己的资料也挡掉——否则
        // ADMIN（乃至 SUPER_ADMIN）的个人设置页会 40304 自我锁死。
        // 注意：删除/禁用/重置密码/角色分配等高危操作各有专用接口，守护栏在那些路径上
        // 照旧生效，本例外只覆盖本接口的资料字段（DTO 本身不含 roleId/username/password）。
        boolean isSelfUpdate = userId.equals(UserBusinessInfoUtils.getCurrentUserId());
        if (!isSelfUpdate) {
            // 守护栏（P0）：不可动超管；管理员账号只有超管能改
            String guardMsg = UserBusinessInfoUtils.checkCanManageUser(user);
            if (guardMsg != null) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, guardMsg);
            }
            // 本方法会顺带更新 status（updateReq.getStatus()），禁用动作同样受"最后一个管理员"保护
            if (updateReq.getStatus() != null && updateReq.getStatus() == 0
                    && UserBusinessInfoUtils.disablesLastActiveAdmin(user)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "系统至少需保留一个启用的管理员");
            }
        }

        // 更新用户信息
        user.setRealName(updateReq.getRealName());
        user.setEmail(updateReq.getEmail());
        user.setPhone(updateReq.getPhone());
        user.setCollegeName(updateReq.getCollegeName());
        user.setMajor(updateReq.getMajor());
        user.setGrade(updateReq.getGrade());
        user.setClassName(updateReq.getClassName());
        // status 已改为可选（UserUpdateReq 不再 @NotNull）：null = 本次不修改状态，
        // 状态变更走 updateUserStatus 专用接口（其守护栏/最后管理员保护更完整）。
        // 直接 setStatus(null) 会把库里状态抹掉，必须跳过。
        // 本人自助更新时强制忽略 status：个人资料接口不得变相自我禁用/启用。
        if (!isSelfUpdate && updateReq.getStatus() != null) {
            user.setStatus(updateReq.getStatus());
        }

        // 个人设置页「通知设置 + 个人偏好」：全部可选，null = 本次不修改。
        // 用户管理页的编辑对话框不带这些字段（null），不会被顺带抹掉；
        // 个人设置页的 saveSettings 则会带上全部 6 个字段一次提交。
        if (updateReq.getNotifySystemError() != null) {
            user.setNotifySystemError(Boolean.TRUE.equals(updateReq.getNotifySystemError()) ? 1 : 0);
        }
        if (updateReq.getNotifySecurityWarning() != null) {
            user.setNotifySecurityWarning(Boolean.TRUE.equals(updateReq.getNotifySecurityWarning()) ? 1 : 0);
        }
        if (updateReq.getNotifySystemAnnouncement() != null) {
            user.setNotifySystemAnnouncement(Boolean.TRUE.equals(updateReq.getNotifySystemAnnouncement()) ? 1 : 0);
        }
        if (updateReq.getNotifyEmail() != null) {
            user.setNotifyEmail(Boolean.TRUE.equals(updateReq.getNotifyEmail()) ? 1 : 0);
        }
        if (updateReq.getLogRetentionDays() != null) {
            user.setLogRetentionDays(updateReq.getLogRetentionDays());
        }
        if (updateReq.getBackupFrequency() != null) {
            user.setBackupFrequency(updateReq.getBackupFrequency());
        }

        sysUserService.updateById(user);

        log.info("管理员更新用户信息成功: userId={}, selfUpdate={}", userId, isSelfUpdate);
        return Result.success("用户信息更新成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteUser(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.SYSTEM_ERROR,"用户不存在");
        }

        // 守护栏（P0）：不可动超管；管理员账号只有超管能删；且不可删掉最后一个启用的管理员
        String guardMsg = UserBusinessInfoUtils.checkCanManageUser(user);
        if (guardMsg != null) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, guardMsg);
        }
        if (UserBusinessInfoUtils.disablesLastActiveAdmin(user)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "系统至少需保留一个启用的管理员");
        }

        // 软删除
        user.setIsDeleted(1);
        sysUserService.updateById(user);

        log.info("管理员删除用户成功: userId={}", userId);
        return Result.success("用户删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> batchDeleteUsers(BatchDeleteReq batchReq) {
        List<Long> userIds = batchReq.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR,"请选择要删除的用户");
        }

        List<SysUser> users = sysUserService.listByIds(userIds);

        // 守护栏（P0）：先全量校验、整批通过才动手 —— 部分成功会让操作人误以为清完了
        for (SysUser user : users) {
            if (user == null || user.getIsDeleted() == 1) {
                continue;
            }
            String guardMsg = UserBusinessInfoUtils.checkCanManageUser(user);
            if (guardMsg != null) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, guardMsg);
            }
        }
        // 逐个校验会漏判"两个管理员一批删掉"：整批排除后再验一次剩余启用管理员
        if (UserBusinessInfoUtils.disablesAllActiveAdmins(users)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "批量删除将移除全部启用的管理员，系统至少需保留一个");
        }

        // 批量软删除
        users.forEach(user -> {
            if (user != null && user.getIsDeleted() == 0) {
                user.setIsDeleted(1);
            }
        });

        sysUserService.updateBatchById(users);

        log.info("管理员批量删除用户成功: count={}", userIds.size());
        return Result.success("批量删除成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateUserStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            return Result.error(ResultCode.PARAM_ERROR,"状态参数无效");
        }

        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.PARAM_ERROR,"用户不存在");
        }

        // 守护栏（P0）：不可动超管；管理员账号只有超管能禁用；且不可禁用最后一个启用的管理员
        String guardMsg = UserBusinessInfoUtils.checkCanManageUser(user);
        if (guardMsg != null) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, guardMsg);
        }
        if (status == 0 && UserBusinessInfoUtils.disablesLastActiveAdmin(user)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "系统至少需保留一个启用的管理员");
        }

        user.setStatus(status);
        sysUserService.updateById(user);

        String statusText = status == 1 ? "启用" : "禁用";
        log.info("管理员{}用户成功: userId={}", statusText, userId);
        return Result.success("用户" + statusText + "成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> resetPassword(Long userId, ResetPasswordReq resetReq) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.PARAM_ERROR,"用户不存在");
        }

        // 守护栏（P0）：不可动超管；管理员密码只有超管能重置（改密 = 接管账号）
        String guardMsg = UserBusinessInfoUtils.checkCanManageUser(user);
        if (guardMsg != null) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, guardMsg);
        }

        // 重置密码
        user.setPassword(passwordEncoder.encode(resetReq.getNewPassword()));
        sysUserService.updateById(user);

        // S7：目标用户的存量令牌立即失效，防止重置后旧会话仍可操作
        tokenRevocationService.revokeAllTokensForUser(userId);

        log.info("管理员重置用户密码成功: userId={}", userId);
        return Result.success("密码重置成功");
    }

    @Override
    public Result<UserInfoDTO> getUserDetail(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.SYSTEM_ERROR,"用户不存在");
        }
        
        UserInfoDTO userInfoDTO = convertToDTO(user, loadRoleNames());
        return Result.success("用户详情获取成功", userInfoDTO);
    }

    @Override
    public Result<Page<SysLoginLog>> getUserLoginHistory(Long userId, Integer page, Integer size) {
        Page<SysLoginLog> loginLogPage = new Page<>(page, size);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginLog::getUserId, userId)
               .orderByDesc(SysLoginLog::getLoginTime);
        
        Page<SysLoginLog> resultPage = sysLoginLogMapper.selectPage(loginLogPage, wrapper);
        return Result.success("登录历史获取成功", resultPage);
    }

    @Override
    public boolean isUsernameExists(String username) {
        SysUser existingUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getIsDeleted, 0));
        return existingUser != null;
    }

    @Override
    public Map<String, Object> getUserStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 总用户数
        Long totalUsers = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, 0));
        stats.put("totalUsers", totalUsers);
        
        // 各类型用户统计
        Long adminCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, 0)
                .eq(SysUser::getIsDeleted, 0));
        Long studentCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, 1)
                .eq(SysUser::getIsDeleted, 0));
        Long teacherCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, 2)
                .eq(SysUser::getIsDeleted, 0));
        
        stats.put("admins", adminCount);
        stats.put("students", studentCount);
        stats.put("teachers", teacherCount);
        
        // 启用/禁用用户统计
        Long enabledUsers = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .eq(SysUser::getIsDeleted, 0));
        Long disabledUsers = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 0)
                .eq(SysUser::getIsDeleted, 0));
        
        stats.put("enabledUsers", enabledUsers);
        stats.put("disabledUsers", disabledUsers);
        
        return stats;
    }

    /**
     * 构建用户查询条件
     */
    private LambdaQueryWrapper<SysUser> buildUserQueryWrapper(String userType, Integer status, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        
        // 过滤已删除用户
        wrapper.eq(SysUser::getIsDeleted, 0);
        
        // 角色筛选
        if (userType != null && !userType.isEmpty()) {
                wrapper.eq(SysUser::getUserType, userType);
        }
        
        // 状态筛选
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        
        // 关键字搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword));
        }
        
        // 按创建时间倒序
        wrapper.orderByDesc(SysUser::getCreateTime);
        
        return wrapper;
    }

    /**
     * 角色ID → 角色名。
     *
     * 角色表只有个位数行，整表捞一次比逐行查省事，也免掉 N+1。
     * 特意**不过滤 is_deleted**：角色被软删后历史用户仍指向它，此时更要显示出名字，
     * 而不是让这批用户集体变成「未知」。
     */
    private Map<Long, String> loadRoleNames() {
        return sysRoleMapper.selectList(null).stream()
                .filter(role -> role.getId() != null && role.getRoleName() != null)
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName, (a, b) -> a));
    }

    /**
     * sys_role 里查不到该角色时的兜底显示名。
     *
     * userType 为 null 必须容忍：这里以前是 `switch (user.getUserType())`，而 Java 的
     * switch 遇到 null 选择表达式会直接抛 NPE（不走 default），于是库里只要有**一行**
     * user_type 为 NULL，整个用户列表接口就 500。自注册那条写入路径从来没写过
     * user_type，而 DDL 是 DEFAULT NULL，这种行是存在过的。
     */
    private String userTypeFallbackName(String userType) {
        if (userType == null || userType.isBlank()) {
            return "未知";
        }
        return switch (userType) {
            case "ADMIN" -> "管理员";
            case "STUDENT" -> "学生";
            case "TEACHER" -> "教师";
            case "SUPER_ADMIN" -> "超级管理员";
            default -> "未知";
        };
    }

    /**
     * 将SysUser转换为UserInfoDTO
     *
     * @param roleNames 角色ID → 角色名，由 loadRoleNames() 批量取，勿在循环里单独查
     */
    private UserInfoDTO convertToDTO(SysUser user, Map<Long, String> roleNames) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setUserType(user.getUserType());
        dto.setStatus(user.getStatus());
        dto.setCollegeName(user.getCollegeName());
        dto.setMajor(user.getMajor());
        dto.setGrade(user.getGrade());
        dto.setClassName(user.getClassName());
        dto.setCreateTime(user.getCreateTime());
        dto.setLastLoginTime(user.getLastLoginTime());

        // 角色以 sys_role 为准。以前是从 user_type 反推的，而 user_type 只是 role_code
        // 的冗余副本：既会因 NULL 抛 NPE，也认不出 SUPER_ADMIN / EDU_ADMIN 这些后来加的
        // 角色（全被 default 打成「未知」）。改成查表后，新增角色不用再改这里的代码。
        dto.setRoleId(user.getRoleId());
        String roleName = roleNames.get(user.getRoleId());
        dto.setRoleName((roleName == null || roleName.isBlank())
                ? userTypeFallbackName(user.getUserType())
                : roleName);

        return dto;
    }
}