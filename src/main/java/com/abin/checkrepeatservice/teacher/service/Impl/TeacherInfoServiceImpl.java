package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.UpdateTeacherInfoReq;
import com.abin.checkrepeatservice.teacher.service.TeacherInfoService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class TeacherInfoServiceImpl implements TeacherInfoService{

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PasswordEncoder passwordEncoder;
    @Override
    public Result<String> updateInfo(UpdateTeacherInfoReq updateReq){
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null|| !authentication.isAuthenticated()){
                return Result.error(ResultCode.NOT_LOGIN, "用户未登录");
            }
            String currentUsername = authentication.getName();
            SysUser currentUser = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getUsername, currentUsername)
                            .eq(SysUser::getIsDeleted, 0)
            );

            if (currentUser == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "当前用户不存在");
            }
            currentUser.setRealName(updateReq.getRealName());
            currentUser.setProfessionalTitle(updateReq.getTitle());
            currentUser.setCollegeId(updateReq.getCollegeId());
            currentUser.setResearchDirection(String.join(",", updateReq.getResearchFields()));
            currentUser.setIntroduce(updateReq.getIntroduce());
            currentUser.setPhone(updateReq.getPhone());
            currentUser.setEmail(updateReq.getEmail());
            currentUser.setOffice(updateReq.getOffice());
            currentUser.setOfficeHours(updateReq.getOfficeHours());
            currentUser.setMaxReviewCount(updateReq.getMaxReviewCount());
            currentUser.setReviewDeadline(updateReq.getReviewDeadline());

            sysUserMapper.updateById(currentUser);

            log.info("用户信息更新成功：用户名={}", currentUsername);
            return Result.success("用户信息更新成功");
        } catch (Exception e) {
            log.error("用户信息更新失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "用户信息更新失败：" + e.getMessage());
        }
    }

    @Override
    public Result<UpdateTeacherInfoReq> getInfoByUserId(Long userId) {
        try {
            // 根据用户ID查询教师信息
            SysUser user = sysUserMapper.selectOne(
                    Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getId, userId)
                            .eq(SysUser::getIsDeleted, 0)
            );

            if (user == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
            }

            // 将SysUser实体转换为UpdateTeacherInfoReq DTO
            UpdateTeacherInfoReq info = new UpdateTeacherInfoReq();
            info.setRealName(user.getRealName());
            info.setUsername(user.getUsername());
            info.setTitle(user.getProfessionalTitle());
            info.setCollegeId(user.getCollegeId());

            // 将研究方向字符串转换为列表
            if (user.getResearchDirection() != null && !user.getResearchDirection().isEmpty()) {
                List<String> researchFields = Arrays.asList(user.getResearchDirection().split(","));
                info.setResearchFields(researchFields);
            } else {
                info.setResearchFields(new ArrayList<>());
            }

            info.setIntroduce(user.getIntroduce());
            info.setPhone(user.getPhone());
            info.setEmail(user.getEmail());
            info.setOffice(user.getOffice());
            info.setOfficeHours(user.getOfficeHours());
            info.setMaxReviewCount(user.getMaxReviewCount());
            info.setReviewDeadline(user.getReviewDeadline());

            log.info("获取用户信息成功：用户ID={}", userId);
            return Result.success("获取用户信息成功", info);
        } catch (Exception e) {
            log.error("获取用户信息失败：用户ID={}, 错误信息={}", userId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户信息失败：" + e.getMessage());
        }
    }

    @Override
    public Result<String> changePasswordByUserId(Long userId, String newPassword) {
        if (userId == null || newPassword == null){
            return Result.error(ResultCode.PARAM_ERROR, "用户ID或密码不能为空");
        }
        
        // 添加密码长度验证
        if (newPassword.length() < 6 || newPassword.length() > 50) {
            return Result.error(ResultCode.PARAM_ERROR, "密码长度必须在6-50位之间");
        }
        
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        String encodePassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodePassword);
        sysUserMapper.updateById(user);
        log.info("用户密码修改成功：用户ID={}", userId);
        return Result.success("密码修改成功");
    }

}
