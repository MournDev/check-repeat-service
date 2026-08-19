package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserQueryService {

    private final SysUserMapper sysUserMapper;

    /**
     * 根据用户ID获取用户信息（包含邮箱）
     */
    public SysUser getUserById(Long userId) {
        return sysUserMapper.selectById(userId);
    }

    /**
     * 根据论文信息获取学生用户信息
     */
    public SysUser getStudentByPaper(PaperInfo paper) {
        return sysUserMapper.selectById(paper.getStudentId());
    }

    /**
     * 根据指导老师ID获取老师信息
     */
    public SysUser getAdvisorById(Long advisorId) {
        if (advisorId == null) {
            return null;
        }
        return sysUserMapper.selectById(advisorId);
    }
}
