package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.DefenseBatchMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseRecordMapper;
import com.abin.checkrepeatservice.admin.service.AdminExportService;
import com.abin.checkrepeatservice.admin.service.AdminPaperService;
import com.abin.checkrepeatservice.admin.service.AdminStatReportService;
import com.abin.checkrepeatservice.admin.vo.DefenseConclusionExcelVO;
import com.abin.checkrepeatservice.admin.vo.PaperExportExcelVO;
import com.abin.checkrepeatservice.admin.vo.ReviewExportExcelVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.SemesterUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.DefenseBatch;
import com.abin.checkrepeatservice.pojo.entity.DefenseRecord;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewRecord;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Wave 3：审核记录 / 答辩结论导出实现。
 *
 * <p>二进制导出约定：所有查询与参数校验都在写流之前完成；失败时抛业务异常，
 * 由全局处理器返回 JSON，不会出现「半个 xlsx」。空数据集只输出表头。</p>
 */
@Service
@Slf4j
public class AdminExportServiceImpl implements AdminExportService {

    private static final int EXPORT_MAX_ROWS = 10000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Resource
    private ReviewRecordMapper reviewRecordMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private DefenseBatchMapper defenseBatchMapper;

    @Resource
    private DefenseRecordMapper defenseRecordMapper;

    // Wave 3：学期包复用论文清单与查重统计的既有生成逻辑
    @Resource
    private AdminPaperService adminPaperService;

    @Resource
    private AdminStatReportService adminStatReportService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void exportReviews(String semester, String startDate, String endDate, HttpServletResponse response) {
        SemesterUtils.Range range = SemesterUtils.resolve(semester, startDate, endDate);
        List<ReviewExportExcelVO> rows = buildReviewRows(range);
        writeExcel(response, "审核记录", range.semester(), rows, ReviewExportExcelVO.class);
        log.info("导出审核记录成功: semester={}, rows={}", semester, rows.size());
    }

    @Override
    public void exportDefenseConclusions(String semester, String startDate, String endDate,
                                         HttpServletResponse response) {
        SemesterUtils.Range range = SemesterUtils.resolve(semester, startDate, endDate);
        List<DefenseConclusionExcelVO> rows = buildDefenseRows(range);
        writeExcel(response, "答辩结论", range.semester(), rows, DefenseConclusionExcelVO.class);
        log.info("导出答辩结论成功: semester={}, rows={}", semester, rows.size());
    }

