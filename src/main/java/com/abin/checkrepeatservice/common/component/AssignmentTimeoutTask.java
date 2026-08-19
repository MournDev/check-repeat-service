package com.abin.checkrepeatservice.common.component;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherAssignmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class AssignmentTimeoutTask {

    @Resource
    private TeacherAssignmentService teacherAssignmentService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    /**
     * 每天凌晨2点处理超时未确认的分配
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void handleTimeoutAssignments() {
        log.info("开始执行分配超时处理任务");
        try {
            // 查询超时未确认的论文（分配时间超过3天）
            LocalDateTime timeoutTime = LocalDateTime.now().minusDays(3);

            LambdaQueryWrapper<PaperInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PaperInfo::getAllocationStatus, DictConstants.AllocationStatus.PENDING)
                    .lt(PaperInfo::getAllocationTime, timeoutTime);

            List<PaperInfo> timeoutPapers = paperInfoMapper.selectList(queryWrapper);

            int processedCount = 0;
            for (PaperInfo paper : timeoutPapers) {
                try {
                    // 自动拒绝超时未确认的分配
                    Result<Boolean> result = teacherAssignmentService.rejectAssignment(
                            paper.getId(),
                            paper.getTeacherId()
                    );

                    if (result.isSuccess()) {
                        processedCount++;
                        log.info("自动拒绝超时论文成功 - 论文ID: {}, 老师ID: {}", paper.getId(), paper.getTeacherId());
                    } else {
                        log.error("自动拒绝超时论文失败 - 论文ID: {}, 错误: {}", paper.getId(), result.getMessage());
                    }

                } catch (Exception e) {
                    log.error("处理超时论文异常 - 论文ID: {}", paper.getId(), e);
                }
            }

            log.info("分配超时处理任务完成 - 处理数量: {}", processedCount);

        } catch (Exception e) {
            log.error("分配超时处理任务执行异常", e);
        }
    }
}
