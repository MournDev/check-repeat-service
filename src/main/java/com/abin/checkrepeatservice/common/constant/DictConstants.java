package com.abin.checkrepeatservice.common.constant;

/**
 * 字典值常量类
 */
public class DictConstants {
    /**
     * 论文状态字典类型
     */
    public static final String DICT_PAPER_STATUS = "paper_status";

    /**
     * 查重状态字典类型
     */
    public static final String DICT_CHECK_STATUS = "check_status";

    /**
     * 论文类型字典类型。
     *
     * 注意这是个"名义字典"：DictService 遇到它会直接由 PaperTypeEnum 合成返回，
     * 不查 sys_dict_data（那批行本仓库没有种子数据，详见 DictService.getDictDataByType）。
     * 保留这个 key 是因为前端 /api/dict/data/type/paper_type 仍在用，
     * 且 DictService 的两处特判需要它。
     */
    public static final String DICT_PAPER_TYPE = "paper_type";


    /**
     * 查重任务状态字典类型
     */
    public static final String DICT_TASK_STATUS = "task_status";

    /**
     * 分配类型字典类型
     */
    public static final String DICT_ALLOCATION_TYPE = "allocation_type";

    /**
     * 分配类型字典类型
     */
    public static final String DICT_ALLOCATION_STATUS = "allocation_status";

    /**
     * 消息类型字典类型
     */
    public static final String DICT_MESSAGE_TYPE = "message_type";

    /**
     * 导师评价标签字典类型（论文全生命周期 P6a：学生对导师的预设标签白名单）
     */
    public static final String DICT_ADVISOR_EVAL_TAG = "advisor_eval_tag";

    /**
     * 消息优先级类型
     */
    public static final String MESSAGE_PRIORITY = "message_priority";

    /**
     * 关联业务类型
     */
    public static final String RELATED_TYPE = "related_type";

    /**
     * 模板类型类型
     */
    public static final String TEMPLATE_TYPE = "template_type";


    /**
     * 论文状态字典值。
     *
     * P1（2026-10）起状态机扩展为「论文全生命周期阶段门」模型：
     * pending → assigned → checking → auditing(初审) → completed(初审通过)
     *   → reviewing(评阅,P2启用) → mid_defense(中期答辩,P3启用) → final_defense(终期答辩,P3启用)
     *   → defense_passed → finalized(定稿) → archived(归档,P4启用)
     * 任一阶段门不通过 → rejected → 退回修改重新提交（重走当前阶段门）。
     * 阶段门流转校验统一走 common/component/PaperStageGate，禁止散落各处自行判断。
     */
    public static class PaperStatus {

        public static final String PENDING = "pending";// 待分配
        public static final String ASSIGNED = "assigned";// 已分配
        public static final String CHECKING = "checking";// 待查重
        public static final String AUDITING = "auditing";// 待审核（导师初审）
        public static final String COMPLETED = "completed";// 审核通过（语义收窄为初审通过）
        public static final String REJECTED = "rejected";// 审核不通过
        public static final String WITHDRAWN = "withdrawn";// 已取消

        // ==================== P1 新增阶段门状态 ====================

        /** 评阅中：初审通过，评阅任务已下发（P2 评阅任务启用） */
        public static final String REVIEWING = "reviewing";
        /** 中期答辩：中期批次已安排（P3 答辩管理启用） */
        public static final String MID_DEFENSE = "mid_defense";
        /** 终期答辩：终期批次已安排（P3 答辩管理启用） */
        public static final String FINAL_DEFENSE = "final_defense";
        /** 答辩通过：答辩组结论为通过（P3 起写入） */
        public static final String DEFENSE_PASSED = "defense_passed";
        /** 定稿：指导教师定稿确认（P4 起写入） */
        public static final String FINALIZED = "finalized";
        /** 已归档：材料包归档完成，已生成归档编号（P4 起写入） */
        public static final String ARCHIVED = "archived";
    }

    // 论文类型的取值域不在这里：见 common/enums/PaperTypeEnum。
    // 原先这里有个 PaperType 嵌套类（graduation/course/other），全项目零引用，
    // 留着就是一份会和枚举漂移的第二值域 —— 已删除。

    /**
     * 查重状态字典值
     */
    public static class CheckStatus {
        public static final String PENDING = "pending";// 待查重
        public static final String CHECKING = "checking";// 查重中
        public static final String CANCELLED = "cancelled";// 已取消
        public static final String COMPLETED = "completed";// 查重完成
        public static final String FAILURE = "failure";// 查重失败
    }

    /**
     * 查重任务状态字典值
     */
    public static class TaskStatus {
        public static final String PENDING = "pending";// 待处理
        public static final String PROCESSING = "processing";// 处理中
        public static final String COMPLETED = "completed";// 处理完成
        public static final String FAILURE = "failure";// 处理失败
    }

    /**
     * 分配类型字典值
     */
    public static class AllocationType {
        public static final String AUTO = "auto";// 自动分配
        public static final String MANUAL = "manual";// 手动分配
    }

    /**
     * 分配状态字典值
     */
    public static class AllocationStatus {
        public static final String PENDING = "pending";// 待确认
        public static final String CONFIRMED = "confirmed";// 已确认
        public static final String REJECTED = "rejected";// 已拒绝
    }

    /**
     * 消息类型字典值
     */
    public static class MessageType {
        public static final String ASSIGNMENT = "assignment"; // 分配通知
        public static final String CONFIRM = "confirm";       // 确认通知
        public static final String REJECT = "reject";         // 拒绝通知
        public static final String REMINDER = "reminder";     // 提醒通知
    }

    // 消息优先级
    public static class MessagePriority {
        public static final Integer NORMAL = 1;    // 普通
        public static final Integer IMPORTANT = 2; // 重要
        public static final Integer URGENT = 3;    // 紧急
    }

    // 关联业务类型
    public static class RelatedType {
        public static final String PAPER = "paper";// 论文
        public static final String TASK = "task";// 查重任务
        public static final String SYSTEM = "system";
    }

    // 模板类型
    public static class TemplateType {
        public static final String ASSIGNMENT = "assignment";   // 分配模板
        public static final String CONFIRM = "confirm";         // 确认模板
        public static final String REJECT = "reject";       // 拒绝模板
    }
}
