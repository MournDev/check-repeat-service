package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.pojo.entity.AdvisorEvaluation;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperStatusLog;
import com.abin.checkrepeatservice.mapper.AdvisorEvaluationMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 导师评价临期催评定时任务（论文全生命周期 P6b）。
 *
 * 行为（默认关闭，由 schedule.advisor-eval.remind-enabled 控制）：
 *  - 每天上午 9 点扫描「可评未评」的论文：状态 defense_passed/finalized、已分配导师、
 *    尚无评价记录，且评价窗口将在 remind-days-before 天内关闭；
 *  - 命中即给学生补发催评通知（ADVISOR_EVAL_REMIND），提升毕业季评价完成率；
 *  - Redis SETNX 去重（键 schedule:advisor-eval:reminded:{paperId}，TTL=剩余天数+1天），
 *    同一论文整个窗口只催一次，服务重启/多实例不重发；
 *  - 存量无状态日志论文窗口无界（与提交侧口径一致），无法计算临期，天然跳过。
 *
 * 与 ReviewAssignTimeoutScheduler 同一套保守约定：默认不启用，确认输出后再打开。
 */
@Component
@Slf4j
public class AdvisorEvalRemindScheduler {

    /** 催评去重键前缀（value 固定 1，TTL 到窗口关闭后失效） */
    private static final String REMINDED_KEY_PREFIX = "schedule:advisor-eval:reminded:";

    @Resource
    private AdvisorEvaluationMapper advisorEvaluationMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 催评开关，默认关：开启后会对学生发通知邮件 */
    @Value("${schedule.advisor-eval.remind-enabled:false}")
    private boolean remindEnabled;

    /** 窗口剩余 N 天内未评价则催评（与 admin.advisor-eval.window-days 同前缀） */
    @Value("${admin.advisor-eval.remind-days-before:3}")
    private int remindDaysBefore;

    /** 评价窗口天数，需与提交侧 AdvisorEvaluationServiceImpl 同源配置 */
    @Value("${admin.advisor-eval.window-days:30}")
    private int evalWindowDays;

    @Scheduled(cron = "0 0 9 * * ?") // 每天上午9点扫描一次
    public void remindBeforeWindowClose() {
        if (!remindEnabled) {
            log.debug("导师评价催评已关闭（schedule.advisor-eval.remind-enabled=false），跳过");
            return;
        }
        try {
            // 1. 可评论文：终期答辩通过/已定稿 且 已分配导师
            List<PaperInfo> eligiblePapers = paperInfoMapper.selectList(new LambdaQueryWrapper<PaperInfo>()
                    .in(PaperInfo::getPaperStatus,
                            DictConstants.PaperStatus.DEFENSE_PASSED, DictConstants.PaperStatus.FINALIZED)
                    .isNotNull(PaperInfo::getTeacherId));
            if (eligiblePapers.isEmpty()) {
                return;
            }

            // 2. 剔除已评价的（advisor_evaluation 与 paper 一对一）
            Set<Long> evaluatedPaperIds = advisorEvaluationMapper.selectList(
                            new LambdaQueryWrapper<AdvisorEvaluation>().select(AdvisorEvaluation::getPaperId))
                    .stream().map(AdvisorEvaluation::getPaperId).collect(Collectors.toSet());
            List<PaperInfo> unevaluated = eligiblePapers.stream()
                    .filter(p -> !evaluatedPaperIds.contains(p.getId()))
                    .toList();
            if (unevaluated.isEmpty()) {
                return;
            }

            // 3. 批量取每篇论文最新一条 defense_passed 日志，算窗口截止
            Map<Long, PaperStatusLog> latestLogMap = paperStatusLogMapper.selectList(
                            new LambdaQueryWrapper<PaperStatusLog>()
                                    .in(PaperStatusLog::getPaperId, unevaluated.stream().map(PaperInfo::getId).toList())
                                    .eq(PaperStatusLog::getNewStatus, DictConstants.PaperStatus.DEFENSE_PASSED)
                                    .orderByDesc(PaperStatusLog::getCreateTime))
                    .stream()
                    .collect(Collectors.toMap(PaperStatusLog::getPaperId, Function.identity(),
                            // 同论文多条日志时保留时间最新的一条（后写覆盖先写，列表已按时间降序）
                            (first, second) -> first));

            LocalDateTime now = LocalDateTime.now();
            int reminded = 0;
            for (PaperInfo paper : unevaluated) {
                try {
                    PaperStatusLog passedLog = latestLogMap.get(paper.getId());
                    // 无日志=窗口无界（存量老数据），无法定义"临期"，跳过
                    if (passedLog == null || passedLog.getCreateTime() == null) {
                        continue;
                    }
                    LocalDateTime windowEnd = passedLog.getCreateTime().plusDays(evalWindowDays);
                    long daysLeft = Duration.between(now, windowEnd).toDays();
                    // 已关闭的不催（提交侧也会拦）；剩余天数超出催评提前量也不催
                    if (daysLeft < 0 || daysLeft > remindDaysBefore) {
                        continue;
                    }
                    // 4. Redis SETNX 去重：整个窗口只催一次（TTL=剩余天数+1天，窗口过失效自清理）
                    String remindKey = REMINDED_KEY_PREFIX + paper.getId();
                    Boolean firstTime = stringRedisTemplate.opsForValue()
                            .setIfAbsent(remindKey, "1", Duration.ofDays(daysLeft + 1));
                    if (!Boolean.TRUE.equals(firstTime)) {
                        continue;
                    }
                    notificationFacadeService.sendAdvisorEvalRemindNotice(
                            paper.getId(), paper.getPaperTitle(), paper.getStudentId(), windowEnd);
                    reminded++;
                } catch (Exception e) {
                    // 单篇失败不阻断本轮扫描（去重键未写入时下一轮会重试该论文）
                    log.error("导师评价催评处理失败：paperId={}", paper.getId(), e);
                }
            }
            if (reminded > 0) {
                log.info("导师评价临期催评完成：本轮催评{}人（窗口剩{}天内）", reminded, remindDaysBefore);
            }
        } catch (Exception e) {
            log.error("导师评价催评扫描执行失败", e);
        }
    }
}
