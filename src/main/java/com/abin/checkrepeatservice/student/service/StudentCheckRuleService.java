package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.vo.StudentCheckRuleVO;

/**
 * 学生端查重规则查询服务接口。
 *
 * <p>规则配置本身归管理端（/api/admin/config/check-rule/**）；学生只需要"我的规则是什么"，
 * 所以这里只开一个只读接口，不复用管理端的分页/列表能力。</p>
 */
public interface StudentCheckRuleService {

    /**
     * 查询指定学生当前生效的查重规则。
     *
     * @param studentId 学生ID
     * @return 生效规则（含按该生学院/专业解析出的通过阈值）
     */
    Result<StudentCheckRuleVO> getCurrentCheckRule(Long studentId);
}
