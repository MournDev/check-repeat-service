package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security用户详情服务：加载用户信息（适配JWT认证）
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 根据用户名查询用户（过滤已删除用户）
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getIsDeleted, 0)
        );
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户名不存在或已被删除");
        }

        // 2. 根据用户角色ID查询角色编码（获取权限）
        SysRole role = sysRoleMapper.selectById(sysUser.getRoleId());
        if (role == null || role.getIsDeleted() == 1) {
            throw new UsernameNotFoundException("用户角色不存在或已被删除");
        }

        // 3. 构建权限列表（Spring Security要求权限以"ROLE_"开头，或直接用角色编码）。
        //
        //    「超级管理员 ⊇ 管理员」采取【双保险】，两处必须同时存在、不能只留其一：
        //      ① 这里发放时补授 ADMIN —— 发放侧兜底。P1 曾只依赖 SecurityConfig 的
        //         RoleHierarchy，结果线上超管全站 403（/api/admin/** 默认 JSON）：
        //         层级是否生效取决于「部署 jar 内 SecurityConfig 是否为最新构建」
        //         与「jar 内打包的 Spring Security ≥ 6.3」（6.3 起 hasAuthority 才接
        //         RoleHierarchy，见 AuthorizeHttpRequestsConfigurer#withRoleHierarchy）。
        //         任一条件不满足，超管的 authorities=[SUPER_ADMIN] 直接撞死在
        //         hasAuthority("ADMIN") 上 —— 这种「登录正常、全后台不可用」的
        //         事故代价远大于一处冗余发放。
        //      ② SecurityConfig.roleHierarchy() 声明式层级 —— 框架标准来源，供
        //        方法级 @PreAuthorize 与后续新增角色/层级演进使用。
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.getRoleCode()));
        if ("SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())) {
            authorities.add(new SimpleGrantedAuthority("ADMIN"));
        }

        // 4. 返回Spring Security的UserDetails对象（封装用户信息与权限）
        return new org.springframework.security.core.userdetails.User(
                sysUser.getUsername(),
                sysUser.getPassword(),
                sysUser.getStatus() == 1, // 账号是否启用（1-正常=启用）
                true, // 账号是否未过期
                true, // 凭证是否未过期
                true, // 账号是否未锁定
                authorities
        );
    }

    /**
     * 根据用户名查找完整的SysUser对象
     * @param username 用户名
     * @return SysUser对象
     * @throws UsernameNotFoundException 当用户不存在时抛出异常
     */
    public SysUser findSysUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 根据用户名查询用户（过滤已删除用户）
        SysUser sysUser = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getIsDeleted, 0)
        );

        if (sysUser == null) {
            throw new UsernameNotFoundException("用户名不存在或已被删除");
        }

        return sysUser;
    }
}