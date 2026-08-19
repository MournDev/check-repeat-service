package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AdminPermissionService;
import com.abin.checkrepeatservice.admin.vo.RoleCreateReq;
import com.abin.checkrepeatservice.admin.vo.RoleUpdateReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * 管理员权限管理服务实现类
 */
@Slf4j
@Service
public class AdminPermissionServiceImpl implements AdminPermissionService {

    @Resource
    private SysRoleMapper sysRoleMapper;
    
    @Resource
    private SysUserService sysUserService;

    @Override
    public Result<List<SysRole>> getRoleList() {
        try {
            LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysRole::getIsDeleted, 0)
                   .orderByAsc(SysRole::getRoleName); // 改为按角色名称排序
            
            List<SysRole> roles = sysRoleMapper.selectList(wrapper);
            
            log.info("获取角色列表成功: count={}", roles.size());
            return Result.success("角色列表获取成功", roles);
        } catch (Exception e) {
            log.error("获取角色列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"获取角色列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> createRole(RoleCreateReq createReq) {
        try {
            // 参数校验
            if (createReq.getRoleName() == null || createReq.getRoleName().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR,"角色名称不能为空");
            }
            if (createReq.getRoleCode() == null || createReq.getRoleCode().trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR,"角色编码不能为空");
            }
            
            // 检查角色编码是否已存在
            if (isRoleCodeExists(createReq.getRoleCode(), null)) {
                return Result.error(ResultCode.SYSTEM_ERROR,"角色编码已存在");
            }
            
            // 创建新角色
            SysRole newRole = new SysRole();
            newRole.setRoleName(createReq.getRoleName().trim());
            newRole.setRoleCode(createReq.getRoleCode().trim());
            newRole.setDescription(createReq.getDescription());
            newRole.setIsDeleted(0);
            
            int result = sysRoleMapper.insert(newRole);
            if (result <= 0) {
                return Result.error(ResultCode.SYSTEM_ERROR,"角色创建失败");
            }
            
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("roleId", newRole.getId());
            resultMap.put("roleName", newRole.getRoleName());
            resultMap.put("message", "角色创建成功");
            
            log.info("管理员创建角色成功: roleName={}, roleId={}", createReq.getRoleName(), newRole.getId());
            return Result.success("角色创建成功", resultMap);
        } catch (Exception e) {
            log.error("创建角色失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建角色失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<String> updateRole(Long roleId, RoleUpdateReq updateReq) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getIsDeleted() == 1) {
            return Result.error(ResultCode.SYSTEM_ERROR,"角色不存在");
        }
        
        // 检查角色编码是否被其他角色使用
        if (!role.getRoleCode().equals(updateReq.getRoleCode())) {
            if (isRoleCodeExists(updateReq.getRoleCode(), roleId)) {
                return Result.error(ResultCode.SYSTEM_ERROR,"角色编码已被其他角色使用");
            }
        }
        
        // 更新角色信息
        role.setRoleName(updateReq.getRoleName());
        role.setRoleCode(updateReq.getRoleCode());
        role.setDescription(updateReq.getDescription());
        // 移除status更新，角色的状态通过isDeleted字段控制
        
        sysRoleMapper.updateById(role);
        
        log.info("管理员更新角色成功: roleId={}", roleId);
        return Result.success("角色更新成功");
    }

    @Override
    public Result<String> deleteRole(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getIsDeleted() == 1) {
            return Result.error(ResultCode.PARAM_ERROR,"角色不存在");
        }
        
        // 检查是否有用户使用该角色
        if (isRoleInUse(roleId)) {
            return Result.error(ResultCode.PARAM_ERROR,"该角色已被用户使用，无法删除");
        }
        
        // 软删除角色
        role.setIsDeleted(1);
        sysRoleMapper.updateById(role);
        
        log.info("管理员删除角色成功: roleId={}", roleId);
        return Result.success("角色删除成功");
    }

    @Override
    public Result<List<Map<String, Object>>> getPermissionTree() {
        // 构建权限树结构
        List<Map<String, Object>> permissionTree = new ArrayList<>();
        
        // 系统管理
        Map<String, Object> systemManage = new HashMap<>();
        systemManage.put("id", "system");
        systemManage.put("label", "系统管理");
        systemManage.put("children", Arrays.asList(
            createPermissionNode("user_manage", "用户管理", Arrays.asList("view", "create", "edit", "delete")),
            createPermissionNode("role_manage", "角色管理", Arrays.asList("view", "create", "edit", "delete")),
            createPermissionNode("permission_manage", "权限管理", Arrays.asList("view", "assign")),
            createPermissionNode("config_manage", "系统配置", Arrays.asList("view", "edit"))
        ));
        permissionTree.add(systemManage);
        
        // 论文管理
        Map<String, Object> paperManage = new HashMap<>();
        paperManage.put("id", "paper");
        paperManage.put("label", "论文管理");
        paperManage.put("children", Arrays.asList(
            createPermissionNode("paper_list", "论文列表", Arrays.asList("view", "edit", "delete")),
            createPermissionNode("paper_check", "论文查重", Arrays.asList("check", "view_result")),
            createPermissionNode("paper_review", "论文审核", Arrays.asList("review", "approve", "reject")),
            createPermissionNode("paper_statistics", "统计报表", Arrays.asList("view"))
        ));
        permissionTree.add(paperManage);
        
        // 数据统计
        Map<String, Object> dataStatistics = new HashMap<>();
        dataStatistics.put("id", "statistics");
        dataStatistics.put("label", "数据统计");
        dataStatistics.put("children", Arrays.asList(
            createPermissionNode("school_overview", "学校概览", Arrays.asList("view")),
            createPermissionNode("user_statistics", "用户统计", Arrays.asList("view")),
            createPermissionNode("paper_statistics", "论文统计", Arrays.asList("view")),
            createPermissionNode("export_report", "导出报表", Arrays.asList("export"))
        ));
        permissionTree.add(dataStatistics);
        
        return Result.success("权限树获取成功", permissionTree);
    }

    @Override
    public Result<String> assignUserRole(Long userId, Long roleId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getIsDeleted() == 1) {
            return Result.error(ResultCode.PARAM_ERROR,"用户不存在");
        }
        
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null || role.getIsDeleted() == 1) {
            return Result.error(ResultCode.PARAM_ERROR,"角色不存在");
        }
        
        // 分配角色
        user.setRoleId(roleId);
        sysUserService.updateById(user);
        
        log.info("管理员分配用户角色成功: userId={}, roleId={}", userId, roleId);
        return Result.success("角色分配成功");
    }

    @Override
    public boolean isRoleCodeExists(String roleCode, Long excludeRoleId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .eq(SysRole::getIsDeleted, 0);
        
        if (excludeRoleId != null) {
            wrapper.ne(SysRole::getId, excludeRoleId);
        }
        
        return sysRoleMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean isRoleInUse(Long roleId) {
        return sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleId, roleId)
                .eq(SysUser::getIsDeleted, 0)) > 0;
    }

    /**
     * 创建权限节点
     */
    private Map<String, Object> createPermissionNode(String id, String label, List<String> actions) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("actions", actions);
        return node;
    }
}