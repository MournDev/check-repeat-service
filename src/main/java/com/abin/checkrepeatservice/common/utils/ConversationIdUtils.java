package com.abin.checkrepeatservice.common.utils;

/**
 * 会话 ID 生成工具。
 *
 * <p>师生之间的会话号由「两个用户 ID 排序后拼接再取哈希」得出，因此是**对称**的：
 * {@code of(a, b) == of(b, a)}。学生端和教师端各自按自己的视角（学生查 paper_info.student_id，
 * 教师查 paper_info.teacher_id）找到同一对用户，算出同一个会话号，落到同一批 messages 行上。</p>
 *
 * <p>这个算法原先只作为私有方法存在于 StudentMessageServiceImpl 里。教师端要复用同一套规则，
 * 复制一份的话两边一旦改动就会各算各的、消息被劈成两个会话，所以收口到这里，
 * 学生端改为委托调用（行为不变）。**改动此方法等于改动线上已有会话的编号，不要动。**</p>
 */
public final class ConversationIdUtils {

    private ConversationIdUtils() {
    }

    /**
     * 由两个用户 ID 生成唯一会话 ID。
     *
     * @param userId1 用户 ID 1（学生或教师）
     * @param userId2 用户 ID 2（教师或学生）
     * @return 会话 ID；两个参数顺序无关
     */
    public static Long of(Long userId1, Long userId2) {
        // 确保顺序一致：较小的 ID 在前，避免 (1,2) 和 (2,1) 生成不同的 ID
        long minId = Math.min(userId1, userId2);
        long maxId = Math.max(userId1, userId2);

        // 格式："conv_{minId}_{maxId}" 的哈希值（取绝对值确保为正）
        String key = "conv_" + minId + "_" + maxId;
        return (long) Math.abs(key.hashCode() & 0x7FFFFFFF);
    }
}
