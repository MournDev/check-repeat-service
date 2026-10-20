package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperCheckSignoff;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.GuidanceProgressVO;
import com.abin.checkrepeatservice.teacher.dto.PaperCheckSignoffReq;
import com.abin.checkrepeatservice.teacher.dto.PaperCheckSignoffVO;
import com.abin.checkrepeatservice.teacher.mapper.PaperCheckSignoffMapper;
import com.abin.checkrepeatservice.teacher.service.CheckReportSignoffService;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教师端查重报告签核服务实现（论文全生命周期 P5）。
 *
 * 关键规则：
 *  - 仅论文指导教师本人（paper_info.teacher_id == 当前用户）可签核，超管/其他教师一律 40304；
 *  - 签核对象是「该论文最新一次 completed 查重任务」（同一论文可重复检测产生多个任务，
 *    新任务即新一轮签核机会），paper_check_signoff (paper_id, task_id) 唯一键兜底并发重复；
 *  - qualified 统一必填指导评分：不合格签核（qualified=0）也要求评分——它是成绩加权合成的
 *    "指导分"数据源，缺了它终期合成会抛 40914；
 *  - qualified=0 时通过通知门面给学生发退回通知（门面内部吞异常，不阻断签核事务）；
 *  - 签核不改论文状态：退回/放行的状态语义由评阅分配门槛（40912）与阶段门各自表达，
 *    这里不越权推进 paper_status。
 */
@Slf4j
@Service
public class CheckReportSignoffServiceImpl implements CheckReportSignoffService {

    /** 签核结论：合格放行 */
    private static final int QUALIFIED_PASS = 1;
    /** 签核结论：不合格退回 */
    private static final int QUALIFIED_REJECT = 0;

