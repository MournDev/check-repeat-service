package com.abin.checkrepeatservice.student.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论文版本历史列表项 DTO
 *
 * <p>与 {@link PaperVersionDTO}（版本详情）区分：本 DTO 仅承载版本历史时间轴渲染
 * 所需的最小字段，字段命名与前端 MyPapers.vue 模板直接对应
 * （version.id / version.submitVersion / version.submitTime / version.isCurrent）。
 */
@Data
public class PaperVersionItemDTO {

    /** 版本记录 ID（paper_submit 主键，查看详情/下载时用） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /** 论文 ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /** 提交版本号（1 起递增） */
    private Integer submitVersion;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 是否当前（最新）版本 */
    private Boolean isCurrent;

    /** 文件 ID */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long fileId;

    /** 提交备注（如「审核不通过重新提交；修改说明：xxx」） */
    private String remark;
}
