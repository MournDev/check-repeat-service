package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.DefenseBatchReq;
import com.abin.checkrepeatservice.admin.dto.DefenseEnrollReq;
import com.abin.checkrepeatservice.admin.dto.DefenseGroupMemberReq;
import com.abin.checkrepeatservice.admin.mapper.DefenseBatchMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseGroupMemberMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseRecordMapper;
import com.abin.checkrepeatservice.admin.service.AdminDefenseService;
import com.abin.checkrepeatservice.admin.vo.DefenseBatchVO;
import com.abin.checkrepeatservice.admin.vo.DefenseGroupMemberVO;
import com.abin.checkrepeatservice.admin.vo.DefenseRecordExcelVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.mapper.CollegeMapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.College;
import com.abin.checkrepeatservice.pojo.entity.DefenseBatch;
import com.abin.checkrepeatservice.pojo.entity.DefenseGroupMember;
import com.abin.checkrepeatservice.pojo.entity.DefenseRecord;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.vo.DefenseRecordVO;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端答辩批次管理服务实现（论文全生命周期 P3）。
 *
 * 关键规则：
 *  - 中期/终期共用一张表，batch_type 区分；
 *  - 学生入批时按批次类型经阶段门推进 paper_status：
 *      mid_term：reviewing → mid_defense（评阅完成才进中期答辩）
 *      final：mid_defense → final_defense（中期通过才进终期答辩）
 *    不符合前置状态的论文跳过并记入 skipReasons；
 *  - 批次删除仅限筹备中（status=0）状态，软删；
 *  - 组成员分配 (batch_id, teacher_id) 唯一索引兜底，重复加入按跳过处理。
 */
@Slf4j
@Service
public class AdminDefenseServiceImpl implements AdminDefenseService {

    @Resource
    private DefenseBatchMapper defenseBatchMapper;

    @Resource
    private DefenseGroupMemberMapper defenseGroupMemberMapper;

    @Resource
    private DefenseRecordMapper defenseRecordMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CollegeMapper collegeMapper;

    @Resource
    private PaperStageGate paperStageGate;

    @Resource
    private PaperStatusLogService paperStatusLogService;

    // ==================== 批次 CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DefenseBatchVO> createBatch(DefenseBatchReq req) {
        Long operatorId = UserBusinessInfoUtils.getCurrentUserId();

