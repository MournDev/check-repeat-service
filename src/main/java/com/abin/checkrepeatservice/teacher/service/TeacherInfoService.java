package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.UpdateTeacherInfoReq;
import jakarta.validation.Valid;

public interface TeacherInfoService{
    /**
     * 更新教师信息
     * @param updateReq 教师信息更新请求
     */
    Result<String> updateInfo(@Valid UpdateTeacherInfoReq updateReq);

    /**
     * 获取教师信息
     * @param userId 教师ID
     * @return 教师信息
     */
    Result<UpdateTeacherInfoReq> getInfoByUserId(Long userId);

    /**
     * 修改密码
     * @param userId 教师ID
     * @return 修改密码结果
     */
    Result<String> changePasswordByUserId(Long userId, String newPassword);
}
