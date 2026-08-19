package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.mapper.SysUserMajorMapper;
import com.abin.checkrepeatservice.user.service.SysUserMajorService;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SysUserMajorServiceImpl extends ServiceImpl<SysUserMajorMapper, Major> implements SysUserMajorService {

    @Resource
    private SysUserService sysUserService;

    /**
     * 完整权限校验逻辑：管理员可修改所有，教师仅能查看自己（不能修改）
     */
    @Override
    public boolean checkModifyMaxCountAuth(Long operatorId, Long targetUserId, Long majorId) {
        // 1. 校验操作人是否存在（排除已删除用户）
        SysUser operator = sysUserService.getById(operatorId);
        if (operator == null || operator.getIsDeleted() == 1) {
            throw new RuntimeException("操作人不存在或已删除");
        }

        // 2. 角色判断：
        // 情况1：管理员（user_type=0）→ 拥有所有专业、所有教师的上限修改权限
        if (operator.getUserType().equals("ADMIN")) {
            return true;
        }

        // 情况2：教师（user_type=2）→ 仅能查看自己的上限，无修改权限（无论哪个专业）
        if (operator.getUserType().equals("TEACHER")) {
            // 即使修改自己的上限，也抛无权限异常（业务规则：教师上限由管理员统一配置）
            throw new RuntimeException("权限不足：教师角色无法修改指导任务上限，请联系管理员操作");
        }

        // 情况3：学生（user_type=1）或其他角色→ 无任何权限
        throw new RuntimeException("权限不足：仅管理员可修改指导任务上限");
    }

    /**
     * 修改指导任务上限（含前置权限校验+数据合法性校验）
     */
    @Override
    public Major modifyAdvisorMaxCount(Long operatorId, Long targetUserId, Long majorId, Integer newMaxCount) {
        // 1. 前置权限校验
        this.checkModifyMaxCountAuth(operatorId, targetUserId, majorId);

        // 2. 校验目标教师是否存在（且为教师角色）
        SysUser targetTeacher = sysUserService.getById(targetUserId);
        if (targetTeacher == null || targetTeacher.getIsDeleted() == 1) {
            throw new RuntimeException("目标教师不存在或已删除");
        }
        if (!targetTeacher.getUserType().equals("TEACHER")) {
            throw new RuntimeException("目标用户不是教师角色，无需配置指导任务上限");
        }

        // 3. 校验“用户-专业”关联记录是否存在（教师必须已关联该专业）
        QueryWrapper<Major> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", targetUserId)
                .eq("major_id", majorId)
                .eq("is_deleted", 0);
        Major userMajor = this.getOne(queryWrapper);
        if (userMajor == null) {
            throw new RuntimeException("目标教师未关联该专业（专业ID：" + majorId + "），无法修改上限");
        }

        // 4. 校验新上限的合法性（1-50之间，避免不合理值）
        if (newMaxCount < 1 || newMaxCount > 50) {
            throw new IllegalArgumentException("指导任务上限需在1-50之间，请重新输入");
        }

//        // 5. 校验新上限是否小于当前指导数（避免设置的上限低于已分配任务数）
//        Integer currentCount = userMajor.getCurrentAdvisorCount();
//        if (newMaxCount < currentCount) {
//            throw new RuntimeException(
//                    String.format("新上限（%d）不能小于当前指导数（%d），请先减少已分配任务或提高上限",
//                            newMaxCount, currentCount)
//            );
//        }
//
//        // 6. 执行修改
//        userMajor.setMaxAdvisorCount(newMaxCount);
        this.updateById(userMajor);

        return userMajor;
    }
}
