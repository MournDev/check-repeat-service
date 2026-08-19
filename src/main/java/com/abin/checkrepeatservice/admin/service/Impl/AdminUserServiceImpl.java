package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.UserInfoDTO;
import com.abin.checkrepeatservice.admin.service.AdminUserService;
import com.abin.checkrepeatservice.admin.vo.UserCreateReq;
import com.abin.checkrepeatservice.admin.vo.UserUpdateReq;
import com.abin.checkrepeatservice.admin.vo.BatchDeleteReq;
import com.abin.checkrepeatservice.admin.vo.ResetPasswordReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
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
            List<UserInfoDTO> dtoList = resultPage.getRecords().stream()
                    .map(this::convertToDTO)
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
        
        // 更新用户信息
        user.setRealName(updateReq.getRealName());
        user.setEmail(updateReq.getEmail());
        user.setPhone(updateReq.getPhone());
        user.setCollegeName(updateReq.getCollegeName());
        user.setMajor(updateReq.getMajor());
        user.setGrade(updateReq.getGrade());
        user.setClassName(updateReq.getClassName());
        user.setStatus(updateReq.getStatus());
        
        sysUserService.updateById(user);
        
        log.info("管理员更新用户信息成功: userId={}", userId);
        return Result.success("用户信息更新成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteUser(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.SYSTEM_ERROR,"用户不存在");
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
        
        // 批量软删除
        List<SysUser> users = sysUserService.listByIds(userIds);
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
        
        // 重置密码
        user.setPassword(passwordEncoder.encode(resetReq.getNewPassword()));
        sysUserService.updateById(user);
        
        log.info("管理员重置用户密码成功: userId={}", userId);
        return Result.success("密码重置成功");
    }

    @Override
    public Result<UserInfoDTO> getUserDetail(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.SYSTEM_ERROR,"用户不存在");
        }
        
        UserInfoDTO userInfoDTO = convertToDTO(user);
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
     * 将SysUser转换为UserInfoDTO
     */
    private UserInfoDTO convertToDTO(SysUser user) {
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
        
        // 设置角色名称
        String roleName = switch (user.getUserType()) {
            case "ADMIN" -> "管理员";
            case "STUDENT" -> "学生";
            case "TEACHER" -> "教师";
            default -> "未知";
        };
        dto.setRoleName(roleName);
        
        return dto;
    }
}