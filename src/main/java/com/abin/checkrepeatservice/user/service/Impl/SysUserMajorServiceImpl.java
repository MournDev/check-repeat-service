package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.mapper.SysUserMajorMapper;
import com.abin.checkrepeatservice.user.service.SysUserMajorService;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SysUserMajorServiceImpl extends ServiceImpl<SysUserMajorMapper, Major> implements SysUserMajorService {

    private static final int MIN_MAX_COUNT = 1;
    private static final int MAX_MAX_COUNT = 50;

    @Resource
    private SysUserService sysUserService;

    /**
     * 完整权限校验逻辑：管理员可修改所有，教师/学生无修改权限
     */
    @Override
    public boolean checkModifyMaxCountAuth(Long operatorId, Long targetUserId, Long majorId) {
        // 1. 校验操作人是否存在（排除已删除用户）
        SysUser operator = sysUserService.getByIdWithNotDeleted(operatorId);
        if (operator == null) {
            throw new RuntimeException("操作人不存在或已删除");
        }

        // 2. 角色判断：仅管理员拥有上限修改权限
        if (sysUserService.isAdmin(operatorId)) {
            return true;
        }
        if (sysUserService.isTeacher(operatorId)) {
            // 即使修改自己的上限，也抛无权限异常（业务规则：教师上限由管理员统一配置）
            throw new RuntimeException("权限不足：教师角色无法修改指导任务上限，请联系管理员操作");
        }
        throw new RuntimeException("权限不足：仅管理员可修改指导任务上限");
    }

    /**
     * 修改指导任务上限（含前置权限校验+数据合法性校验）
     * 上限写入 sys_user.max_review_count —— 分配逻辑（Admin/自动分配）实际读取的字段；
     * majorId 保留在签名中以兼容既有接口契约，当前不参与写入。
     */
    @Override
    public SysUser modifyAdvisorMaxCount(Long operatorId, Long targetUserId, Long majorId, Integer newMaxCount) {
        // 1. 前置权限校验
        this.checkModifyMaxCountAuth(operatorId, targetUserId, majorId);

        // 2. 校验目标教师是否存在（且为教师角色）
        SysUser targetTeacher = sysUserService.getByIdWithNotDeleted(targetUserId);
        if (targetTeacher == null) {
            throw new RuntimeException("目标教师不存在或已删除");
        }
        if (!sysUserService.isTeacher(targetUserId)) {
            throw new RuntimeException("目标用户不是教师角色，无需配置指导任务上限");
        }

        // 3. 校验新上限的合法性（1-50之间，避免不合理值）
        if (newMaxCount == null || newMaxCount < MIN_MAX_COUNT || newMaxCount > MAX_MAX_COUNT) {
            throw new IllegalArgumentException("指导任务上限需在1-50之间，请重新输入");
        }

        // 4. 执行修改
        targetTeacher.setMaxReviewCount(newMaxCount);
        sysUserService.updateById(targetTeacher);

        return targetTeacher;
    }
}
