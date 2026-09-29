package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户-专业关联服务接口（含权限校验）
 */
public interface SysUserMajorService extends IService<Major> {


    /**
     * 校验操作人是否有权限修改指导任务上限
     * @param operatorId 操作人ID（当前登录用户ID）
     * @param targetUserId 目标教师ID（被修改上限的教师）
     * @param majorId 专业ID（保留以兼容既有接口契约，当前不参与校验）
     * @return 有权限返回true，无权限抛异常
     */
    boolean checkModifyMaxCountAuth(Long operatorId, Long targetUserId, Long majorId);

    /**
     * 修改指导任务上限（含权限校验）
     * 上限写入 sys_user.max_review_count —— 分配逻辑（Admin/自动分配）实际读取的字段
     * @param operatorId 操作人ID
     * @param targetUserId 目标教师ID
     * @param majorId 专业ID（保留以兼容既有接口契约，当前不参与写入）
     * @param newMaxCount 新的指导任务上限
     * @return 修改后的教师（SysUser）
     */
    SysUser modifyAdvisorMaxCount(Long operatorId, Long targetUserId, Long majorId, Integer newMaxCount);
}
