package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;

import java.util.List;

public interface CheckTaskService {
    /**
     * 创建查重任务（学生本人发起）
     * @param paperId 论文ID
     * @return 查重任务结果
     */
    Result<CheckResultVO> createCheckTask(Long paperId);

    /**
     * 创建查重任务，显式指定论文归属学生。
     *
     * <p>教师确认接收论文后由系统代学生触发查重时使用：查重归属校验必须按
     * 「论文所属学生」判断，不能用当前登录教师 ID 去比对 paper.studentId，
     * 否则教师触发路径必然校验失败（P0-8）。</p>
     *
     * @param paperId   论文ID
     * @param studentId 论文归属学生ID；为 null 时回退到当前登录用户
     * @return 查重任务结果
     */
    Result<CheckResultVO> createCheckTask(Long paperId, Long studentId);

    /**
     * 获取查重任务列表
     * @param paperId 论文ID
     * @param checkStatus 状态
     * @return 查重任务详情
     */
    Result<List<CheckTaskResultDTO>> getMyCheckTaskList(Long paperId, Integer checkStatus);

    /**
     * 获取查重任务详情
     * @param paperId 论文ID
     * @return 删除结果
     */
    Result<CheckTaskResultDTO> getCheckTaskDetail(Long paperId);

    /**
     * 教师端获取查重任务详情（按任务ID查询，并校验论文归属）
     *
     * @param teacherId 当前教师ID
     * @param taskId 查重任务ID
     * @return 查重任务详情
     */
    Result<CheckTaskResultDTO> getTeacherCheckTaskDetail(Long teacherId, Long taskId);

    /**
     * 取消待执行的查重任务
     *
     * @param taskId 待取消的任务ID
     * @return 删除结果
     */
    Result<String> cancelCheckTask(Long taskId);

    /**
     * 获取查重结果
     * @param paperId 论文ID
     * @return 查重结果
     */
    CheckResultVO getCheckResult(Long paperId);


}