    /** 指导看板论文明细条数上限（create_time desc 取最近 50 条） */
    private static final int DASHBOARD_PAPER_LIMIT = 50;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperCheckSignoffMapper paperCheckSignoffMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> sign(PaperCheckSignoffReq req) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 论文存在性 + 归属校验：只有指导教师本人能签自己的论文。
        //    用 PERMISSION_NO_ACCESS（40304）而不是 PERMISSION_TEACHER_OWNER（40303）——
        //    后者文案是"仅指导该任务的教师可操作"，语义绑定评阅任务，签核场景用通用无权限码。
        PaperInfo paper = paperInfoMapper.selectById(req.getPaperId());
        if (paper == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除", null);
        }
        if (!Objects.equals(paper.getTeacherId(), currentUserId)) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "仅论文指导教师可签核查重报告", null);
        }

        // 2. qualified 取值域校验（1-合格 / 0-不合格）；@NotNull 已拦 null，这里拦越界值
        if (!Integer.valueOf(QUALIFIED_PASS).equals(req.getQualified())
                && !Integer.valueOf(QUALIFIED_REJECT).equals(req.getQualified())) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "签核结论只能是 1（合格）或 0（不合格）", null);
        }

        // 3. 定位签核对象：该论文最新一次 completed 查重任务。
        //    create_time 只到秒，同一秒内可能有重检任务，故补 id 倒序兜底（与 CheckRule 列表同口径）
        CheckTask latestTask = checkTaskMapper.selectOne(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getPaperId, req.getPaperId())
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                .orderByDesc(CheckTask::getCreateTime)
                .orderByDesc(CheckTask::getId)
                .last("LIMIT 1"));
        if (latestTask == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "该论文暂无已完成的查重任务，无法签核", null);
        }

        // 4. 重复签核校验：同一任务只允许签一次（唯一键 (paper_id, task_id) 在库里兜底，
        //    这里先查是为了给出 40913 业务语义而非裸 DuplicateKeyException）
        long signedCount = paperCheckSignoffMapper.selectCount(new LambdaQueryWrapper<PaperCheckSignoff>()
                .eq(PaperCheckSignoff::getPaperId, req.getPaperId())
                .eq(PaperCheckSignoff::getTaskId, latestTask.getId()));
        if (signedCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_CHECK_ALREADY_SIGNOFF);
        }

        // 5. 落库：comment 纯文本 XSS 清洗（与答辩记录/评阅意见同口径）；
        //    reportId 直接取任务回填的报告ID，便于前端从签核记录跳查重报告
        PaperCheckSignoff signoff = new PaperCheckSignoff();
        signoff.setPaperId(req.getPaperId());
        signoff.setTaskId(latestTask.getId());
        signoff.setReportId(latestTask.getReportId());
        signoff.setAdvisorId(currentUserId);
        signoff.setQualified(req.getQualified());
        // 指导评分统一必填：它同时是成绩加权合成的"指导分"数据源，
        // 不合格签核也必须给分（DTO 层 @NotNull + @DecimalMin/Max 已拦）
        signoff.setGuidanceScore(req.getGuidanceScore());
        signoff.setComment(StringUtils.hasText(req.getComment())
                ? Jsoup.clean(req.getComment(), Safelist.none()) : null);
        signoff.setSignTime(LocalDateTime.now());
        UserBusinessInfoUtils.setAuditField(signoff, true);
        paperCheckSignoffMapper.insert(signoff);

        // 6. 不合格签核 → 退回通知学生（门面吞异常：通知挂了也不能让学生白签，
        //    更不能让 @Transactional 回滚签核记录）。合格签核不另行通知——
        //    后续评阅分配/成绩合成有自己的学生侧触点，避免重复打扰。
        if (Integer.valueOf(QUALIFIED_REJECT).equals(req.getQualified()) && paper.getStudentId() != null) {
            notificationFacadeService.sendCheckSignoffRejectedNotice(paper.getStudentId(), paper.getPaperTitle());
        }

        log.info("查重报告签核成功：signoffId={}，paperId={}，taskId={}，qualified={}，advisorId={}",
                signoff.getId(), req.getPaperId(), latestTask.getId(), req.getQualified(), currentUserId);

        return Result.success("查重报告签核成功", String.valueOf(signoff.getId()));
    }

    @Override
    public Result<PaperCheckSignoffVO> getSignoff(Long paperId) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 论文存在性 + 归属校验：只有指导教师本人可查看本论文签核记录
        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        if (!Objects.equals(paper.getTeacherId(), currentUserId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "仅论文指导教师可查看签核记录");
        }

        // 2. 取最新一条签核记录；同一论文重检后会有多条，按签核时间倒序、ID 倒序兜底
        PaperCheckSignoff signoff = paperCheckSignoffMapper.selectOne(
                new LambdaQueryWrapper<PaperCheckSignoff>()
                        .eq(PaperCheckSignoff::getPaperId, paperId)
                        .orderByDesc(PaperCheckSignoff::getSignTime)
                        .orderByDesc(PaperCheckSignoff::getId)
                        .last("LIMIT 1"));
        if (signoff == null) {
            return Result.success("该论文尚未签核", null);
        }

        PaperCheckSignoffVO vo = new PaperCheckSignoffVO();
        vo.setPaperId(signoff.getPaperId());
        vo.setTaskId(signoff.getTaskId());
        vo.setReportId(signoff.getReportId());
        vo.setAdvisorId(signoff.getAdvisorId());
        vo.setQualified(signoff.getQualified());
        vo.setGuidanceScore(signoff.getGuidanceScore());
        vo.setComment(signoff.getComment());
        vo.setSignTime(signoff.getSignTime());

        SysUser advisor = signoff.getAdvisorId() == null ? null
                : sysUserMapper.selectById(signoff.getAdvisorId());
        vo.setAdvisorName(advisor == null ? "未知教师"
                : (StringUtils.hasText(advisor.getRealName()) ? advisor.getRealName() : advisor.getUsername()));

        return Result.success("查询成功", vo);
    }

    @Override
    public Result<GuidanceProgressVO> guidanceProgress() {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();

        // 按指导教师取全量（@TableLogic 自动附加 is_deleted=0）。教师名下论文量级有限
        //（几十到几百），一次查出后内存做 total/状态分布/最近 50 条三件事，
        // 不走 DB 端 GROUP BY —— only_full_group_by 模式下按状态分组要整列进 GROUP BY，
        // 内存分组更省心也更好控制排序
        List<PaperInfo> papers = paperInfoMapper.selectList(new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getTeacherId, teacherId)
                .orderByDesc(PaperInfo::getCreateTime)
                .orderByDesc(PaperInfo::getId));

        GuidanceProgressVO vo = new GuidanceProgressVO();
        vo.setTotal((long) papers.size());

        // 状态分布：TreeMap 按状态码排序，输出稳定；null 状态归入 unknown 而不是 NPE
        Map<String, Long> statusCount = papers.stream()
                .collect(Collectors.groupingBy(
                        p -> StringUtils.hasText(p.getPaperStatus()) ? p.getPaperStatus() : "unknown",
                        TreeMap::new,
                        Collectors.counting()));
        vo.setStatusSummary(statusCount.entrySet().stream()
                .map(e -> {
                    GuidanceProgressVO.StatusSummaryItem item = new GuidanceProgressVO.StatusSummaryItem();
                    item.setPaperStatus(e.getKey());
                    item.setCount(e.getValue());
                    return item;
                })
                .toList());

        // 论文明细：全量已按 create_time desc 排好，截取最近 50 条
        List<PaperInfo> recentPapers = papers.stream()
                .limit(DASHBOARD_PAPER_LIMIT)
                .toList();

        // 批量补学生姓名（realName 优先，空回落 username，与答辩 VO 组装同口径）
        List<Long> studentIds = recentPapers.stream()
                .map(PaperInfo::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysUser> studentMap = studentIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        vo.setPapers(recentPapers.stream().map(p -> {
            GuidanceProgressVO.PaperItem item = new GuidanceProgressVO.PaperItem();
            item.setPaperId(p.getId());
            item.setPaperTitle(p.getPaperTitle());
            SysUser student = p.getStudentId() == null ? null : studentMap.get(p.getStudentId());
            item.setStudentName(student == null ? null
                    : (StringUtils.hasText(student.getRealName()) ? student.getRealName() : student.getUsername()));
            item.setPaperStatus(p.getPaperStatus());
            item.setSimilarityRate(p.getSimilarityRate());
            item.setFinalScore(p.getFinalScore());
            return item;
        }).toList());

        return Result.success("指导看板查询成功", vo);
    }
}
