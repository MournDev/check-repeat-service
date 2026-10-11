package com.abin.checkrepeatservice.common.component;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.abin.checkrepeatservice.common.constant.DictConstants.PaperStatus.*;

/**
 * 论文全生命周期阶段门（P1，2026-10）。
 *
 * 职责：论文状态（DictConstants.PaperStatus）流转合法性的唯一裁决点。
 * 从 P2 评阅、P3 答辩、P4 归档起，所有推进论文状态的业务动作在落库前
 * 必须先过本组件校验，禁止各 Service 自行 if/else 判断状态流转。
 *
 * 主链（阶段门模型）：
 *   pending → assigned → checking → auditing(初审) → completed(初审通过)
 *     → reviewing(评阅) → mid_defense(中期答辩) → final_defense(终期答辩)
 *     → defense_passed → finalized(定稿) → archived(归档)
 * 任一阶段门不通过 → rejected → 退回修改（重新提交回到 pending，重走当前阶段门）。
 *
 * 已有流程在 P1 保持原状（不强制接入，避免回归），P2 起新流程必须接入；
 * 后续重构存量流程时逐个替换为走本组件。
 */
@Component
public class PaperStageGate {

    /**
     * 合法流转表：from → 允许的 to 集合。
     * 维护约定：改这里之前先对照 docs/论文全生命周期落地方案.md 的状态机设计，
     * 并同步 DictConstants.PaperStatus 与 db/补论文全生命周期P1.sql 的字典行。
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.ofEntries(
            // 分配链（存量流程）
            Map.entry(PENDING, Set.of(ASSIGNED, WITHDRAWN)),
            Map.entry(ASSIGNED, Set.of(CHECKING, PENDING, WITHDRAWN)),
            Map.entry(CHECKING, Set.of(AUDITING, ASSIGNED, WITHDRAWN)),
            // 初审阶段门（存量流程）
            Map.entry(AUDITING, Set.of(COMPLETED, REJECTED, WITHDRAWN)),
            // 初审通过 → 进入评阅阶段门（P2 启用）；重新发起审核允许回到待审核
            Map.entry(COMPLETED, Set.of(REVIEWING, AUDITING, WITHDRAWN)),
            // 评阅阶段门（P2 启用）：通过进中期答辩，不通过退回
            Map.entry(REVIEWING, Set.of(MID_DEFENSE, REJECTED, WITHDRAWN)),
            // 中期答辩阶段门（P3 启用）
            Map.entry(MID_DEFENSE, Set.of(FINAL_DEFENSE, REJECTED, WITHDRAWN)),
            // 终期答辩阶段门（P3 启用）
            Map.entry(FINAL_DEFENSE, Set.of(DEFENSE_PASSED, REJECTED, WITHDRAWN)),
            // 定稿与归档（P3/P4 启用）
            Map.entry(DEFENSE_PASSED, Set.of(FINALIZED)),
            Map.entry(FINALIZED, Set.of(ARCHIVED)),
            // 退回修改：重新提交回到待分配（与存量 reSubmitPaper 行为一致）；
            // 教师也可对被驳回论文重新发起审核（存量 reReview 行为）
            Map.entry(REJECTED, Set.of(PENDING, AUDITING, WITHDRAWN)),
            // 终态
            Map.entry(WITHDRAWN, Set.of()),
            Map.entry(ARCHIVED, Set.of()));

    /**
     * 存量库里的历史数字状态码（早期代码以数字字符串写 paper_status，
     * 语义见 StudentReviewController 注释：2-待审核 3-通过 4-不通过）。
     * 时间线等读路径遇到这类值时先归一化，再查标签。仅收录有依据的映射，
     * 不认识的值原样返回，避免拍脑袋翻译错阶段。
     */
    private static final Map<String, String> LEGACY_NUMERIC_STATUS = Map.of(
            "0", PENDING,
            "1", PENDING,
            "2", AUDITING,
            "3", COMPLETED,
            "4", REJECTED);

    /**
     * 校验状态流转合法性，不合法直接抛 40305（该状态下不可操作）。
     *
     * @param from 当前状态（DictConstants.PaperStatus 字典值）
     * @param to   目标状态
     */
    public void assertTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS,
                    String.format("论文状态不允许从 [%s] 流转到 [%s]", from, to));
        }
    }

    /**
     * 判断状态流转是否合法（null 或未知状态一律视为不合法，保守拒绝）。
     */
    public boolean canTransition(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return false;
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 查询某状态的下一跳合法状态集合（未知状态返回空集）。
     */
    public Set<String> nextStages(String from) {
        Set<String> allowed = from == null ? null : ALLOWED_TRANSITIONS.get(from);
        return allowed == null ? Set.of() : allowed;
    }

    /**
     * 是否终态（已取消 / 已归档）。
     */
    public boolean isTerminal(String status) {
        return WITHDRAWN.equals(status) || ARCHIVED.equals(status);
    }

    /**
     * 历史数字状态码归一化为字典值；非数字或未知值原样返回。
     */
    public String normalizeLegacy(String status) {
        if (status == null || status.isBlank()) {
            return status;
        }
        return LEGACY_NUMERIC_STATUS.getOrDefault(status.trim(), status.trim());
    }

    /**
     * 状态是否属于 P1 扩展后的合法值域（含历史数字码归一化后判定）。
     */
    public boolean isKnownStatus(String status) {
        String s = normalizeLegacy(status);
        return ALLOWED_TRANSITIONS.containsKey(s);
    }
}
