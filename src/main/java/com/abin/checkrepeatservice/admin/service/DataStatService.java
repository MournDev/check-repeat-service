package com.abin.checkrepeatservice.admin.service;


import com.abin.checkrepeatservice.admin.dto.AuditEfficiencyStatDTO;
import com.abin.checkrepeatservice.admin.dto.CheckResultStatDTO;
import com.abin.checkrepeatservice.admin.dto.SubmitTrendStatDTO;
import com.abin.checkrepeatservice.admin.vo.StatQueryReq;
import com.abin.checkrepeatservice.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 数据统计服务接口
 */
public interface DataStatService {

    /**
     * 1. 统计学生论文提交趋势
     * @param queryReq 筛选条件（统计维度、时间范围、专业/年级）
     * @return 提交趋势统计结果
     */
    Result<SubmitTrendStatDTO> statSubmitTrend(StatQueryReq queryReq);

    /**
     * 2. 分析查重结果（按专业或年级分组）
     * @param queryReq 筛选条件
     * @param groupType 分组类型（MAJOR-按专业，GRADE-按年级）
     * @return 查重结果分析
     */
    Result<CheckResultStatDTO> statCheckResult(StatQueryReq queryReq,
                                               @RequestParam("groupType") String groupType);

    /**
     * 3. 统计教师审核效率
     * @param queryReq 筛选条件（可指定教师ID单独统计）
     * @return 审核效率统计结果
     */
    Result<AuditEfficiencyStatDTO> statAuditEfficiency(StatQueryReq queryReq);

    /**
     * 4. 导出统计结果到Excel
     * @param queryReq 筛选条件
     * @param statType 统计类型（SUBMIT-提交趋势，CHECK-查重结果，AUDIT-审核效率）
     * @param response Http响应（用于下载Excel）
     */
    void exportStatResult(StatQueryReq queryReq,
                          @RequestParam("statType") String statType,
                          HttpServletResponse response);
}
