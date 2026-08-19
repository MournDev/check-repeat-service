package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseEntity {
        /**
         * 主键ID（雪花ID，由业务代码生成，非自增）
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @TableId(type = IdType.ASSIGN_ID)
        private Long id;

        /**
         * 创建人ID（关联sys_user.id，审计字段）
         */
        @TableField(fill = FieldFill.INSERT) // 新增时自动填充
        private Long createBy;

        /**
         * 创建时间（审计字段）
         */
        @TableField(fill = FieldFill.INSERT) // 新增时自动填充
        private LocalDateTime createTime;

        /**
         * 更新人ID（关联sys_user.id，审计字段）
         */
        @TableField(fill = FieldFill.INSERT_UPDATE) // 新增/更新时自动填充
        private Long updateBy;

        /**
         * 更新时间（审计字段）
         */
        @TableField(fill = FieldFill.INSERT_UPDATE) // 新增/更新时自动填充
        private LocalDateTime updateTime;

        /**
         * 软删除标记（0-未删除，1-已删除）
         */
        @TableLogic // MyBatis-Plus自动处理软删除逻辑
        private Integer isDeleted;
}
