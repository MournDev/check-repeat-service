package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.Major;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户-专业关联服务接口（含权限校验）
 */
public interface SysUserMajorService extends IService<Major> {


    /**
     * 校验操作人是否有权限修改指导任务上限
     * @param operatorId 操作人ID（当前登录用户ID）
     * @param targetUserId 目标教师ID（被修改上限的教师）
     * @param majorId 专业ID（修改的是该教师在该专业下的上限）
     * @return 有权限返回true，无权限抛异常
     */
    boolean checkModifyMaxCountAuth(Long operatorId, Long targetUserId, Long majorId);

    /**
     * 修改指导任务上限（含权限校验）
     * @param operatorId 操作人ID
     * @param targetUserId 目标教师ID
     * @param majorId 专业ID
     * @param newMaxCount 新的指导任务上限
     * @return 修改后的SysUserMajor实体
     */
    Major modifyAdvisorMaxCount(Long operatorId, Long targetUserId, Long majorId, Integer newMaxCount);
}
