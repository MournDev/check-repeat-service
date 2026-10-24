package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.CollegeCompareVO;
import com.abin.checkrepeatservice.admin.dto.MonthlyTrendVO;
import com.abin.checkrepeatservice.admin.dto.SimilarityDistributionVO;
import com.abin.checkrepeatservice.admin.mapper.AdminStatReportMapper;
import com.abin.checkrepeatservice.admin.service.AdminStatReportService;
import com.abin.checkrepeatservice.admin.vo.CollegeCompareExcelVO;
import com.abin.checkrepeatservice.admin.vo.MonthlyTrendExcelVO;
import com.abin.checkrepeatservice.admin.vo.SimilarityDistributionExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.datascope.DataScopeResolver;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端全校统计报表服务实现（论文全生命周期 P5）。
 *
 * 关键口径：
 *  - 分布：check_result（is_deleted=0，repeat_rate 非空）十分桶，
 *    派生子表先打桶再 GROUP BY（兼容 only_full_group_by），Java 侧补齐缺失桶；
 *  - 学院对比：paper_info（is_deleted=0，similarity_rate 非空）按 college_id 聚合，
 *    college_id 为 NULL 归"未分配"；paper_info 无 college_name 冗余列，SQL 中 LEFT JOIN college；
 *  - 月度趋势：check_result（is_deleted=0，check_time 非空）按月聚合，Java 侧取最近 12 个月并补缺失月。
 */
@Slf4j
@Service
public class AdminStatReportServiceImpl implements AdminStatReportService {

    /**
     * 重复率十桶（左闭右开，末桶含 100），与 AdminStatReportMapper.xml 的 CASE 顺序一一对应
     */
    private static final List<String> RATE_BUCKETS = List.of(
            "0-10", "10-20", "20-30", "30-40", "40-50",
            "50-60", "60-70", "70-80", "80-90", "90-100");

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Resource
    private AdminStatReportMapper adminStatReportMapper;

    @Resource
    private DataScopeResolver dataScopeResolver;

    @Override
    public Result<List<SimilarityDistributionVO>> getSimilarityDistribution() {
        Map<String, Long> bucketCountMap = adminStatReportMapper
                .selectSimilarityDistribution(dataScopeResolver.currentFilterOrNull()).stream()
                .collect(Collectors.toMap(SimilarityDistributionVO::getBucket,
                        SimilarityDistributionVO::getCount, (a, b) -> a));

        // Java 侧补全缺失桶为 0，保证十桶完整且有序
        List<SimilarityDistributionVO> result = new ArrayList<>();
        for (String bucket : RATE_BUCKETS) {
            SimilarityDistributionVO vo = new SimilarityDistributionVO();
            vo.setBucket(bucket);
            vo.setCount(bucketCountMap.getOrDefault(bucket, 0L));
            result.add(vo);
        }
        return Result.success(result);
    }

    @Override
    public Result<List<CollegeCompareVO>> getCollegeCompare() {
        List<CollegeCompareVO> list = adminStatReportMapper
                .selectCollegeCompare(dataScopeResolver.currentFilterOrNull());
        for (CollegeCompareVO vo : list) {
            if (vo.getCollegeId() == null) {
                vo.setCollegeName("未分配");
            } else if (vo.getCollegeName() == null || vo.getCollegeName().isBlank()) {
                // college_id 有值但 college 记录缺失/被删
                vo.setCollegeName("未知学院");
            }
        }
        return Result.success(list);
    }

    @Override
    public Result<List<MonthlyTrendVO>> getMonthlyTrend() {
        Map<String, MonthlyTrendVO> ymMap = adminStatReportMapper
                .selectMonthlyTrend(dataScopeResolver.currentFilterOrNull()).stream()
                .collect(Collectors.toMap(MonthlyTrendVO::getYm, Function.identity(), (a, b) -> a));

        // 取最近 12 个月（含当月），缺失月补 0，保证趋势连续
        YearMonth current = YearMonth.now();
        List<MonthlyTrendVO> result = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            String ym = current.minusMonths(i).format(MONTH_FORMATTER);
            MonthlyTrendVO db = ymMap.get(ym);
            if (db != null) {
                result.add(db);
            } else {
                MonthlyTrendVO vo = new MonthlyTrendVO();
                vo.setYm(ym);
                vo.setCount(0L);
                vo.setAvgRate(BigDecimal.ZERO);
                result.add(vo);
            }
        }
        return Result.success(result);
    }

    @Override
    public void exportStatReport(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "全校查重统计报表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
            exportStatReportToStream(response.getOutputStream());
        } catch (Exception e) {
            log.error("导出全校查重统计报表失败", e);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    @Override
    public int exportStatReportToStream(java.io.OutputStream outputStream) {
        // 三个 sheet 的行数据（复用三个查询接口的组装逻辑）
        List<SimilarityDistributionExcelVO> distRows = getSimilarityDistribution().getData().stream()
                .map(vo -> {
                    SimilarityDistributionExcelVO row = new SimilarityDistributionExcelVO();
                    row.setBucket(vo.getBucket());
                    row.setCount(vo.getCount());
                    return row;
                })
                .collect(Collectors.toList());

        List<CollegeCompareExcelVO> collegeRows = getCollegeCompare().getData().stream()
                .map(vo -> {
                    CollegeCompareExcelVO row = new CollegeCompareExcelVO();
                    row.setCollegeName(vo.getCollegeName());
                    row.setAvgRate(vo.getAvgRate());
                    row.setTotal(vo.getTotal());
                    return row;
                })
                .collect(Collectors.toList());

        List<MonthlyTrendExcelVO> trendRows = getMonthlyTrend().getData().stream()
                .map(vo -> {
                    MonthlyTrendExcelVO row = new MonthlyTrendExcelVO();
                    row.setYm(vo.getYm());
                    row.setCount(vo.getCount());
                    row.setAvgRate(vo.getAvgRate());
                    return row;
                })
                .collect(Collectors.toList());

        try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {
            WriteSheet distSheet = EasyExcel.writerSheet(0, "查重结果分布")
                    .head(SimilarityDistributionExcelVO.class).build();
            excelWriter.write(distRows, distSheet);
            WriteSheet collegeSheet = EasyExcel.writerSheet(1, "学院对比")
                    .head(CollegeCompareExcelVO.class).build();
            excelWriter.write(collegeRows, collegeSheet);
            WriteSheet trendSheet = EasyExcel.writerSheet(2, "月度趋势")
                    .head(MonthlyTrendExcelVO.class).build();
            excelWriter.write(trendRows, trendSheet);
        }
        log.info("导出台账统计报表成功: 分布={}行, 学院对比={}行, 月度趋势={}行",
                distRows.size(), collegeRows.size(), trendRows.size());
        return distRows.size() + collegeRows.size() + trendRows.size();
    }
}
