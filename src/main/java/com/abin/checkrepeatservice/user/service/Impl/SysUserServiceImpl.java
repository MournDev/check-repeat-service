package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.enums.UserTypeEnum;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /**
     * 查询未删除的用户（避免查询已软删除的数据）
     */
    @Override
    public SysUser getByIdWithNotDeleted(Long userId) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userId)
                .eq("is_deleted", 0); // 过滤已删除用户
        return this.getOne(queryWrapper);
    }

    /**
     * 判断是否为管理员（user_type=0）
     */
    @Override
    public boolean isAdmin(Long userId) {
        SysUser user = this.getByIdWithNotDeleted(userId);
        if (user == null) {
            return false;
        }
        return UserTypeEnum.ADMIN.getCode().equals(user.getUserType());
    }

    /**
     * 判断是否为教师（user_type=2）
     */
    @Override
    public boolean isTeacher(Long userId) {
        SysUser user = this.getByIdWithNotDeleted(userId);
        if (user == null) {
            return false;
        }
        return UserTypeEnum.TEACHER.getCode().equals(user.getUserType());
    }

    /**
     * 判断是否为学生（user_type=1）
     */
    @Override
    public boolean isStudent(Long userId) {
        SysUser user = this.getByIdWithNotDeleted(userId);
        if (user == null) {
            return false;
        }
        return UserTypeEnum.STUDENT.getCode().equals(user.getUserType());
    }
}
