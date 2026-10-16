package com.abin.checkrepeatservice.student.event;

import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import lombok.Getter;

import java.util.List;

/**
 * 增强查重任务执行事件（架构修复 P0）。
 *
 * <p>替代 EnhancedCheckTaskServiceImpl 内部对本类 {@code @Async} 方法的直接调用
 * （Spring AOP 自调用导致 @Async 失效，任务实际在创建/重试事务内同步执行）。
 * 事务提交后由独立监听器在 checkTaskExecutor 线程池异步执行。</p>
 */
@Getter
public class EnhancedCheckTaskExecuteEvent {

    private final Long taskId;

    private final List<CheckEngineTypeEnum> engineTypes;

    public EnhancedCheckTaskExecuteEvent(Long taskId, List<CheckEngineTypeEnum> engineTypes) {
        this.taskId = taskId;
        this.engineTypes = engineTypes;
    }
}
