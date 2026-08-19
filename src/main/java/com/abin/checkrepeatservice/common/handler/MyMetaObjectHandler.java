package com.abin.checkrepeatservice.common.handler;


import com.abin.checkrepeatservice.pojo.entity.BaseEntity;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus审计字段自动填充处理器
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 新增操作自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        if (metaObject.getOriginalObject() instanceof BaseEntity) {
            // 填充创建人ID（当前登录用户ID，匿名操作填0）
            Long currentUserId = 0L;
            try {
                currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            } catch (Exception e) {
                // 系统初始化或匿名操作时默认填充管理员ID（需替换为实际管理员雪花ID）
                currentUserId = 1546278765432123458L;
            }
            strictInsertFill(metaObject, "createBy", Long.class, currentUserId);
            // 填充创建时间
            strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
            // 填充更新人ID（新增时与创建人一致）
            strictInsertFill(metaObject, "updateBy", Long.class, currentUserId);
            // 填充更新时间（新增时与创建时间一致）
            strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            // 填充软删除标记（默认未删除）
            strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
        }
    }

    /**
     * 更新操作自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.getOriginalObject() instanceof BaseEntity) {
            // 填充更新人ID
            Long currentUserId = 0L;
            try {
                currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            } catch (Exception e) {
                currentUserId = 1546278765432123458L;
            }
            strictUpdateFill(metaObject, "updateBy", Long.class, currentUserId);
            // 填充更新时间
            strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
    }
}
