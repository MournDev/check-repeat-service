package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查重报告实体：对应check_report表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_report") // 绑定数据库表名
public class CheckReport extends BaseEntity {
    /**
     * 对应的查重任务ID（关联check_task.id，唯一）
     */
    private Long taskId;

    /**
     * 报告编号（唯一，如：REPORT20240512001）
     */
    private String reportNo;

    /**
     * 重复详情（JSON格式字符串，含重复段落、相似来源、相似度）
     */
    private String repeatDetails;

    /**
     * 报告文件存储路径
     */
    private String reportPath;

    /**
     * 报告文件类型（默认pdf）
     */
    private String reportType;
}
