package com.abin.checkrepeatservice.student.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 论文提交完成事件（架构修复 P0）。
 *
 * <p>背景：原提交事务在同类中直接调用本类的 {@code @Async asyncAllocateTeacherAndCheck}，
 * Spring AOP 自调用不经过代理，@Async 完全失效——导师分配 + Tika 解析 + 全库查重实际
 * 同步跑在学生「提交论文」的 HTTP 请求与数据库事务内，前端超时且长占连接池连接。</p>
 *
 * <p>现改为：提交事务提交成功后（AFTER_COMMIT）由独立监听器 bean 异步执行分配与查重，
 * 既保证调用经过代理真正异步，也保证回滚时不会触发后续流程。</p>
 */
@Getter
public class PaperSubmittedEvent extends ApplicationEvent {

    private final Long paperId;

    private final Long studentId;

    public PaperSubmittedEvent(Object source, Long paperId, Long studentId) {
        super(source);
        this.paperId = paperId;
        this.studentId = studentId;
    }
}
