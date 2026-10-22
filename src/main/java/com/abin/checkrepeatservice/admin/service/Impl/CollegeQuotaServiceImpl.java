package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.CheckQuotaSaveReq;
import com.abin.checkrepeatservice.admin.mapper.CheckCollegeQuotaMapper;
import com.abin.checkrepeatservice.admin.service.CollegeQuotaService;
import com.abin.checkrepeatservice.admin.vo.CheckCollegeQuotaVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.mapper.CollegeMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckCollegeQuota;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.College;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学院月度查重额度服务实现（论文全生命周期 P5）。
 *
 * 并发安全：扣减用单条条件 UPDATE（used_quota < total_quota 时 +1），依赖 InnoDB 行锁
 * 保证并发下不超扣；影响行数=0 时重查记录区分"额度用尽"与"记录已被删除"。
 *
 * 月度隔离：quota_month（yyyy-MM）天然按月隔离，跨月即新记录，无需清零任务。
 */
@Slf4j
@Service
public class CollegeQuotaServiceImpl implements CollegeQuotaService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private CheckCollegeQuotaMapper checkCollegeQuotaMapper;

    @Resource
    private CollegeMapper collegeMapper;

    // M4.1 失败返还：需要读取任务的扣减标记与论文所属学院
    @Resource
    private com.abin.checkrepeatservice.student.mapper.CheckTaskMapper checkTaskMapper;

    @Resource
    private com.abin.checkrepeatservice.student.mapper.PaperInfoMapper paperInfoMapper;

    @Override
    public boolean checkAndConsume(Long collegeId) {
        // 论文未归属学院：不做额度管控，直接放行
        if (collegeId == null) {
            return false;
        }
        String currentMonth = LocalDate.now().format(MONTH_FORMATTER);
        CheckCollegeQuota quota = selectByCollegeAndMonth(collegeId, currentMonth);
        // 当月未配置额度 = 不限额，直接放行
        if (quota == null) {
            return false;
        }
        // 原子扣减：UPDATE 条件 used_quota < total_quota，行锁保证并发安全
        int rows = checkCollegeQuotaMapper.incrementUsedQuota(quota.getId());
        if (rows > 0) {
            return true;
        }
        // 影响行数=0：重查判定是"额度用尽"还是"记录已被管理员删除"（删除 = 当月不限额，放行）
        CheckCollegeQuota latest = selectByCollegeAndMonth(collegeId, currentMonth);
        if (latest == null) {
            log.info("学院月度额度记录已被删除，本次放行: collegeId={}, quotaMonth={}", collegeId, currentMonth);
            return false;
        }
        log.warn("学院月度查重额度不足: collegeId={}, 已用 {}/{}", collegeId, latest.getUsedQuota(), latest.getTotalQuota());
        throw new BusinessException(ResultCode.BUSINESS_QUOTA_EXCEEDED,
                "本学院当月查重额度已用完（已用 " + latest.getUsedQuota() + "/" + latest.getTotalQuota() + "）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundForFailedTask(Long taskId) {
        if (taskId == null) {
            return;
        }
        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task == null || task.getQuotaCharged() == null || task.getQuotaCharged() != 1) {
            return;
        }
        // 幂等闸门：先把 quota_charged 由 1 原子翻转为 0，只有抢到的调用方执行返还，
        // 避免异常重入 / 定时任务重复扫描导致同一任务多次返还
        int flipped = checkTaskMapper.update(null, new LambdaUpdateWrapper<CheckTask>()
                .eq(CheckTask::getId, taskId)
                .eq(CheckTask::getQuotaCharged, 1)
                .set(CheckTask::getQuotaCharged, 0));
        if (flipped == 0) {
            return;
        }
        PaperInfo paper = paperInfoMapper.selectById(task.getPaperId());
        if (paper == null || paper.getCollegeId() == null) {
            return;
        }
        // 额度在任务创建时扣减，返还也必须回到创建月（跨月失败不侵占本月额度）
        String quotaMonth = task.getCreateTime() != null
                ? task.getCreateTime().format(MONTH_FORMATTER)
                : LocalDate.now().format(MONTH_FORMATTER);
        CheckCollegeQuota quota = selectByCollegeAndMonth(paper.getCollegeId(), quotaMonth);
        if (quota == null) {
            // 额度记录已被删除：当月已不限额，无需返还
            return;
        }
        int refunded = checkCollegeQuotaMapper.decrementUsedQuota(quota.getId());
        if (refunded > 0) {
            log.info("查重任务失败，已返还学院月度额度: taskId={}, collegeId={}, quotaMonth={}",
                    taskId, paper.getCollegeId(), quotaMonth);
        } else {
            log.warn("查重任务失败，额度返还跳过（已用额度为 0 或记录已删）: taskId={}, collegeId={}, quotaMonth={}",
                    taskId, paper.getCollegeId(), quotaMonth);
        }
    }

    @Override
    public Result<Page<CheckCollegeQuotaVO>> listQuotas(String quotaMonth, Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<CheckCollegeQuota> wrapper = new LambdaQueryWrapper<>();
        if (quotaMonth != null && !quotaMonth.isBlank()) {
            wrapper.eq(CheckCollegeQuota::getQuotaMonth, quotaMonth);
        }
        wrapper.orderByDesc(CheckCollegeQuota::getQuotaMonth)
                .orderByAsc(CheckCollegeQuota::getCollegeId);
        // pageSize = -1 时 PaginationInnerInterceptor 不拼 LIMIT，返回全量（"不分页"支持）
        Page<CheckCollegeQuota> page = checkCollegeQuotaMapper.selectPage(
                new Page<>(currentPage, pageSize), wrapper);

        Map<Long, String> collegeNameMap = loadCollegeNameMap(
                page.getRecords().stream()
                        .map(CheckCollegeQuota::getCollegeId)
                        .distinct()
                        .collect(Collectors.toList()));

        List<CheckCollegeQuotaVO> voList = page.getRecords().stream().map(quota -> {
            CheckCollegeQuotaVO vo = new CheckCollegeQuotaVO();
            vo.setId(quota.getId());
            vo.setCollegeId(quota.getCollegeId());
            vo.setCollegeName(collegeNameMap.get(quota.getCollegeId()));
            vo.setQuotaMonth(quota.getQuotaMonth());
            vo.setTotalQuota(quota.getTotalQuota());
            vo.setUsedQuota(quota.getUsedQuota());
            return vo;
        }).collect(Collectors.toList());

        Page<CheckCollegeQuotaVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return Result.success(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> saveQuota(CheckQuotaSaveReq req) {
        String quotaMonth = req.getQuotaMonth();
        if (quotaMonth == null || quotaMonth.isBlank()) {
            // 默认当前月
            quotaMonth = LocalDate.now().format(MONTH_FORMATTER);
        } else if (!quotaMonth.matches("\\d{4}-\\d{2}")) {
            return Result.error(ResultCode.PARAM_ERROR, "quotaMonth 格式必须为 yyyy-MM，如 2026-10");
        }

        CheckCollegeQuota exist = selectByCollegeAndMonth(req.getCollegeId(), quotaMonth);
        if (exist != null) {
            // 已存在：只改 total（used 保留）
            exist.setTotalQuota(req.getTotalQuota());
            checkCollegeQuotaMapper.updateById(exist);
        } else {
            CheckCollegeQuota quota = new CheckCollegeQuota();
            quota.setCollegeId(req.getCollegeId());
            quota.setQuotaMonth(quotaMonth);
            quota.setTotalQuota(req.getTotalQuota());
            quota.setUsedQuota(0);
            try {
                checkCollegeQuotaMapper.insert(quota);
            } catch (DuplicateKeyException e) {
                // 并发插入撞 (college_id, quota_month) 唯一键：退化为更新 total
                CheckCollegeQuota race = selectByCollegeAndMonth(req.getCollegeId(), quotaMonth);
                if (race != null) {
                    race.setTotalQuota(req.getTotalQuota());
                    checkCollegeQuotaMapper.updateById(race);
                }
            }
        }
        log.info("学院月度查重额度保存成功: collegeId={}, quotaMonth={}, totalQuota={}",
                req.getCollegeId(), quotaMonth, req.getTotalQuota());
        return Result.success("学院月度查重额度保存成功");
    }

    /**
     * 按 (college_id, quota_month) 查额度记录（唯一键，LIMIT 1 兜底）
     */
    private CheckCollegeQuota selectByCollegeAndMonth(Long collegeId, String quotaMonth) {
        return checkCollegeQuotaMapper.selectOne(new LambdaQueryWrapper<CheckCollegeQuota>()
                .eq(CheckCollegeQuota::getCollegeId, collegeId)
                .eq(CheckCollegeQuota::getQuotaMonth, quotaMonth)
                .last("LIMIT 1"));
    }

    /**
     * 批量查学院名称（删除的学院会被 @TableLogic 过滤，VO 侧留空）
     */
    private Map<Long, String> loadCollegeNameMap(List<Long> collegeIds) {
        if (collegeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return collegeMapper.selectBatchIds(collegeIds).stream()
                .collect(Collectors.toMap(College::getId, College::getCollegeName, (a, b) -> a));
    }
}