        // 批次编号唯一性预校验（友好提示，避免直接撞唯一索引报 500）
        Long existCount = defenseBatchMapper.selectCount(new LambdaQueryWrapper<DefenseBatch>()
                .eq(DefenseBatch::getBatchNo, req.getBatchNo()));
        if (existCount != null && existCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "批次编号已存在：" + req.getBatchNo());
        }

        DefenseBatch batch = new DefenseBatch();
        batch.setBatchNo(req.getBatchNo());
        batch.setBatchType(req.getBatchType());
        batch.setCollegeId(req.getCollegeId());
        batch.setDefenseDate(req.getDefenseDate());
        batch.setLocation(req.getLocation());
        batch.setMode(req.getMode());
        batch.setLeaderId(req.getLeaderId());
        batch.setSecretaryId(req.getSecretaryId());
        batch.setStatus(DefenseBatch.STATUS_PREPARING);
        batch.setRemark(req.getRemark());
        defenseBatchMapper.insert(batch);

        log.info("答辩批次创建成功：batchId={}，batchNo={}，batchType={}，operatorId={}",
                batch.getId(), batch.getBatchNo(), batch.getBatchType(), operatorId);

        return Result.success("答辩批次创建成功", toVO(batch, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DefenseBatchVO> updateBatch(Long id, DefenseBatchReq req) {
        DefenseBatch batch = mustGetBatch(id);

        // 批次类型不可改（避免状态机错位：mid_term 改 final 会让已入册论文的前置状态校验失效）
        if (!batch.getBatchType().equals(req.getBatchType())) {
            throw new BusinessException(ResultCode.PARAM_VALUE_INVALID, "批次类型不可修改");
        }
        // 已结束的批次不可改（结论已提交，改时间/地点会破坏审计一致性）
        if (Integer.valueOf(DefenseBatch.STATUS_FINISHED).equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "已结束的批次不可编辑");
        }

        DefenseBatch update = new DefenseBatch();
        update.setId(id);
        update.setCollegeId(req.getCollegeId());
        update.setDefenseDate(req.getDefenseDate());
        update.setLocation(req.getLocation());
        update.setMode(req.getMode());
        update.setLeaderId(req.getLeaderId());
        update.setSecretaryId(req.getSecretaryId());
        update.setRemark(req.getRemark());
        defenseBatchMapper.updateById(update);

        // 回读最新值返回
        DefenseBatch latest = defenseBatchMapper.selectById(id);
        return Result.success("答辩批次更新成功", toVO(latest, true));
    }

    @Override
    public Result<Page<DefenseBatchVO>> listBatches(String batchType, Long collegeId, Integer status,
                                                    Integer currentPage, Integer pageSize) {
        int page = currentPage == null || currentPage < 1 ? 1 : currentPage;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<DefenseBatch> wrapper = new LambdaQueryWrapper<DefenseBatch>()
                .orderByDesc(DefenseBatch::getDefenseDate)
                .orderByDesc(DefenseBatch::getCreateTime);
        if (StringUtils.hasText(batchType)) {
            wrapper.eq(DefenseBatch::getBatchType, batchType);
        }
        if (collegeId != null) {
            wrapper.eq(DefenseBatch::getCollegeId, collegeId);
        }
        if (status != null) {
            wrapper.eq(DefenseBatch::getStatus, status);
        }

        Page<DefenseBatch> result = defenseBatchMapper.selectPage(new Page<>(page, size), wrapper);
        Page<DefenseBatchVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(enrichBatchList(result.getRecords()));
        return Result.success("答辩批次列表查询成功", voPage);
    }

    @Override
    public Result<DefenseBatchVO> getBatchDetail(Long id) {
        DefenseBatch batch = mustGetBatch(id);
        return Result.success("答辩批次详情查询成功", toVO(batch, true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteBatch(Long id) {
        DefenseBatch batch = mustGetBatch(id);
        // 仅筹备中状态可删：进行中/已结束的批次已有答辩记录与状态推进留痕，软删会断链
        if (!Integer.valueOf(DefenseBatch.STATUS_PREPARING).equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "仅筹备中状态的批次可删除");
        }
        defenseBatchMapper.deleteById(id);
        // 同步软删组成员（按业务一致性，避免脏数据）
        defenseGroupMemberMapper.delete(new LambdaQueryWrapper<DefenseGroupMember>()
                .eq(DefenseGroupMember::getBatchId, id));
        log.info("答辩批次删除成功：batchId={}，operatorId={}", id, UserBusinessInfoUtils.getCurrentUserId());
        return Result.success("答辩批次删除成功", null);
    }

    // ==================== 答辩组分配 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> assignMembers(Long batchId, DefenseGroupMemberReq req) {
        // 路径参数与请求体批次ID一致性校验（防误传）
        if (req.getBatchId() != null && !Objects.equals(req.getBatchId(), batchId)) {
            throw new BusinessException(ResultCode.PARAM_VALUE_INVALID, "路径批次ID与请求体不一致");
        }
        DefenseBatch batch = mustGetBatch(batchId);
        if (Integer.valueOf(DefenseBatch.STATUS_FINISHED).equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "已结束的批次不可修改答辩组");
        }

        // 解析组长ID：显式指定优先，否则取列表第一个
        List<Long> teacherIds = new ArrayList<>(new LinkedHashSet<>(req.getTeacherIds()));
        Long leaderId = req.getLeaderTeacherId() != null ? req.getLeaderTeacherId() : teacherIds.get(0);

        // 校验组长必须在列表中
        if (!teacherIds.contains(leaderId)) {
            throw new BusinessException(ResultCode.PARAM_VALUE_INVALID, "组长必须在答辩组教师列表中");
        }

        // 校验教师身份有效性
        List<SysUser> teachers = sysUserMapper.selectBatchIds(teacherIds);
        Map<Long, SysUser> teacherMap = teachers.stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        for (Long tid : teacherIds) {
            SysUser t = teacherMap.get(tid);
            if (t == null || !"TEACHER".equals(t.getUserType()) || !Integer.valueOf(1).equals(t.getStatus())) {
                throw new BusinessException(ResultCode.BUSINESS_ILLEGAL,
                        "教师" + tid + "不存在或不是启用状态的教师");
            }
        }

        // 落库；唯一索引冲突（重复加入）按跳过处理
        int success = 0;
        List<String> skips = new ArrayList<>();
        for (Long tid : teacherIds) {
            String role = Objects.equals(tid, leaderId)
                    ? DefenseGroupMember.ROLE_LEADER
                    : (StringUtils.hasText(req.getDefaultRole()) ? req.getDefaultRole()
                            : DefenseGroupMember.ROLE_MEMBER);
            try {
                DefenseGroupMember member = new DefenseGroupMember();
                member.setBatchId(batchId);
                member.setTeacherId(tid);
                member.setGroupRole(role);
                defenseGroupMemberMapper.insert(member);
                success++;
            } catch (DuplicateKeyException e) {
                skips.add("教师" + tid + "：已在答辩组中");
            }
        }

        // 同步回填 defense_batch.leader_id（便于组长视角快速识别）
        if (batch.getLeaderId() == null || !batch.getLeaderId().equals(leaderId)) {
            DefenseBatch update = new DefenseBatch();
            update.setId(batchId);
            update.setLeaderId(leaderId);
            defenseBatchMapper.updateById(update);
        }

        log.info("答辩组分配完成：batchId={}，leaderId={}，成功={}，跳过={}",
                batchId, leaderId, success, skips.size());

        return Result.success("答辩组分配完成", Map.of(
                "total", teacherIds.size(),
                "success", success,
                "skipped", skips.size(),
                "skipReasons", skips,
                "leaderId", leaderId));
    }

    @Override
    public Result<List<DefenseGroupMemberVO>> listBatchMembers(Long batchId) {
        mustGetBatch(batchId);
        return Result.success("答辩组成员名单查询成功", loadBatchMembers(batchId));
    }

    // ==================== 学生入批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> enrollStudents(Long batchId, DefenseEnrollReq req) {
        if (req.getBatchId() != null && !Objects.equals(req.getBatchId(), batchId)) {
            throw new BusinessException(ResultCode.PARAM_VALUE_INVALID, "路径批次ID与请求体不一致");
        }
        DefenseBatch batch = mustGetBatch(batchId);
        if (Integer.valueOf(DefenseBatch.STATUS_FINISHED).equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "已结束的批次不可入册学生");
        }

        // 按批次类型决定目标状态与前置状态
        boolean isMid = DefenseBatch.TYPE_MID_TERM.equals(batch.getBatchType());
        String targetStatus = isMid ? DictConstants.PaperStatus.MID_DEFENSE
                : DictConstants.PaperStatus.FINAL_DEFENSE;

        List<Long> paperIds = new ArrayList<>(new LinkedHashSet<>(req.getPaperIds()));
        List<PaperInfo> papers = paperInfoMapper.selectBatchIds(paperIds);
        Map<Long, PaperInfo> paperMap = papers.stream()
                .collect(Collectors.toMap(PaperInfo::getId, Function.identity()));

        // 当前批次已入册的论文（防重复入册）
        Set<Long> existingPaperIds = defenseRecordMapper.selectList(
                        new LambdaQueryWrapper<DefenseRecord>()
                                .eq(DefenseRecord::getBatchId, batchId))
                .stream().map(DefenseRecord::getPaperId).collect(Collectors.toSet());

        int success = 0;
        List<String> skips = new ArrayList<>();
        Long operatorId = UserBusinessInfoUtils.getCurrentUserId();
        for (Long paperId : paperIds) {
            PaperInfo paper = paperMap.get(paperId);
            if (paper == null) {
                skips.add("论文" + paperId + "：不存在或已删除");
                continue;
            }
            if (existingPaperIds.contains(paperId)) {
                skips.add("论文" + paperId + "：已在本批次中");
                continue;
            }
            String currentStatus = paper.getPaperStatus();
            // 阶段门校验：不符合前置状态的论文跳过
            if (!paperStageGate.canTransition(currentStatus, targetStatus)) {
                skips.add("论文" + paperId + "：当前状态[" + currentStatus + "]不可进入[" + targetStatus + "]");
                continue;
            }
            // 学生ID必须存在
            if (paper.getStudentId() == null) {
                skips.add("论文" + paperId + "：未关联学生，无法入册");
                continue;
            }

            try {
                DefenseRecord record = new DefenseRecord();
                record.setBatchId(batchId);
                record.setPaperId(paperId);
                record.setStudentId(paper.getStudentId());
                // 顺序号暂留空，由组长在答辩日人工编排
                defenseRecordMapper.insert(record);
                success++;
                existingPaperIds.add(paperId);

                // 阶段门推进 paper_status（与 P2 分配评阅任务推进 reviewing 同构）
                paperStageGate.assertTransition(currentStatus, targetStatus);
                PaperInfo updatePaper = new PaperInfo();
                updatePaper.setId(paperId);
                updatePaper.setPaperStatus(targetStatus);
                paperInfoMapper.updateById(updatePaper);
                paperStatusLogService.recordStatusLog(paperId, currentStatus, targetStatus,
                        "入册答辩批次[" + batch.getBatchNo() + "]，进入" + (isMid ? "中期" : "终期") + "答辩阶段",
                        operatorId, null);
            } catch (DuplicateKeyException e) {
                skips.add("论文" + paperId + "：已在本批次中（并发重复）");
            }
        }

        if (success == 0) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT,
                    "未入册任何论文：" + String.join("；", skips));
        }

        log.info("学生入批完成：batchId={}，batchType={}，成功={}，跳过={}",
                batchId, batch.getBatchType(), success, skips.size());

        return Result.success("学生入批完成", Map.of(
                "total", paperIds.size(),
                "success", success,
                "skipped", skips.size(),
                "skipReasons", skips));
    }

    // ==================== 批次内记录列表 ====================

    @Override
    public Result<Page<DefenseRecordVO>> listBatchRecords(Long batchId, Long studentId, String result,
                                                          Integer currentPage, Integer pageSize) {
        mustGetBatch(batchId);
        int page = currentPage == null || currentPage < 1 ? 1 : currentPage;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<DefenseRecord> wrapper = new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getBatchId, batchId)
                .orderByAsc(DefenseRecord::getDefenseOrder)
                .orderByAsc(DefenseRecord::getCreateTime);
        if (studentId != null) {
            wrapper.eq(DefenseRecord::getStudentId, studentId);
        }
        if (StringUtils.hasText(result)) {
            wrapper.eq(DefenseRecord::getResult, result);
        }

        Page<DefenseRecord> resultPage = defenseRecordMapper.selectPage(new Page<>(page, size), wrapper);
        Page<DefenseRecordVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(enrichRecordList(resultPage.getRecords(), batchId));
        return Result.success("答辩记录列表查询成功", voPage);
    }

    @Override
    public List<DefenseRecordExcelVO> buildExportRecords(Long batchId) {
        mustGetBatch(batchId);
        // 与列表同一排序口径：顺序号升序，其次按创建时间；不分页，导出全量
        List<DefenseRecord> records = defenseRecordMapper.selectList(
                new LambdaQueryWrapper<DefenseRecord>()
                        .eq(DefenseRecord::getBatchId, batchId)
                        .orderByAsc(DefenseRecord::getDefenseOrder)
                        .orderByAsc(DefenseRecord::getCreateTime));
        if (CollectionUtils.isEmpty(records)) {
            return List.of();
        }
        List<DefenseRecordVO> vos = enrichRecordList(records, batchId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return vos.stream().map(vo -> {
            DefenseRecordExcelVO row = new DefenseRecordExcelVO();
            row.setDefenseOrder(vo.getDefenseOrder());
            row.setPaperTitle(vo.getPaperTitle());
            row.setStudentName(vo.getStudentName());
            row.setStudentUsername(vo.getStudentUsername());
            row.setScore(vo.getScore());
            row.setGrade(vo.getGrade());
            row.setResultDesc(vo.getResultDesc());
            row.setComment(vo.getComment());
            row.setSubmitByName(vo.getSubmitByName());
            row.setSubmitTime(vo.getSubmitTime() == null ? "" : vo.getSubmitTime().format(fmt));
            return row;
        }).toList();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 加载批次组成员名单：组长（leader）在前，成员（member）在后，同角色按加入时间升序。
     * 管理端 listBatchMembers 与教师端工作台共用此查询口径。
     */
    private List<DefenseGroupMemberVO> loadBatchMembers(Long batchId) {
        List<DefenseGroupMember> members = defenseGroupMemberMapper.selectList(
                new LambdaQueryWrapper<DefenseGroupMember>()
                        .eq(DefenseGroupMember::getBatchId, batchId)
                        .orderByAsc(DefenseGroupMember::getCreateTime));
        if (CollectionUtils.isEmpty(members)) {
            return List.of();
        }

        List<Long> teacherIds = members.stream()
                .map(DefenseGroupMember::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> teacherMap = teacherIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        return members.stream()
                .sorted((a, b) -> {
                    // 组长在前；同角色按加入时间升序
                    boolean aLeader = DefenseGroupMember.ROLE_LEADER.equals(a.getGroupRole());
                    boolean bLeader = DefenseGroupMember.ROLE_LEADER.equals(b.getGroupRole());
                    if (aLeader != bLeader) {
                        return aLeader ? -1 : 1;
                    }
                    if (a.getCreateTime() != null && b.getCreateTime() != null) {
                        return a.getCreateTime().compareTo(b.getCreateTime());
                    }
                    return 0;
                })
                .map(m -> {
                    SysUser teacher = m.getTeacherId() == null ? null : teacherMap.get(m.getTeacherId());
                    DefenseGroupMemberVO vo = new DefenseGroupMemberVO();
                    vo.setId(m.getId());
                    vo.setBatchId(m.getBatchId());
                    vo.setTeacherId(m.getTeacherId());
                    vo.setTeacherName(teacher == null ? null
                            : (StringUtils.hasText(teacher.getRealName()) ? teacher.getRealName() : teacher.getUsername()));
                    vo.setTeacherUsername(teacher == null ? null : teacher.getUsername());
                    vo.setGroupRole(m.getGroupRole());
                    vo.setGroupRoleDesc(DefenseGroupMember.ROLE_LEADER.equals(m.getGroupRole()) ? "组长" : "成员");
                    vo.setCreateTime(m.getCreateTime());
                    return vo;
                }).toList();
    }

    private DefenseBatch mustGetBatch(Long id) {
        DefenseBatch batch = defenseBatchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "答辩批次不存在或已删除");
        }
        return batch;
    }

    /**
     * 列表批量组装 VO：补全学院名称、组长/秘书姓名、组成员数、入册论文数、已提交结论数。
     * 仅管理端会调用此方法（带统计）。
     */
    private List<DefenseBatchVO> enrichBatchList(List<DefenseBatch> batches) {
        if (CollectionUtils.isEmpty(batches)) {
            return List.of();
        }
        // 学院名称
        List<Long> collegeIds = batches.stream().map(DefenseBatch::getCollegeId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> collegeNameMap = collegeIds.isEmpty() ? Map.of()
                : collegeMapper.selectBatchIds(collegeIds).stream()
                        .collect(Collectors.toMap(College::getId, College::getCollegeName));

        // 组长/秘书姓名
        List<Long> userIds = batches.stream()
                .flatMap(b -> java.util.stream.Stream.of(b.getLeaderId(), b.getSecretaryId()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        // 批次统计：组成员数 / 入册论文数 / 已提交结论数（一次 group by 查询，遵循 only_full_group_by）
        List<Long> batchIds = batches.stream().map(DefenseBatch::getId).toList();
        Map<Long, long[]> stats = batchStats(batchIds);

        return batches.stream().map(b -> {
            DefenseBatchVO vo = toVO(b, false);
            vo.setCollegeName(b.getCollegeId() == null ? null : collegeNameMap.get(b.getCollegeId()));
            vo.setLeaderName(b.getLeaderId() == null ? null : userNameMap.get(b.getLeaderId()));
            vo.setSecretaryName(b.getSecretaryId() == null ? null : userNameMap.get(b.getSecretaryId()));
            long[] s = stats.getOrDefault(b.getId(), new long[3]);
            vo.setMemberCount((int) s[0]);
            vo.setPaperCount((int) s[1]);
            vo.setSubmittedCount((int) s[2]);
            return vo;
        }).toList();
    }

    /**
     * 单批次组装 VO（详情或创建回显）。
     *
     * @param withStats true 时附带组成员数/入册论文数/已提交结论数
     */
    private DefenseBatchVO toVO(DefenseBatch batch, boolean withStats) {
        DefenseBatchVO vo = new DefenseBatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setBatchType(batch.getBatchType());
        vo.setBatchTypeDesc(batchTypeDesc(batch.getBatchType()));
        vo.setCollegeId(batch.getCollegeId());
        vo.setDefenseDate(batch.getDefenseDate());
        vo.setLocation(batch.getLocation());
        vo.setMode(batch.getMode());
        vo.setModeDesc(DefenseBatch.MODE_ONLINE.equals(batch.getMode()) ? "线上答辩" : "线下答辩");
        vo.setLeaderId(batch.getLeaderId());
        vo.setSecretaryId(batch.getSecretaryId());
        vo.setStatus(batch.getStatus());
        vo.setStatusDesc(batchStatusDesc(batch.getStatus()));
        vo.setRemark(batch.getRemark());
        vo.setCreateTime(batch.getCreateTime());

        // 详情或创建回显时补组长/秘书姓名（创建时 batch.id 已落库，可直接查）
        if (withStats || batch.getLeaderId() != null) {
            List<Long> userIds = java.util.stream.Stream.of(batch.getLeaderId(), batch.getSecretaryId())
                    .filter(Objects::nonNull).toList();
            if (!userIds.isEmpty()) {
                Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
                vo.setLeaderName(batch.getLeaderId() == null ? null : userNameMap.get(batch.getLeaderId()));
                vo.setSecretaryName(batch.getSecretaryId() == null ? null : userNameMap.get(batch.getSecretaryId()));
            }
            if (batch.getCollegeId() != null) {
                College college = collegeMapper.selectById(batch.getCollegeId());
                vo.setCollegeName(college == null ? null : college.getCollegeName());
            }
        }
        if (withStats) {
            Map<Long, long[]> stats = batchStats(List.of(batch.getId()));
            long[] s = stats.getOrDefault(batch.getId(), new long[3]);
            vo.setMemberCount((int) s[0]);
            vo.setPaperCount((int) s[1]);
            vo.setSubmittedCount((int) s[2]);
        }
        return vo;
    }

    /**
     * 批次统计：[组成员数, 入册论文数, 已提交结论数]。
     * 使用 count 查询而非 group by（避免 only_full_group_by 约束），按批次逐个统计。
     * 批次管理列表页典型页大小 10，10 次 count 查询可接受；若后续需要优化可改 XML group by。
     */
    private Map<Long, long[]> batchStats(List<Long> batchIds) {
        if (CollectionUtils.isEmpty(batchIds)) {
            return Map.of();
        }
        Map<Long, long[]> result = new java.util.HashMap<>();
        for (Long bid : batchIds) {
            long memberCount = defenseGroupMemberMapper.selectCount(
                    new LambdaQueryWrapper<DefenseGroupMember>().eq(DefenseGroupMember::getBatchId, bid));
            long paperCount = defenseRecordMapper.selectCount(
                    new LambdaQueryWrapper<DefenseRecord>().eq(DefenseRecord::getBatchId, bid));
            long submittedCount = defenseRecordMapper.selectCount(
                    new LambdaQueryWrapper<DefenseRecord>()
                            .eq(DefenseRecord::getBatchId, bid)
                            .isNotNull(DefenseRecord::getResult));
            result.put(bid, new long[]{memberCount, paperCount, submittedCount});
        }
        return result;
    }

    private List<DefenseRecordVO> enrichRecordList(List<DefenseRecord> records, Long batchId) {
        if (CollectionUtils.isEmpty(records)) {
            return List.of();
        }
        DefenseBatch batch = defenseBatchMapper.selectById(batchId);
        String batchNo = batch == null ? null : batch.getBatchNo();

        List<Long> paperIds = records.stream().map(DefenseRecord::getPaperId).distinct().toList();
        Map<Long, PaperInfo> paperMap = paperIds.isEmpty() ? Map.of()
                : paperInfoMapper.selectBatchIds(paperIds).stream()
                        .collect(Collectors.toMap(PaperInfo::getId, Function.identity()));

        List<Long> studentIds = records.stream().map(DefenseRecord::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> studentMap = studentIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<Long> submitByIds = records.stream().map(DefenseRecord::getSubmitBy)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> submitByNameMap = submitByIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(submitByIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        return records.stream().map(r -> {
            PaperInfo paper = paperMap.get(r.getPaperId());
            SysUser student = r.getStudentId() == null ? null : studentMap.get(r.getStudentId());
            DefenseRecordVO vo = new DefenseRecordVO();
            vo.setId(r.getId());
            vo.setBatchId(r.getBatchId());
            vo.setBatchNo(batchNo);
            vo.setPaperId(r.getPaperId());
            vo.setPaperTitle(paper == null ? null : paper.getPaperTitle());
            vo.setStudentId(r.getStudentId());
            vo.setStudentName(student == null ? null
                    : (StringUtils.hasText(student.getRealName()) ? student.getRealName() : student.getUsername()));
            vo.setStudentUsername(student == null ? null : student.getUsername());
            vo.setDefenseOrder(r.getDefenseOrder());
            vo.setScore(r.getScore());
            vo.setGrade(r.getGrade());
            vo.setResult(r.getResult());
            vo.setResultDesc(resultDesc(r.getResult()));
            // 列表展示用已清洗的 comment 原文
            vo.setComment(r.getComment());
            vo.setAttachmentId(r.getAttachmentId());
            vo.setSubmitBy(r.getSubmitBy());
            vo.setSubmitByName(r.getSubmitBy() == null ? null : submitByNameMap.get(r.getSubmitBy()));
            vo.setSubmitTime(r.getSubmitTime());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
    }

    private String batchTypeDesc(String batchType) {
        if (batchType == null) return null;
        return switch (batchType) {
            case DefenseBatch.TYPE_MID_TERM -> "中期答辩";
            case DefenseBatch.TYPE_FINAL -> "终期答辩";
            default -> batchType;
        };
    }

    private String batchStatusDesc(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case DefenseBatch.STATUS_PREPARING -> "筹备中";
            case DefenseBatch.STATUS_IN_PROGRESS -> "进行中";
            case DefenseBatch.STATUS_FINISHED -> "已结束";
            default -> String.valueOf(status);
        };
    }

    private String resultDesc(String result) {
        if (result == null) return null;
        return switch (result) {
            case DefenseRecord.RESULT_PASS -> "通过";
            case DefenseRecord.RESULT_FAIL -> "不通过";
            case DefenseRecord.RESULT_POSTPONE -> "缓辩";
            default -> result;
        };
    }
}
