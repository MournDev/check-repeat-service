package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.TrendQueryDTO;
import com.abin.checkrepeatservice.teacher.dto.TrendResultDTO;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherDataAnalysisService;
import com.abin.checkrepeatservice.teacher.vo.CollegeDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.ReviewStatusVO;
import com.abin.checkrepeatservice.teacher.vo.SimilarityDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.TrendDataVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TeacherDataAnalysisImpl implements TeacherDataAnalysisService {

    @Autowired
    private ReviewRecordMapper reviewRecordMapper;

    @Autowired
    private PaperInfoMapper paperInfoMapper;

    @Override
    public TrendResultDTO getReviewTrend(TrendQueryDTO query) {
        validateTrendQuery(query);

        // 计算时间范围
        TrendQueryDTO.DateRange dateRange = query.getDateRange();
        LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

        log.info("获取审核趋势数据: teacherId={}, chartType={}, timeRange={}, start={}, end={}",
                query.getTeacherId(), query.getChartType(), query.getTimeRange(),
                startDateTime, endDateTime);

        // 根据图表类型获取数据
        List<TrendDataVO> rawData;
        List<String> labels;
        List<Integer> values;

        log.info("开始处理图表数据 - 图表类型: {}, 时间范围: {} 到 {}",
                query.getChartType(), dateRange.getStartDate(), dateRange.getEndDate());

        switch (query.getChartType()) {
            case "daily":
                rawData = reviewRecordMapper.selectDailyTrend(query.getTeacherId(),
                        startDateTime, endDateTime);
                labels = generateDailyLabels(dateRange.getStartDate(), dateRange.getEndDate());
                log.info("生成每日标签数量: {}", labels.size());
                values = processDailyData(rawData, labels);
                break;

            case "weekly":
                rawData = reviewRecordMapper.selectWeeklyTrend(query.getTeacherId(),
                        startDateTime, endDateTime);
                labels = generateWeeklyLabels(dateRange.getStartDate(), dateRange.getEndDate());
                values = processWeeklyData(rawData, labels);
                break;

            case "monthly":
                rawData = reviewRecordMapper.selectMonthlyTrend(query.getTeacherId(),
                        startDateTime, endDateTime);
                labels = generateMonthlyLabels(dateRange.getStartDate(), dateRange.getEndDate());
                values = processMonthlyData(rawData, labels);
                break;

            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的图表类型: " + query.getChartType());
        }

        // 计算趋势百分比
        BigDecimal trendPercentage = calculateTrendPercentage(query.getTeacherId(), dateRange);

        // 获取总数
        Integer total = reviewRecordMapper.selectTotalReviews(query.getTeacherId(),
                startDateTime, endDateTime);

        // 构建结果
        TrendResultDTO result = buildTrendResult(labels, values, trendPercentage, total);

        return result;
    }

    /**
     * 处理每日数据并补齐缺失日期
     */
    private List<Integer> processDailyData(List<TrendDataVO> rawData, List<String> labels) {
        log.info("处理每日数据 - 原始数据条数: {}, 标签数量: {}",
                rawData != null ? rawData.size() : 0, labels.size());

        Map<String, Integer> dataMap = new HashMap<>();
        if (rawData != null) {
            for (TrendDataVO data : rawData) {
                if (data.getDate() != null && data.getReviewCount() != null) {
                    dataMap.put(data.getDate(), data.getReviewCount());
                    log.debug("原始数据: {} = {}", data.getDate(), data.getReviewCount());
                }
            }
        }

        List<Integer> values = new ArrayList<>();
        for (String label : labels) {
            Integer value = dataMap.getOrDefault(label, 0);
            values.add(value);
            if (value > 0) {
                log.debug("匹配数据: {} = {}", label, value);
            }
        }

        log.info("处理后数据 - 有数据的天数: {}",
                values.stream().filter(v -> v > 0).count());

        return values;
    }

    /**
     * 处理周数据并补齐缺失周
     */
    private List<Integer> processWeeklyData(List<TrendDataVO> rawData, List<String> labels) {
        Map<String, Integer> dataMap = new HashMap<>();
        if (rawData != null) {
            for (TrendDataVO data : rawData) {
                if (data.getWeekLabel() != null && data.getReviewCount() != null) {
                    dataMap.put(data.getWeekLabel(), data.getReviewCount());
                }
            }
        }

        List<Integer> values = new ArrayList<>();
        for (String label : labels) {
            values.add(dataMap.getOrDefault(label, 0));
        }

        return values;
    }

    /**
     * 处理月数据并补齐缺失月
     */
    private List<Integer> processMonthlyData(List<TrendDataVO> rawData, List<String> labels) {
        Map<String, Integer> dataMap = new HashMap<>();
        if (rawData != null) {
            for (TrendDataVO data : rawData) {
                if (data.getMonth() != null && data.getReviewCount() != null) {
                    dataMap.put(data.getMonth(), data.getReviewCount());
                }
            }
        }

        List<Integer> values = new ArrayList<>();
        for (String label : labels) {
            values.add(dataMap.getOrDefault(label, 0));
        }

        return values;
    }

    /**
     * 生成连续的日期标签
     */
    private List<String> generateDailyLabels(LocalDate start, LocalDate end) {
        List<String> labels = new ArrayList<>();
        LocalDate current = start;

        // 限制最大天数，防止生成过多标签
        long maxDays = 365; // 最多一年
        long daysBetween = ChronoUnit.DAYS.between(start, end);

        if (daysBetween > maxDays) {
            log.warn("时间范围过大({}天)，限制为{}天", daysBetween, maxDays);
            end = start.plusDays(maxDays);
        }

        while (!current.isAfter(end)) {
            labels.add(current.format(DateTimeFormatter.ISO_DATE));
            current = current.plusDays(1);
        }

        log.info("生成日期标签: {} 到 {}, 共{}天", start, end, labels.size());

        return labels;
    }

    /**
     * 生成周标签
     */
    private List<String> generateWeeklyLabels(LocalDate start, LocalDate end) {
        List<String> labels = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            int year = current.getYear();
            int week = getWeekOfYear(current);
            labels.add(String.format("%d-W%02d", year, week));
            current = current.plusWeeks(1);
        }

        // 确保不超过范围
        if (labels.size() > 0) {
            String lastLabel = labels.get(labels.size() - 1);
            String[] parts = lastLabel.split("-W");
            int year = Integer.parseInt(parts[0]); // 从标签中提取年份
            int lastWeek = Integer.parseInt(parts[1]);
            if (lastWeek > 52) {
                labels.set(labels.size() - 1, String.format("%d-W%02d", year, lastWeek - 52));
            }
        }

        return labels;
    }

    /**
     * 生成月标签
     */
    private List<String> generateMonthlyLabels(LocalDate start, LocalDate end) {
        List<String> labels = new ArrayList<>();
        LocalDate current = start.withDayOfMonth(1);
        LocalDate endMonth = end.withDayOfMonth(1);

        while (!current.isAfter(endMonth)) {
            labels.add(current.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            current = current.plusMonths(1);
        }

        return labels;
    }

    /**
     * 计算一年中的第几周
     */
    private int getWeekOfYear(LocalDate date) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return date.get(weekFields.weekOfWeekBasedYear());
    }

    /**
     * 计算趋势百分比
     */
    private BigDecimal calculateTrendPercentage(Long teacherId, TrendQueryDTO.DateRange currentRange) {
        // 计算上一个周期的日期范围
        LocalDate previousEnd = currentRange.getStartDate().minusDays(1);
        long daysBetween = ChronoUnit.DAYS.between(currentRange.getStartDate(), currentRange.getEndDate());
        LocalDate previousStart = previousEnd.minusDays(daysBetween);

        // 获取当前周期数据
        LocalDateTime currentStart = currentRange.getStartDate().atStartOfDay();
        LocalDateTime currentEnd = currentRange.getEndDate().atTime(23, 59, 59);
        Integer currentTotal = reviewRecordMapper.selectTotalReviews(teacherId, currentStart, currentEnd);
        currentTotal = currentTotal != null ? currentTotal : 0;

        // 获取上一个周期数据
        LocalDateTime previousStartDateTime = previousStart.atStartOfDay();
        LocalDateTime previousEndDateTime = previousEnd.atTime(23, 59, 59);
        Integer previousTotal = reviewRecordMapper.selectTotalReviews(teacherId, previousStartDateTime, previousEndDateTime);
        previousTotal = previousTotal != null ? previousTotal : 0;

        // 计算百分比
        if (previousTotal == 0) {
            return currentTotal > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        double percentage = ((currentTotal - previousTotal) * 100.0) / previousTotal;
        return BigDecimal.valueOf(percentage).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 获取上一个周期的时间范围
     */
    private TrendQueryDTO.DateRange getPreviousRange(TrendQueryDTO.DateRange currentRange) {
        LocalDate previousEnd = currentRange.getStartDate().minusDays(1);
        long daysBetween = ChronoUnit.DAYS.between(currentRange.getStartDate(), currentRange.getEndDate());
        LocalDate previousStart = previousEnd.minusDays(daysBetween);

        return new TrendQueryDTO.DateRange(previousStart, previousEnd);
    }

    /**
     * 构建趋势结果
     */
    private TrendResultDTO buildTrendResult(List<String> labels, List<Integer> values,
                                            BigDecimal trendPercentage, Integer total) {
        TrendResultDTO result = new TrendResultDTO();
        result.setXAxis(labels);

        TrendResultDTO.ChartSeriesDTO series = new TrendResultDTO.ChartSeriesDTO();
        series.setName("审核数量");
        series.setData(values);
        series.setType("line");
        series.setColor("#1890ff");

        result.setSeries(Collections.singletonList(series));
        result.setTotalReviews(total != null ? total : 0);
        result.setTrendPercentage(trendPercentage);
        result.setTrendType(getTrendType(trendPercentage));
        result.setMessage(generateTrendMessage(trendPercentage));

        return result;
    }

    /**
     * 获取趋势类型
     */
    private String getTrendType(BigDecimal percentage) {
        if (percentage == null) return "neutral";

        if (percentage.compareTo(BigDecimal.ZERO) > 0) {
            return "up";
        } else if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            return "down";
        } else {
            return "neutral";
        }
    }

    /**
     * 生成趋势消息
     */
    private String generateTrendMessage(BigDecimal percentage) {
        if (percentage == null) return "暂无趋势数据";

        if (percentage.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("较上一周期增长%s%%", percentage.abs());
        } else if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            return String.format("较上一周期下降%s%%", percentage.abs());
        } else {
            return "与上一周期持平";
        }
    }

    /**
     * 验证查询参数
     */
    private void validateTrendQuery(TrendQueryDTO query) {
        if (query.getTeacherId() == null) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "教师ID不能为空");
        }
        if (StringUtils.isBlank(query.getChartType())) {
            query.setChartType("daily");
        }
        if (StringUtils.isBlank(query.getTimeRange())) {
            query.setTimeRange("week");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> getReviewStats(Long teacherId, String timeRange) {
        log.info("获取审核统计数据: teacherId={}, timeRange={}", teacherId, timeRange);

        // 计算时间范围 - 修正时间计算逻辑
        LocalDate now = LocalDate.now();  // 使用当前真实时间
        LocalDate endDate = now;  // 结束时间为今天
        LocalDate startDate;
        LocalDate compareStartDate;

        log.info("当前系统时间: {}", now);  // 添加调试日志

        switch (timeRange.toLowerCase()) {
            case "week":
                startDate = endDate.minusDays(6);  // 改为减6天，包含今天共7天
                compareStartDate = startDate.minusDays(7);
                break;
            case "month":
                startDate = endDate.minusDays(29); // 改为减29天，包含今天共30天
                compareStartDate = startDate.minusDays(30);
                break;
            case "quarter":
                startDate = endDate.minusDays(89); // 改为减89天，包含今天共90天
                compareStartDate = startDate.minusDays(90);
                break;
            case "year":
                startDate = endDate.minusDays(364); // 改为减364天，包含今天共365天
                compareStartDate = startDate.minusDays(365);
                break;
            default:
                startDate = endDate.minusDays(6);
                compareStartDate = startDate.minusDays(7);
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        LocalDateTime compareStartDateTime = compareStartDate.atStartOfDay();

        log.info("查询时间范围: {} 到 {}", startDateTime, endDateTime);  // 添加调试日志

        // 查询当前周期统计数据
        Integer totalReviews = reviewRecordMapper.selectTotalReviews(teacherId, startDateTime, endDateTime);
        totalReviews = totalReviews != null ? totalReviews : 0;
        log.info("总审核数查询结果: {}", totalReviews);

        // 调试：先查询所有状态的审核记录
        List<Map<String, Object>> allStatusReviews = reviewRecordMapper.selectAllStatusReviews(teacherId, startDateTime, endDateTime);
        log.info("所有状态审核记录详情: {}", allStatusReviews);

        // 调试：查询具体的审核记录
        List<TrendDataVO> dailyTrend = reviewRecordMapper.selectDailyTrend(teacherId, startDateTime, endDateTime);
        log.info("每日趋势数据: {}", dailyTrend);

        // 只查询通过和拒绝两种状态的数量（符合前端需求）
        Integer approvedReviews = reviewRecordMapper.countByStatus(teacherId, 3, startDateTime, endDateTime); // 3-审核通过
        Integer rejectedReviews = reviewRecordMapper.countByStatus(teacherId, 4, startDateTime, endDateTime); // 4-审核不通过

        log.info("审核状态统计 - 通过: {}, 拒绝: {}", approvedReviews, rejectedReviews);

        // 查询指导学生数
        Integer currentStudents = reviewRecordMapper.countCurrentStudents(teacherId);
        log.info("指导学生数: {}", currentStudents);

        // 查询比较周期数据（只针对通过和拒绝状态）
        Integer previousApproved = reviewRecordMapper.countByStatus(teacherId, 3, compareStartDateTime, startDateTime.minusSeconds(1));
        Integer previousRejected = reviewRecordMapper.countByStatus(teacherId, 4, compareStartDateTime, startDateTime.minusSeconds(1));

        Integer previousTotal = (previousApproved != null ? previousApproved : 0) + (previousRejected != null ? previousRejected : 0);
        Integer currentTotal = (approvedReviews != null ? approvedReviews : 0) + (rejectedReviews != null ? rejectedReviews : 0);

        // 计算趋势
        BigDecimal approvedTrend = calculatePercentageTrend(approvedReviews, previousApproved);
        BigDecimal rejectedTrend = calculatePercentageTrend(rejectedReviews, previousRejected);
        BigDecimal totalTrend = calculatePercentageTrend(currentTotal, previousTotal);

        // 构建适合前端图表的结果结构
        Map<String, Object> stats = new HashMap<>();

        // 基础统计数据
        stats.put("totalReviews", currentTotal);
        stats.put("approvedReviews", approvedReviews != null ? approvedReviews : 0);
        stats.put("rejectedReviews", rejectedReviews != null ? rejectedReviews : 0);
        stats.put("currentStudents", currentStudents != null ? currentStudents : 0);

        // 趋势数据
        stats.put("totalTrend", totalTrend);
        stats.put("approvedTrend", approvedTrend != null ? approvedTrend : BigDecimal.ZERO);
        stats.put("rejectedTrend", rejectedTrend != null ? rejectedTrend : BigDecimal.ZERO);

        // 为ECharts准备的图表数据格式
        Map<String, Object> chartData = new HashMap<>();
        chartData.put("labels", new String[]{"通过", "拒绝"});
        chartData.put("values", new Integer[]{
                approvedReviews != null ? approvedReviews : 0,
                rejectedReviews != null ? rejectedReviews : 0
        });
        chartData.put("colors", new String[]{"#52c41a", "#ff4d4f"}); // 绿色表示通过，红色表示拒绝
        stats.put("chartData", chartData);

        return stats;
    }

    /**
     * 计算百分比趋势
     */
    private BigDecimal calculatePercentageTrend(Integer current, Integer previous) {
        if (previous == null || previous == 0) {
            return current != null && current > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        double percentage = ((current - previous) * 100.0) / previous;
        return BigDecimal.valueOf(percentage).setScale(1, RoundingMode.HALF_UP);
    }
        @Override
        public PageResultVO<TrendDataVO> getDetailData (TrendQueryDTO query, Integer page, Integer size){
            validateTrendQuery(query);

            // 计算时间范围
            TrendQueryDTO.DateRange dateRange = query.getDateRange();
            LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
            LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

            // 查询总条数（用于分页计算）
            Integer total = reviewRecordMapper.selectTotalReviews(query.getTeacherId(), startDateTime, endDateTime);
            total = total != null ? total : 0;

            // 计算分页
            if (page == null || page < 1) page = 1;
            if (size == null || size < 1) size = 10;
            int offset = (page - 1) * size;

            // 查询分页数据（保持原有逻辑，但修复PageResult设置问题）
            List<TrendDataVO> dataList = reviewRecordMapper.selectDetailData(
                    query.getTeacherId(), startDateTime, endDateTime, offset, size);

            // 处理数据格式
            if (dataList != null) {
                for (TrendDataVO data : dataList) {
                    // 格式化平均审核时长
                    if (data.getAvgReviewTime() != null) {
                        data.setAvgReviewTime(data.getAvgReviewTime().setScale(1, RoundingMode.HALF_UP));
                    }
                    // 格式化平均相似度
                    if (data.getAvgSimilarity() != null) {
                        data.setAvgSimilarity(data.getAvgSimilarity().setScale(1, RoundingMode.HALF_UP));
                    }
                }
            }

            // 构建分页结果 - 使用统一的PageResultVO
            PageResultVO<TrendDataVO> result = new PageResultVO<>();
            result.setPageNum(page);
            result.setPageSize(size);
            result.setTotalCount(total);
            result.setTotalPage((int) Math.ceil((double) total / size));
            result.setList(dataList != null ? dataList : new ArrayList<>());
            
            // 添加调试日志
            log.info("详细数据查询结果 - 总数: {}, 当前页数据条数: {}, 分页信息: page={}, pageSize={}, totalPages={}",
                    total, dataList != null ? dataList.size() : 0, page, size, result.getTotalPage());

            return result;

        }

        @Override
        public void exportTrendData (TrendQueryDTO query, HttpServletResponse response){
            validateTrendQuery(query);

            try {
                // 设置响应头
                String fileName = String.format("审核数据_%s_%s_%s.xlsx",
                        query.getTeacherId(), query.getChartType(), LocalDate.now());
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=" +
                        URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));

                // 查询所有数据（不分页）
                TrendQueryDTO.DateRange dateRange = query.getDateRange();
                LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
                LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

                List<TrendDataVO> dataList = reviewRecordMapper.selectAllDetailData(
                        query.getTeacherId(), startDateTime, endDateTime);

                // 创建Excel
                try (Workbook workbook = new XSSFWorkbook()) {
                    Sheet sheet = workbook.createSheet("审核数据");

                    // 创建表头
                    Row headerRow = sheet.createRow(0);
                    String[] headers = {"日期", "审核总数", "通过数", "驳回数", "待审核", "平均相似度", "平均审核时长(小时)"};
                    for (int i = 0; i < headers.length; i++) {
                        Cell cell = headerRow.createCell(i);
                        cell.setCellValue(headers[i]);

                        // 设置表头样式
                        CellStyle headerStyle = workbook.createCellStyle();
                        Font font = workbook.createFont();
                        font.setBold(true);
                        headerStyle.setFont(font);
                        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        cell.setCellStyle(headerStyle);
                    }

                    // 填充数据
                    if (dataList != null) {
                        int rowNum = 1;
                        for (TrendDataVO data : dataList) {
                            Row row = sheet.createRow(rowNum++);

                            // 日期
                            if (data.getDate() != null) {
                                row.createCell(0).setCellValue(data.getDate());
                            } else if (data.getWeekLabel() != null) {
                                row.createCell(0).setCellValue(data.getWeekLabel());
                            } else if (data.getMonth() != null) {
                                row.createCell(0).setCellValue(data.getMonth());
                            }

                            // 审核总数
                            row.createCell(1).setCellValue(data.getReviewCount() != null ? data.getReviewCount() : 0);

                            // 通过数
                            row.createCell(2).setCellValue(data.getApprovedCount() != null ? data.getApprovedCount() : 0);

                            // 驳回数
                            row.createCell(3).setCellValue(data.getRejectedCount() != null ? data.getRejectedCount() : 0);

                            // 待审核数（需要根据状态计算）
                            Integer pendingCount = data.getReviewCount() != null ?
                                    data.getReviewCount() - (data.getApprovedCount() != null ? data.getApprovedCount() : 0)
                                            - (data.getRejectedCount() != null ? data.getRejectedCount() : 0) : 0;
                            row.createCell(4).setCellValue(Math.max(pendingCount, 0));

                            // 平均相似度
                            if (data.getAvgSimilarity() != null) {
                                row.createCell(5).setCellValue(data.getAvgSimilarity().doubleValue());
                            }

                            // 平均审核时长
                            if (data.getAvgReviewTime() != null) {
                                row.createCell(6).setCellValue(data.getAvgReviewTime().doubleValue());
                            }
                        }

                        // 自动调整列宽
                        for (int i = 0; i < headers.length; i++) {
                            sheet.autoSizeColumn(i);
                        }
                    }

                    // 写入响应流
                    workbook.write(response.getOutputStream());
                    response.flushBuffer();

                    log.info("数据导出成功: teacherId={}, 导出{}条记录",
                            query.getTeacherId(), dataList != null ? dataList.size() : 0);
                }

            } catch (IOException e) {
                log.error("导出数据失败: {}", e.getMessage());
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "导出数据失败: " + "系统异常，请稍后重试");
            }
        }

        @Override
        public List<ReviewStatusVO> getReviewStatusDistribution (Long teacherId, String timeRange){
            log.info("获取审核状态分布: teacherId={}, timeRange={}", teacherId, timeRange);

            // 如果是默认的近期时间范围，使用calculateDateRangeFromTimeRange方法
            DateRange dateRange;
            if ("week".equals(timeRange) || "month".equals(timeRange) || "quarter".equals(timeRange) || "year".equals(timeRange)) {
                dateRange = calculateDateRangeFromTimeRange(timeRange);
            } else {
                // 自定义时间范围或其他情况，使用当前日期
                LocalDate now = LocalDate.now();
                LocalDate startDate;
                switch (timeRange.toLowerCase()) {
                    case "all":  // 显示所有历史数据
                        startDate = LocalDate.of(2022, 1, 1);  // 从2022年开始
                        break;
                    default:
                        dateRange = calculateDateRangeFromTimeRange("week");  // 默认使用周范围
                        return getReviewStatusWithCustomRange(teacherId, dateRange);
                }
                dateRange = new DateRange(startDate, now);
            }
            
            return getReviewStatusWithCustomRange(teacherId, dateRange);
        }

        private List<ReviewStatusVO> getReviewStatusWithCustomRange(Long teacherId, DateRange dateRange) {
            LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
            LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

            List<ReviewStatusVO> distribution = reviewRecordMapper.selectReviewStatusDistribution(
                    teacherId, startDateTime, endDateTime);

            // 只保留通过和拒绝两种状态，过滤掉待审核等其他状态
            if (distribution != null) {
                distribution = distribution.stream()
                        .filter(vo -> "审核通过".equals(vo.getStatusName()) || "审核未通过".equals(vo.getStatusName()))
                        .collect(Collectors.toList());
            }

            // 确保通过和拒绝状态都有数据
            ensurePassRejectStatusesPresent(distribution);

            return distribution;
        }

        @Override
        public List<SimilarityDistributionVO> getSimilarityDistribution (Long teacherId, String timeRange){
            log.info("获取相似度分布: teacherId={}, timeRange={}", teacherId, timeRange);

            // 增强的时间范围计算和调试
            DateRange dateRange = calculateDateRangeFromTimeRange(timeRange);
            LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
            LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

            // 添加调试信息
            log.info("相似度分布查询时间范围: {} 到 {}", startDateTime, endDateTime);

            // 先查询基础数据量
            Integer totalCount = paperInfoMapper.countTotalPapers(teacherId, startDateTime, endDateTime);
            log.info("该时间范围内总论文数: {}", totalCount);

            // 如果没有数据，查询教师总论文数（无时间限制）
            if (totalCount == null || totalCount == 0) {
                Integer totalTeacherPapers = paperInfoMapper.countTeacherPapers(teacherId);
                log.info("教师总论文数（无时间限制）: {}", totalTeacherPapers);

                // 调试：查询该教师的所有论文数据
                List<PaperInfo> allPapers = paperInfoMapper.selectList(
                        new LambdaQueryWrapper<PaperInfo>()
                                .eq(PaperInfo::getTeacherId, teacherId)
                                .eq(PaperInfo::getIsDeleted, 0)
                                .isNotNull(PaperInfo::getSimilarityRate)
                                .gt(PaperInfo::getSimilarityRate, 0)
                                .orderByDesc(PaperInfo::getSubmitTime)
                );
                log.info("教师所有有效论文数据: {}", allPapers);
            }

            if (totalCount == null || totalCount == 0) {
                log.warn("警告: 时间范围内没有找到论文数据，尝试扩大查询范围");
                // 如果没有数据，尝试查询更长时间范围 - 修复错误
                LocalDate oneYearAgo = LocalDate.now().minusYears(1);
                LocalDate startOfLastYear = oneYearAgo.withDayOfYear(1);  // 获取去年的第一天
                LocalDate endOfNextYear = LocalDate.now().withDayOfYear(1).plusYears(1).minusDays(1);  // 获取明年第一天的前一天

                startDateTime = startOfLastYear.atStartOfDay();
                endDateTime = endOfNextYear.atTime(23, 59, 59);
                log.info("扩大查询范围至: {} 到 {}", startDateTime, endDateTime);
            }

            List<SimilarityDistributionVO> distribution = paperInfoMapper.selectSimilarityDistribution(
                    teacherId, startDateTime, endDateTime);

            log.info("原始相似度分布数据: {}", distribution);

            // 确保所有范围都有数据
            ensureAllRangesPresent(distribution);

            // 添加统计信息
            int totalPapers = distribution.stream().mapToInt(SimilarityDistributionVO::getPaperCount).sum();
            log.info("相似度分布统计 - 总论文数: {}, 分布详情: {}", totalPapers, distribution);

            return distribution;
        }

        @Override
        public List<CollegeDistributionVO> getCollegeDistribution (Long teacherId, String timeRange){
            log.info("获取学院分布: teacherId={}, timeRange={}", teacherId, timeRange);

            DateRange dateRange = calculateDateRangeFromTimeRange(timeRange);
            LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
            LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

            List<CollegeDistributionVO> distribution = paperInfoMapper.selectCollegeDistribution(
                    teacherId, startDateTime, endDateTime);

            // 限制返回数量并处理null值
            if (distribution != null) {
                distribution = distribution.stream()
                        .filter(item -> item.getCollegeName() != null && !item.getCollegeName().isEmpty())
                        .limit(10)
                        .collect(Collectors.toList());

                // 处理null值
                distribution.forEach(item -> {
                    if (item.getStudentCount() == null) item.setStudentCount(0);
                    if (item.getReviewCount() == null) item.setReviewCount(0);
                    if (item.getAvgSimilarity() == null) item.setAvgSimilarity(BigDecimal.ZERO);
                });
            } else {
                distribution = new ArrayList<>();
            }

            return distribution;
        }

        /**
         * 获取所有趋势数据（导出用）
         */
        private List<TrendDataVO> getAllTrendDataForExport (TrendQueryDTO query){
            TrendQueryDTO.DateRange dateRange = query.getDateRange();
            LocalDateTime startDateTime = dateRange.getStartDate().atStartOfDay();
            LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

            switch (query.getChartType()) {
                case "daily":
                    return reviewRecordMapper.selectDailyTrend(query.getTeacherId(), startDateTime, endDateTime);
                case "weekly":
                    return reviewRecordMapper.selectWeeklyTrend(query.getTeacherId(), startDateTime, endDateTime);
                case "monthly":
                    return reviewRecordMapper.selectMonthlyTrend(query.getTeacherId(), startDateTime, endDateTime);
                default:
                    return reviewRecordMapper.selectDailyTrend(query.getTeacherId(), startDateTime, endDateTime);
            }
        }
        /**
         * 创建审核趋势数据sheet
         */
        private void createTrendDataSheet (Sheet sheet, List < TrendDataVO > dataList, String chartType){
            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers;
            if ("daily".equals(chartType)) {
                headers = new String[]{"日期", "审核总数", "通过数", "驳回数", "平均相似度(%)", "平均审核时长(小时)"};
            } else if ("weekly".equals(chartType)) {
                headers = new String[]{"周次", "审核总数", "独立论文数", "平均审核时长(小时)"};
            } else {
                headers = new String[]{"月份", "审核总数", "平均相似度(%)", "平均审核时长(小时)"};
            }

            CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 填充数据
            if (dataList != null) {
                int rowNum = 1;
                for (TrendDataVO data : dataList) {
                    Row row = sheet.createRow(rowNum++);

                    if ("daily".equals(chartType)) {
                        row.createCell(0).setCellValue(data.getDate());
                        row.createCell(1).setCellValue(data.getReviewCount() != null ? data.getReviewCount() : 0);
                        row.createCell(2).setCellValue(data.getApprovedCount() != null ? data.getApprovedCount() : 0);
                        row.createCell(3).setCellValue(data.getRejectedCount() != null ? data.getRejectedCount() : 0);
                        if (data.getAvgSimilarity() != null) {
                            row.createCell(4).setCellValue(data.getAvgSimilarity().doubleValue());
                        }
                        if (data.getAvgReviewTime() != null) {
                            row.createCell(5).setCellValue(data.getAvgReviewTime().doubleValue());
                        }
                    } else if ("weekly".equals(chartType)) {
                        row.createCell(0).setCellValue(data.getWeekLabel());
                        row.createCell(1).setCellValue(data.getReviewCount() != null ? data.getReviewCount() : 0);
                        row.createCell(2).setCellValue(data.getUniquePapers() != null ? data.getUniquePapers() : 0);
                        if (data.getAvgReviewTime() != null) {
                            row.createCell(3).setCellValue(data.getAvgReviewTime().doubleValue());
                        }
                    } else {
                        row.createCell(0).setCellValue(data.getMonth());
                        row.createCell(1).setCellValue(data.getReviewCount() != null ? data.getReviewCount() : 0);
                        if (data.getAvgSimilarity() != null) {
                            row.createCell(2).setCellValue(data.getAvgSimilarity().doubleValue());
                        }
                        if (data.getAvgReviewTime() != null) {
                            row.createCell(3).setCellValue(data.getAvgReviewTime().doubleValue());
                        }
                    }
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
        }

        /**
         * 创建统计汇总sheet
         */
        private void createStatsSheet (Sheet sheet, TrendQueryDTO query){
            // 获取统计数据
            Map<String, Object> stats = getReviewStats(query.getTeacherId(), query.getTimeRange());

            // 创建表头
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(sheet.getWorkbook());
            String[] headers = {"统计项", "数值", "备注"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 填充数据
            String[][] statsData = {
                    {"总审核数", stats.get("totalReviews").toString(), "时间段内总审核数量"},
                    {"待审核数", stats.get("pendingReviews").toString(), "当前待审核的论文数量"},
                    {"已通过数", stats.get("approvedReviews").toString(), "已通过审核的论文数量"},
                    {"指导学生数", stats.get("currentStudents").toString(), "当前指导的学生数量"},
                    {"审核趋势", stats.get("reviewTrend") + "%", "与上一周期比较"},
                    {"通过趋势", stats.get("approvedTrend") + "%", "与上一周期比较"}
            };

            for (int i = 0; i < statsData.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < statsData[i].length; j++) {
                    row.createCell(j).setCellValue(statsData[i][j]);
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
        }

        /**
         * 创建分布数据sheet
         */
        private void createDistributionSheet (Sheet sheet, TrendQueryDTO query){
            // 获取分布数据
            List<ReviewStatusVO> statusDistribution = getReviewStatusDistribution(query.getTeacherId(), query.getTimeRange());
            List<SimilarityDistributionVO> similarityDistribution = getSimilarityDistribution(query.getTeacherId(), query.getTimeRange());
            List<CollegeDistributionVO> collegeDistribution = getCollegeDistribution(query.getTeacherId(), query.getTimeRange());

            // 审核状态分布
            Row statusHeaderRow = sheet.createRow(0);
            statusHeaderRow.createCell(0).setCellValue("审核状态分布");
            CellStyle sectionHeaderStyle = createSectionHeaderStyle(sheet.getWorkbook());
            statusHeaderRow.getCell(0).setCellStyle(sectionHeaderStyle);

            Row statusSubHeaderRow = sheet.createRow(1);
            String[] statusHeaders = {"状态", "数量", "百分比", "颜色"};
            for (int i = 0; i < statusHeaders.length; i++) {
                statusSubHeaderRow.createCell(i).setCellValue(statusHeaders[i]);
            }

            int rowNum = 2;
            for (ReviewStatusVO status : statusDistribution) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(status.getStatusName());
                row.createCell(1).setCellValue(status.getCount());
                if (status.getPercentage() != null) {
                    row.createCell(2).setCellValue(status.getPercentage() + "%");
                }
                row.createCell(3).setCellValue(status.getColor());
            }

            // 相似度分布
            rowNum += 2;
            Row similarityHeaderRow = sheet.createRow(rowNum);
            similarityHeaderRow.createCell(0).setCellValue("相似度分布");
            similarityHeaderRow.getCell(0).setCellStyle(sectionHeaderStyle);

            Row similaritySubHeaderRow = sheet.createRow(rowNum + 1);
            String[] similarityHeaders = {"相似度范围", "论文数量", "百分比"};
            for (int i = 0; i < similarityHeaders.length; i++) {
                similaritySubHeaderRow.createCell(i).setCellValue(similarityHeaders[i]);
            }

            rowNum += 2;
            for (SimilarityDistributionVO similarity : similarityDistribution) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(similarity.getRange());
                row.createCell(1).setCellValue(similarity.getPaperCount());
                if (similarity.getPercentage() != null) {
                    row.createCell(2).setCellValue(similarity.getPercentage() + "%");
                }
            }

            // 学院分布
            rowNum += 2;
            Row collegeHeaderRow = sheet.createRow(rowNum);
            collegeHeaderRow.createCell(0).setCellValue("学院分布");
            collegeHeaderRow.getCell(0).setCellStyle(sectionHeaderStyle);

            Row collegeSubHeaderRow = sheet.createRow(rowNum + 1);
            String[] collegeHeaders = {"学院名称", "学生数量", "审核数量", "平均相似度(%)"};
            for (int i = 0; i < collegeHeaders.length; i++) {
                collegeSubHeaderRow.createCell(i).setCellValue(collegeHeaders[i]);
            }

            rowNum += 2;
            for (CollegeDistributionVO college : collegeDistribution) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(college.getCollegeName());
                row.createCell(1).setCellValue(college.getStudentCount());
                row.createCell(2).setCellValue(college.getReviewCount());
                if (college.getAvgSimilarity() != null) {
                    row.createCell(3).setCellValue(college.getAvgSimilarity().doubleValue());
                }
            }

            // 自动调整列宽
            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }
        }

        /**
         * 创建表头样式
         */
        private CellStyle createHeaderStyle (Workbook workbook){
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /**
         * 创建章节标题样式
         */
        private CellStyle createSectionHeaderStyle (Workbook workbook){
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 14);
            style.setFont(font);
            return style;
        }

        /**
         * 格式化趋势数据
         */
        private TrendDataVO formatTrendData (TrendDataVO data){
            if (data.getAvgSimilarity() != null) {
                data.setAvgSimilarity(data.getAvgSimilarity().setScale(1, RoundingMode.HALF_UP));
            }
            if (data.getAvgReviewTime() != null) {
                data.setAvgReviewTime(data.getAvgReviewTime().setScale(1, RoundingMode.HALF_UP));
            }
            return data;
        }

        /**
         * 确保通过和拒绝状态都有数据（只保留这两种状态）
         */
        private void ensurePassRejectStatusesPresent (List < ReviewStatusVO > distribution) {
            if (distribution == null) {
                distribution = new ArrayList<>();
            }

            // 只关注通过和拒绝两种状态
            Map<Integer, String> statusMap = new HashMap<>();
            statusMap.put(3, "审核通过");
            statusMap.put(4, "审核未通过");

            Map<Integer, String> colorMap = new HashMap<>();
            colorMap.put(3, "#52c41a");  // 绿色表示通过
            colorMap.put(4, "#ff4d4f");  // 红色表示拒绝

            for (Map.Entry<Integer, String> entry : statusMap.entrySet()) {
                Integer statusCode = entry.getKey();
                String statusName = entry.getValue();

                boolean exists = distribution.stream()
                        .anyMatch(d -> statusCode.equals(d.getStatus()) || statusName.equals(d.getStatusName()));

                if (!exists) {
                    ReviewStatusVO newStatus = new ReviewStatusVO();
                    newStatus.setStatus(statusCode.toString());
                    newStatus.setStatusName(statusName);
                    newStatus.setCount(0);
                    newStatus.setPercentage(BigDecimal.ZERO);
                    newStatus.setColor(colorMap.getOrDefault(statusCode, "#d9d9d9"));
                    distribution.add(newStatus);
                }
            }
        }

        /**
         * 确保所有状态都有数据（保留原来的方法以供其他地方使用）
         */
        private void ensureAllStatusesPresent (List < ReviewStatusVO > distribution) {
            if (distribution == null) {
                distribution = new ArrayList<>();
            }

            Map<String, String> statusMap = new HashMap<>();
            statusMap.put("pending", "待审核");
            statusMap.put("in_review", "审核中");
            statusMap.put("approved", "已通过");
            statusMap.put("rejected", "已驳回");
            statusMap.put("need_revision", "需要修改");

            Map<String, String> colorMap = new HashMap<>();
            colorMap.put("approved", "#52c41a");
            colorMap.put("pending", "#faad14");
            colorMap.put("rejected", "#ff4d4f");
            colorMap.put("need_revision", "#1890ff");
            colorMap.put("in_review", "#d9d9d9");

            for (Map.Entry<String, String> entry : statusMap.entrySet()) {
                String status = entry.getKey();
                boolean exists = distribution.stream()
                        .anyMatch(d -> status.equals(d.getStatus()));

                if (!exists) {
                    ReviewStatusVO newStatus = new ReviewStatusVO();
                    newStatus.setStatus(status);
                    newStatus.setStatusName(entry.getValue());
                    newStatus.setCount(0);
                    newStatus.setPercentage(BigDecimal.ZERO);
                    newStatus.setColor(colorMap.getOrDefault(status, "#d9d9d9"));
                    distribution.add(newStatus);
                }
            }
        }

        /**
         * 确保所有相似度范围都有数据
         */
        private void ensureAllRangesPresent (List < SimilarityDistributionVO > distribution) {
            if (distribution == null) {
                distribution = new ArrayList<>();
            }

            String[] ranges = {"0-10%", "10-20%", "20-30%", "30-40%", "40-50%", "50%以上"};
            Map<String, SimilarityDistributionVO> distributionMap = distribution.stream()
                    .collect(Collectors.toMap(SimilarityDistributionVO::getRange, d -> d));

            List<SimilarityDistributionVO> completeDistribution = new ArrayList<>();
            int total = distribution.stream()
                    .mapToInt(SimilarityDistributionVO::getPaperCount)
                    .sum();

            for (String range : ranges) {
                SimilarityDistributionVO item = distributionMap.get(range);
                if (item == null) {
                    item = new SimilarityDistributionVO();
                    item.setRange(range);
                    item.setPaperCount(0);
                    item.setPercentage(BigDecimal.ZERO);
                } else if (item.getPercentage() == null && total > 0) {
                    BigDecimal percentage = BigDecimal.valueOf(item.getPaperCount() * 100.0 / total)
                            .setScale(1, RoundingMode.HALF_UP);
                    item.setPercentage(percentage);
                }
                completeDistribution.add(item);
            }

            distribution.clear();
            distribution.addAll(completeDistribution);
        }

        /**
         * 根据时间范围计算日期范围
         */
        private DateRange calculateDateRangeFromTimeRange (String timeRange){
            LocalDate endDate = LocalDate.now();
            LocalDate startDate;

            switch (timeRange.toLowerCase()) {
                case "week":
                    startDate = endDate.minusWeeks(1);
                    break;
                case "month":
                    startDate = endDate.minusMonths(1);
                    break;
                case "quarter":
                    startDate = endDate.minusMonths(3);
                    break;
                case "year":
                    startDate = endDate.minusYears(1);
                    break;
                default:
                    startDate = endDate.minusWeeks(1);
            }

            return new DateRange(startDate, endDate);
        }


        @Data
        @AllArgsConstructor
        private static class DateRange {
            private LocalDate startDate;
            private LocalDate endDate;
        }
    }
