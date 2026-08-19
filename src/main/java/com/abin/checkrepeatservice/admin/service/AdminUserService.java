package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.UserInfoDTO;
import com.abin.checkrepeatservice.admin.vo.UserCreateReq;
import com.abin.checkrepeatservice.admin.vo.UserUpdateReq;
import com.abin.checkrepeatservice.admin.vo.BatchDeleteReq;
import com.abin.checkrepeatservice.admin.vo.ResetPasswordReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 管理员用户管理服务接口
 */
public interface AdminUserService {
    
    /**
     * 获取用户列表（分页）
     */
    Result<Page<UserInfoDTO>> getUserList(Integer page, Integer size, String userType,
                                        Integer status, String keyword);
    
    /**
     * 创建新用户
     */
    Result<Map<String, Object>> createUser(UserCreateReq createReq);
    
    /**
     * 更新用户信息
     */
    Result<String> updateUser(Long userId, UserUpdateReq updateReq);
    
    /**
     * 删除用户
     */
    Result<String> deleteUser(Long userId);
    
    /**
     * 批量删除用户
     */
    Result<String> batchDeleteUsers(BatchDeleteReq batchReq);
    
    /**
     * 启用/禁用用户
     */
    Result<String> updateUserStatus(Long userId, Integer status);
    
    /**
     * 重置用户密码
     */
    Result<String> resetPassword(Long userId, ResetPasswordReq resetReq);
    
    /**
     * 获取用户详细信息
     */
    Result<UserInfoDTO> getUserDetail(Long userId);
    
    /**
     * 获取用户登录历史
     */
    Result<Page<SysLoginLog>> getUserLoginHistory(Long userId, Integer page, Integer size);
    
    /**
     * 用户名唯一性检查
     */
    boolean isUsernameExists(String username);
    
    /**
     * 获取用户统计信息
     */
    Map<String, Object> getUserStatistics();
}