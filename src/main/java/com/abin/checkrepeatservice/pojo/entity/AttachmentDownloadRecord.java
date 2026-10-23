package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 附件下载记录（共享文件下载次数统计）。
 *
 * <p>每成功下载一次写一条记录，create_time 即下载时间；
 * 列表按 attachment_id 聚合计数。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attachment_download_record")
public class AttachmentDownloadRecord extends BaseEntity {

    /**
     * 附件ID（file_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("attachment_id")
    private Long attachmentId;

    /**
     * 下载人ID（sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("user_id")
    private Long userId;
}
