package com.abin.checkrepeatservice.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理端导师评价实名分页查询请求（论文全生命周期 P6a）。
 * 全部筛选条件可空：teacherId 按导师、collegeId 按学院（经 paper_info 关联）、
 * minOverall/maxOverall 按总体满意度分数段过滤、beginDate/endDate 按评价提交时间
 * 过滤（P6b 学年/届别归口：跨届评价混算会失真，排行/完成率可按时间段切分）。
 */
@Data
public class AdvisorEvalPageReq {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须≥1")
    private Integer pageNum;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须≥1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize;

    /**
     * 导师ID（可空）
     */
    private Long teacherId;

    /**
     * 学院ID（可空，按论文 college_id 过滤）
     */
    private Long collegeId;

    /**
     * 总体满意度下限（1-5，可空）
     */
    @Min(value = 1, message = "评分下限范围为1-5")
    @Max(value = 5, message = "评分上限范围为1-5")
    private Integer minOverall;

    /**
     * 总体满意度上限（1-5，可空）
     */
    @Min(value = 1, message = "评分下限范围为1-5")
    @Max(value = 5, message = "评分上限范围为1-5")
    private Integer maxOverall;

    /**
     * 评价提交时间下限（含当天，可空；GET 查询参数 yyyy-MM-dd）
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    /**
     * 评价提交时间上限（含当天，可空；GET 查询参数 yyyy-MM-dd）
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
