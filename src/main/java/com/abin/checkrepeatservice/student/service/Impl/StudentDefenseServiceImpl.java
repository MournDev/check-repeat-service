package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.DefenseBatchMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseRecordMapper;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.DefenseBatch;
import com.abin.checkrepeatservice.pojo.entity.DefenseRecord;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.student.service.StudentDefenseService;
import com.abin.checkrepeatservice.student.vo.StudentDefenseVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生端答辩服务实现（论文全生命周期 P3）。
 *
 * 学生只看到本人被安排的答辩批次与个人记录，按答辩时间倒序返回。
 */
@Slf4j
@Service
public class StudentDefenseServiceImpl implements StudentDefenseService {

    @Resource
    private DefenseBatchMapper defenseBatchMapper;

    @Resource
    private DefenseRecordMapper defenseRecordMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Override
    public Result<List<StudentDefenseVO>> listMyDefense(String batchType) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();

        // 我的答辩记录（按学生ID查 defense_record）
        LambdaQueryWrapper<DefenseRecord> wrapper = new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getStudentId, studentId)
                .orderByDesc(DefenseRecord::getCreateTime);
        // 注意：batchType 在 defense_batch 上不在 defense_record 上，需要取出后过滤
        List<DefenseRecord> records = defenseRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return Result.success("我的答辩安排查询成功", List.of());
        }

        List<Long> batchIds = records.stream().map(DefenseRecord::getBatchId).distinct().toList();
        Map<Long, DefenseBatch> batchMap = defenseBatchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(DefenseBatch::getId, Function.identity()));

        // 按 batchType 过滤
        if (StringUtils.hasText(batchType)) {
            records = records.stream()
                    .filter(r -> {
                        DefenseBatch b = batchMap.get(r.getBatchId());
                        return b != null && batchType.equals(b.getBatchType());
                    }).toList();
            if (records.isEmpty()) {
                return Result.success("我的答辩安排查询成功", List.of());
            }
        }

        // 组长/秘书姓名
        List<Long> userIds = batchMap.values().stream()
                .flatMap(b -> java.util.stream.Stream.of(b.getLeaderId(), b.getSecretaryId()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        // 按答辩时间倒序
        List<StudentDefenseVO> voList = records.stream()
                .sorted((a, b) -> {
                    DefenseBatch ba = batchMap.get(a.getBatchId());
                    DefenseBatch bb = batchMap.get(b.getBatchId());
                    if (ba == null || ba.getDefenseDate() == null) return 1;
                    if (bb == null || bb.getDefenseDate() == null) return -1;
                    return bb.getDefenseDate().compareTo(ba.getDefenseDate());
                })
                .map(r -> {
                    DefenseBatch batch = batchMap.get(r.getBatchId());
                    StudentDefenseVO vo = new StudentDefenseVO();
                    vo.setBatchId(batch == null ? null : batch.getId());
                    vo.setBatchNo(batch == null ? null : batch.getBatchNo());
                    vo.setBatchType(batch == null ? null : batch.getBatchType());
                    vo.setBatchTypeDesc(batch == null ? null : batchTypeDesc(batch.getBatchType()));
                    vo.setDefenseDate(batch == null ? null : batch.getDefenseDate());
                    vo.setLocation(batch == null ? null : batch.getLocation());
                    vo.setMode(batch == null ? null : batch.getMode());
                    vo.setModeDesc(batch == null ? null
                            : (DefenseBatch.MODE_ONLINE.equals(batch.getMode()) ? "线上答辩" : "线下答辩"));
                    vo.setLeaderName(batch == null || batch.getLeaderId() == null ? null
                            : userNameMap.get(batch.getLeaderId()));
                    vo.setSecretaryName(batch == null || batch.getSecretaryId() == null ? null
                            : userNameMap.get(batch.getSecretaryId()));
                    vo.setRecordId(r.getId());
                    vo.setDefenseOrder(r.getDefenseOrder());
                    vo.setScore(r.getScore());
                    vo.setGrade(r.getGrade());
                    vo.setResult(r.getResult());
                    vo.setResultDesc(resultDesc(r.getResult()));
                    vo.setComment(r.getComment());
                    vo.setSubmitTime(r.getSubmitTime());
                    return vo;
                }).toList();

        return Result.success("我的答辩安排查询成功", voList);
    }

    private String batchTypeDesc(String batchType) {
        if (batchType == null) return null;
        return switch (batchType) {
            case DefenseBatch.TYPE_MID_TERM -> "中期答辩";
            case DefenseBatch.TYPE_FINAL -> "终期答辩";
            default -> batchType;
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