    @Override
    public void exportSemesterPackage(String semester, String startDate, String endDate,
                                      String items, HttpServletResponse response) {
        // 所有文件先在内存里生成完毕，再写 ZIP；参数/数据异常不会留下半个压缩包
        SemesterUtils.Range range = SemesterUtils.resolve(semester, startDate, endDate);
        Set<String> selectedItems = parsePackageItems(items);
        List<PackageFile> files = new ArrayList<>();

        if (selectedItems.contains("papers")) {
            List<PaperExportExcelVO> rows = adminPaperService.buildPaperExportRows(semester, startDate, endDate);
            files.add(new PackageFile("论文清单.xlsx",
                    writeXlsxBytes("论文清单", rows, PaperExportExcelVO.class), rows.size()));
        }
        if (selectedItems.contains("checks")) {
            ByteArrayOutputStream checkOut = new ByteArrayOutputStream();
            int checkRows = adminStatReportService.exportStatReportToStream(checkOut);
            files.add(new PackageFile("查重统计.xlsx", checkOut.toByteArray(), checkRows));
        }
        if (selectedItems.contains("reviews")) {
            List<ReviewExportExcelVO> rows = buildReviewRows(range);
            files.add(new PackageFile("审核记录.xlsx",
                    writeXlsxBytes("审核记录", rows, ReviewExportExcelVO.class), rows.size()));
        }
        if (selectedItems.contains("defense")) {
            List<DefenseConclusionExcelVO> rows = buildDefenseRows(range);
            files.add(new PackageFile("答辩结论.xlsx",
                    writeXlsxBytes("答辩结论", rows, DefenseConclusionExcelVO.class), rows.size()));
        }

        List<Map<String, Object>> fileMetas = new ArrayList<>();
        for (PackageFile file : files) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", file.name());
            meta.put("rows", file.rows());
            fileMetas.add(meta);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("semester", semester);
        manifest.put("generatedAt", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        manifest.put("files", fileMetas);

        byte[] manifestBytes;
        try {
            manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
        } catch (Exception e) {
            throw new IllegalStateException("生成 manifest.json 失败：" + e.getMessage(), e);
        }

        try {
            response.setContentType("application/zip");
            response.setCharacterEncoding("utf-8");
            String suffix = semester == null || semester.isBlank() ? "" : "_" + semester;
            String fileName = "学期数据包" + suffix + "_"
                    + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".zip";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);

            try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
                for (PackageFile file : files) {
                    putZipEntry(zip, file.name(), file.content());
                }
                putZipEntry(zip, "manifest.json", manifestBytes);
                zip.finish();
            }
            log.info("导出学期数据包成功: semester={}, files={}", semester, files.size());
        } catch (IOException e) {
            throw new IllegalStateException("导出学期数据包失败：" + e.getMessage(), e);
        }
    }

    private Set<String> parsePackageItems(String items) {
        Set<String> allowed = new LinkedHashSet<>(List.of("papers", "checks", "reviews", "defense"));
        if (items == null || items.isBlank()) {
            return allowed;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String token : items.split(",")) {
            String item = token.trim().toLowerCase();
            if (item.isEmpty()) {
                continue;
            }
            if (!allowed.contains(item)) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "items 仅支持 papers/checks/reviews/defense：" + item, null);
            }
            selected.add(item);
        }
        return selected.isEmpty() ? allowed : selected;
    }

    private <T> byte[] writeXlsxBytes(String sheetName, List<T> rows, Class<T> clazz) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, clazz).sheet(sheetName).doWrite(rows);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 " + sheetName + " 失败：" + e.getMessage(), e);
        }
    }

    private void putZipEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private record PackageFile(String name, byte[] content, int rows) {
    }

    private List<ReviewExportExcelVO> buildReviewRows(SemesterUtils.Range range) {
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<ReviewRecord>()
                .eq(ReviewRecord::getIsDeleted, 0);
        if (range.start() != null) {
            wrapper.ge(ReviewRecord::getReviewTime, range.start());
        }
        if (range.endExclusive() != null) {
            wrapper.lt(ReviewRecord::getReviewTime, range.endExclusive());
        }
        wrapper.orderByDesc(ReviewRecord::getReviewTime).last("LIMIT " + EXPORT_MAX_ROWS);

        List<ReviewRecord> records = reviewRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> paperIds = records.stream().map(ReviewRecord::getPaperId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, PaperInfo> paperMap = paperIds.isEmpty() ? Collections.emptyMap()
                : paperInfoMapper.selectBatchIds(paperIds).stream()
                        .collect(Collectors.toMap(PaperInfo::getId, p -> p));

        List<Long> userIds = new ArrayList<>();
        paperMap.values().forEach(p -> {
            if (p.getStudentId() != null) userIds.add(p.getStudentId());
            if (p.getTeacherId() != null) userIds.add(p.getTeacherId());
        });
        records.forEach(r -> {
            if (r.getAuditorId() != null) userIds.add(r.getAuditorId());
        });
        Map<Long, SysUser> userMap = loadUsers(userIds);

        List<ReviewExportExcelVO> rows = new ArrayList<>(records.size());
        for (ReviewRecord record : records) {
            PaperInfo paper = paperMap.get(record.getPaperId());
            SysUser student = paper == null ? null : userMap.get(paper.getStudentId());
            SysUser advisor = paper == null ? null : userMap.get(paper.getTeacherId());
            Long auditorId = record.getAuditorId() != null ? record.getAuditorId() : record.getTeacherId();
            SysUser auditor = userMap.get(auditorId);

            ReviewExportExcelVO row = new ReviewExportExcelVO();
            row.setPaperTitle(paper == null ? null : paper.getPaperTitle());
            row.setStudentName(student == null ? null : student.getRealName());
            row.setStudentNo(student == null ? null : student.getUsername());
            row.setAdvisorName(advisor == null ? null : advisor.getRealName());
            row.setReviewResult(reviewResultLabel(record.getReviewStatus()));
            row.setReviewOpinion(record.getReviewOpinion());
            row.setAuditSource(auditSourceLabel(record.getAuditSource()));
            row.setProxyReason(record.getProxyReason());
            row.setAuditorName(auditor == null ? null : auditor.getRealName());
            row.setReviewTime(record.getReviewTime() == null ? null : record.getReviewTime().format(TIME_FORMATTER));
            rows.add(row);
        }
        return rows;
    }

    private List<DefenseConclusionExcelVO> buildDefenseRows(SemesterUtils.Range range) {
        LambdaQueryWrapper<DefenseBatch> batchWrapper = new LambdaQueryWrapper<DefenseBatch>()
                .eq(DefenseBatch::getIsDeleted, 0);
        if (range.start() != null) {
            batchWrapper.ge(DefenseBatch::getDefenseDate, range.start());
        }
        if (range.endExclusive() != null) {
            batchWrapper.lt(DefenseBatch::getDefenseDate, range.endExclusive());
        }
        List<DefenseBatch> batches = defenseBatchMapper.selectList(batchWrapper);
        if (batches.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, DefenseBatch> batchMap = batches.stream()
                .collect(Collectors.toMap(DefenseBatch::getId, b -> b));
        List<Long> batchIds = new ArrayList<>(batchMap.keySet());
        List<DefenseRecord> records = defenseRecordMapper.selectList(
                new LambdaQueryWrapper<DefenseRecord>()
                        .eq(DefenseRecord::getIsDeleted, 0)
                        .in(DefenseRecord::getBatchId, batchIds)
                        .last("LIMIT " + EXPORT_MAX_ROWS));
        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> paperIds = records.stream().map(DefenseRecord::getPaperId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, PaperInfo> paperMap = paperIds.isEmpty() ? Collections.emptyMap()
                : paperInfoMapper.selectBatchIds(paperIds).stream()
                        .collect(Collectors.toMap(PaperInfo::getId, p -> p));

        List<Long> userIds = new ArrayList<>();
        records.forEach(r -> {
            if (r.getStudentId() != null) userIds.add(r.getStudentId());
        });
        batches.forEach(b -> {
            if (b.getLeaderId() != null) userIds.add(b.getLeaderId());
        });
        Map<Long, SysUser> userMap = loadUsers(userIds);

        List<DefenseConclusionExcelVO> rows = new ArrayList<>(records.size());
        for (DefenseRecord record : records) {
            DefenseBatch batch = batchMap.get(record.getBatchId());
            PaperInfo paper = paperMap.get(record.getPaperId());
            SysUser student = userMap.get(record.getStudentId());
            SysUser leader = batch == null ? null : userMap.get(batch.getLeaderId());

            DefenseConclusionExcelVO row = new DefenseConclusionExcelVO();
            row.setBatchNo(batch == null ? null : batch.getBatchNo());
            row.setBatchType(batchTypeLabel(batch == null ? null : batch.getBatchType()));
            row.setDefenseDate(batch == null || batch.getDefenseDate() == null
                    ? null : batch.getDefenseDate().format(TIME_FORMATTER));
            row.setStudentName(student == null ? null : student.getRealName());
            row.setPaperTitle(paper == null ? null : paper.getPaperTitle());
            row.setResult(defenseResultLabel(record.getResult()));
            row.setScore(record.getScore() == null ? null : record.getScore().toPlainString());
            row.setGrade(record.getGrade());
            row.setLeaderName(leader == null ? null : leader.getRealName());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, SysUser> loadUsers(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return new HashMap<>();
        }
        return sysUserMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private void writeExcel(HttpServletResponse response, String baseName, String semester,
                            List<?> rows, Class<?> clazz) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String suffix = semester == null || semester.isBlank() ? "" : "_" + semester;
            String fileName = baseName + suffix + "_"
                    + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".xlsx";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            EasyExcel.write(response.getOutputStream(), clazz)
                    .sheet(baseName)
                    .doWrite(rows);
        } catch (Exception e) {
            throw new IllegalStateException("导出文件失败：" + e.getMessage(), e);
        }
    }

    private String reviewResultLabel(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 3 -> "通过";
            case 4 -> "退回";
            default -> String.valueOf(status);
        };
    }

    private String auditSourceLabel(String auditSource) {
        if (auditSource == null) {
            return "导师审核";
        }
        return switch (auditSource) {
            case "ADMIN_PROXY" -> "管理员代审";
            case "TEACHER" -> "导师审核";
            default -> auditSource;
        };
    }

    private String batchTypeLabel(String batchType) {
        if (batchType == null) {
            return null;
        }
        return switch (batchType) {
            case DefenseBatch.TYPE_MID_TERM -> "中期答辩";
            case DefenseBatch.TYPE_FINAL -> "终期答辩";
            default -> batchType;
        };
    }

    private String defenseResultLabel(String result) {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case DefenseRecord.RESULT_PASS -> "通过";
            case DefenseRecord.RESULT_FAIL -> "不通过";
            case DefenseRecord.RESULT_POSTPONE -> "缓辩";
            default -> result;
        };
    }
}
