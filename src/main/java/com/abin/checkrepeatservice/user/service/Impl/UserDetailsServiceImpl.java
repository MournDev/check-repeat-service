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

        // 3. 构建权限列表（Spring Security要求权限以"ROLE_"开头，或直接用角色编码）
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role.getRoleCode()));

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