package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SysUserService extends IService<SysUser> {

    /**
     * 查询未删除的用户（避免查询已软删除的数据）
     */
    SysUser getByIdWithNotDeleted(Long userId);

    /**
     * 判断是否为管理员（user_type=0）
     */
    boolean isAdmin(Long userId);

    /**
     * 判断是否为教师（user_type=2）
     */
    boolean isTeacher(Long userId);

    /**
     * 判断是否为学生（user_type=1）
     */
    boolean isStudent(Long userId);
}
