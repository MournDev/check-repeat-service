package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.vo.PaperReSubmitReq;
import com.abin.checkrepeatservice.student.dto.StudentReviewDetailDTO;
import com.abin.checkrepeatservice.student.vo.StudentReviewQueryReq;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckRuleMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.StudentReviewService;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生端审核结果服务实现类
 */
@Slf4j
@Service
public class StudentReviewServiceImpl extends ServiceImpl<PaperInfoMapper, PaperInfo> implements StudentReviewService {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private ReviewRecordMapper reviewRecordMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CheckRuleMapper checkRuleMapper;

    // 论文存储根路径（复用之前的配置）
    @Value("${paper.upload.base-path:/data/paper-upload}")
    private String paperBasePath;

    // 审核附件存储根路径（复用之前的配置）
    @Value("${review.attach.base-path:/data/review-attach/}")
    private String reviewAttachBasePath;

    // 支持的论文格式（复用Tika工具类的配置）
    private static final Set<String> SUPPORTED_PAPER_TYPES = Set.of("doc", "docx", "pdf");

    @Override
    public Result<Page<StudentReviewDetailDTO>> getMyReviewList(StudentReviewQueryReq queryReq) {
        Long currentStudentId = UserBusinessInfoUtils.getCurrentUserId();
        Integer currentPage = queryReq.getCurrentPage();
        Integer pageSize = queryReq.getPageSize();
        Integer paperStatus = queryReq.getPaperStatus();

        // 1. 构建分页查询条件（仅查询当前学生的论文）
        Page<PaperInfo> paperPage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
        paperWrapper.eq(PaperInfo::getStudentId, currentStudentId)
                .eq(PaperInfo::getIsDeleted, 0);

        // 2. 按论文状态过滤（可选：2-待审核，3-通过，4-不通过）
        if (paperStatus != null && Arrays.asList(2, 3, 4).contains(paperStatus)) {
            paperWrapper.eq(PaperInfo::getPaperStatus, paperStatus);
        }

        // 3. 执行分页查询（按提交时间倒序，最新的在前）
        IPage<PaperInfo> paperIPage = baseMapper.selectPage(paperPage, paperWrapper);
        List<PaperInfo> paperList = paperIPage.getRecords();
        if (CollectionUtils.isEmpty(paperList)) {
            return Result.success("审核结果列表查询成功", new Page<>());
        }

        // 4. 批量转换为学生端DTO（减少SQL查询）
        List<StudentReviewDetailDTO> detailDTOList = convertToStudentReviewDTO(paperList);

        // 5. 构建分页结果
        Page<StudentReviewDetailDTO> pageInfo = new Page<>(
        );

        return Result.success("审核结果列表查询成功", pageInfo);
    }

    @Override
    public Result<StudentReviewDetailDTO> getReviewDetail(@RequestParam("paperId") Long paperId) {
        Long currentStudentId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 校验论文合法性与权限
        PaperInfo paperInfo = baseMapper.selectById(paperId);
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        // 仅论文所属学生可查看
        if (!paperInfo.getStudentId().equals(currentStudentId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限查看他人论文的审核详情");
        }

        // 2. 转换为DTO（含完整信息）
        List<StudentReviewDetailDTO> dtoList = convertToStudentReviewDTO(Collections.singletonList(paperInfo));
        if (CollectionUtils.isEmpty(dtoList)) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "未找到该论文的审核信息");
        }

        return Result.success("审核详情查询成功", dtoList.get(0));
    }

    @Override
    public void downloadReviewAttach(@RequestParam("attachPath") String attachPath, HttpServletResponse response) {
        // 1. 校验附件路径合法性（防路径遍历攻击）
        if (!StringUtils.hasText(attachPath) || !attachPath.startsWith(reviewAttachBasePath)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "附件路径不合法");
        }

        // 2. 校验附件是否存在
        java.nio.file.Path filePath = Paths.get(attachPath);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "审核附件不存在或已删除");
        }

        // 3. 设置响应头并输出文件流（复用教师端附件下载逻辑，确保一致性）
        try {
            // 提取附件原文件名（简化处理，实际应从审核记录中获取）
            String fileName = "审核附件_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + "." + attachPath.substring(attachPath.lastIndexOf(".") + 1);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());

            // 设置响应头
            response.setContentType(Files.probeContentType(filePath));
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("Content-Length", String.valueOf(Files.size(filePath)));

            // 输出文件流
            try (InputStream in = Files.newInputStream(filePath);
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            log.info("学生下载审核附件成功：{}", attachPath);
        } catch (IOException e) {
            log.error("审核附件下载失败（路径：{}）：", attachPath, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "附件下载异常，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> reSubmitPaper(PaperReSubmitReq reSubmitReq) {
        Long currentStudentId = UserBusinessInfoUtils.getCurrentUserId();
        Long originalPaperId = reSubmitReq.getOriginalPaperId();
        MultipartFile revisedFile = reSubmitReq.getRevisedFile();
        String revisionDesc = reSubmitReq.getRevisionDesc();

        // 1. 基础校验
        // 1.1 校验原论文状态（仅审核不通过的论文可重新提交）
        PaperInfo originalPaper = baseMapper.selectById(originalPaperId);
        if (originalPaper == null || originalPaper.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "原论文不存在或已删除");
        }
        if (!originalPaper.getStudentId().equals(currentStudentId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限重新提交他人论文");
        }
        if (originalPaper.getPaperStatus()!= DictConstants.CheckStatus.FAILURE) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS,
                    String.format("仅审核不通过（状态4）的论文可重新提交，当前状态：%s", getPaperStatusDesc(Integer.valueOf(originalPaper.getPaperStatus()))));
        }

        // 1.2 校验修改后文件
        if (revisedFile == null || revisedFile.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "修改后的论文文件不能为空");
        }
        // 校验文件格式
        String originalFileName = revisedFile.getOriginalFilename();
        String fileType = originalFileName.substring(originalFileName.lastIndexOf(".") + 1).toLowerCase();
        if (!SUPPORTED_PAPER_TYPES.contains(fileType)) {
            return Result.error(ResultCode.PARAM_ERROR,
                    String.format("仅支持%s格式的论文文件，当前格式：%s", String.join(",", SUPPORTED_PAPER_TYPES), fileType));
        }
        // 校验文件大小（200MB）
        long maxSize = 200 * 1024 * 1024;
        if (revisedFile.getSize() > maxSize) {
            return Result.error(ResultCode.PARAM_ERROR,
                    String.format("论文文件大小不能超过200MB，当前大小：%.2fMB", revisedFile.getSize() / 1024.0 / 1024.0));
        }

        // 2. 保存修改后的论文文件
        String revisedPaperPath = saveRevisedPaperFile(revisedFile, currentStudentId, originalPaperId);

        // 3. 生成新的论文记录（原论文保留，新增重新提交记录）
        PaperInfo revisedPaper = new PaperInfo();
        // 基础信息（复用原论文的标题、教师ID等）
        revisedPaper.setPaperTitle(originalPaper.getPaperTitle() + "(修改版_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ")");
        revisedPaper.setStudentId(currentStudentId);
        // 计算文件MD5（防重复上传）
        String fileMd5;
        try {
            fileMd5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(revisedFile.getInputStream());
        } catch (IOException e) {
            log.error("计算修改后论文MD5失败：", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件校验失败，请重试");
        }
        revisedPaper.setFileMd5(fileMd5);
        // 状态：0-待查重，提交时间：当前时间
        revisedPaper.setSubmitTime(LocalDateTime.now());
        revisedPaper.setPaperStatus(DictConstants.PaperStatus.PENDING);
        // 填充审计字段（创建人=当前学生）
        UserBusinessInfoUtils.setAuditField(revisedPaper, true);
        baseMapper.insert(revisedPaper);

        // 4. 记录修改说明（可选：可新增re_submit_record表，此处简化为在原论文备注中记录）
        baseMapper.updateById(originalPaper);

        // 5. 构建返回结果
        Map<String, Object> resultMap = new HashMap<>(4);
        resultMap.put("revisedPaperId", revisedPaper.getId());
        resultMap.put("reSubmitTime", revisedPaper.getSubmitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        resultMap.put("currentStatus", revisedPaper.getPaperStatus());

        log.info("学生重新提交论文成功：原论文ID={}，新论文ID={}，学生ID={}",
                originalPaperId, revisedPaper.getId(), currentStudentId);
        return Result.success("重新提交成功，论文已进入待查重队列", resultMap);
    }

    @Override
    public Result<Page<PaperInfo>> getResubmitRecord(@RequestParam("originalPaperId") Long originalPaperId,
                                                         @RequestParam("currentPage") Integer currentPage,
                                                         @RequestParam("pageSize") Integer pageSize) {
        Long currentStudentId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 校验原论文权限
        PaperInfo originalPaper = baseMapper.selectById(originalPaperId);
        if (originalPaper == null || originalPaper.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "原论文不存在或已删除");
        }
        if (!originalPaper.getStudentId().equals(currentStudentId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限查看他人论文的重新提交记录");
        }

        // 2. 查询重新提交记录（通过标题中的“修改版”标识，或通过关联表，此处简化）
        Page<PaperInfo> paperPage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getStudentId, currentStudentId)
                .like(PaperInfo::getPaperTitle, "修改版")
                .eq(PaperInfo::getIsDeleted, 0)
                .orderByDesc(PaperInfo::getSubmitTime);

        IPage<PaperInfo> paperIPage = baseMapper.selectPage(paperPage, wrapper);
        Page<PaperInfo> pageInfo = new Page<>(
        );

        return Result.success("重新提交记录查询成功", pageInfo);
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 批量转换PaperInfo列表为学生端审核结果DTO
     */
    private List<StudentReviewDetailDTO> convertToStudentReviewDTO(List<PaperInfo> paperList) {
        if (CollectionUtils.isEmpty(paperList)) {
            return new ArrayList<>();
        }

        // 1. 批量查询关联数据（任务、报告、审核记录、教师信息）
        // 1.1 提取论文ID列表
        List<Long> paperIds = paperList.stream().map(PaperInfo::getId).collect(Collectors.toList());

        // 1.2 批量查询查重任务（Map<论文ID, 最新成功任务>）
        Map<Long, CheckTask> taskMap = new HashMap<>();
        List<CheckTask> taskList = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                        .in(CheckTask::getPaperId, paperIds)
                        .eq(CheckTask::getCheckStatus, 2) // 仅成功任务
                        .eq(CheckTask::getIsDeleted, 0)
        );
        Map<Long, List<CheckTask>> taskGroup = taskList.stream()
                .collect(Collectors.groupingBy(CheckTask::getPaperId));
        for (Map.Entry<Long, List<CheckTask>> entry : taskGroup.entrySet()) {
            CheckTask latestTask = entry.getValue().stream()
                    .sorted((t1, t2) -> t2.getEndTime().compareTo(t1.getEndTime()))
                    .findFirst()
                    .orElse(null);
            taskMap.put(entry.getKey(), latestTask);
        }

        // 1.3 批量查询审核记录（Map<论文ID, 最新审核记录>）
        Map<Long, ReviewRecord> reviewMap = new HashMap<>();
        List<ReviewRecord> reviewList = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<ReviewRecord>()
                        .in(ReviewRecord::getPaperId, paperIds)
                        .eq(ReviewRecord::getIsDeleted, 0)
        );
        Map<Long, List<ReviewRecord>> reviewGroup = reviewList.stream()
                .collect(Collectors.groupingBy(ReviewRecord::getPaperId));
        for (Map.Entry<Long, List<ReviewRecord>> entry : reviewGroup.entrySet()) {
            ReviewRecord latestReview = entry.getValue().stream()
                    .sorted((r1, r2) -> r2.getReviewTime().compareTo(r1.getReviewTime()))
                    .findFirst()
                    .orElse(null);
            reviewMap.put(entry.getKey(), latestReview);
        }

//        // 1.4 批量查询教师信息（Map<教师ID, 教师实体>）
//        Set<Long> teacherIds = paperList.stream()
//                .map(PaperInfo::getTeacherId)
//                .distinct()
//                .collect(Collectors.toSet());
//        Map<Long, SysUser> teacherMap = sysUserMapper.selectBatchIds(teacherIds).stream()
//                .collect(Collectors.toMap(SysUser::getId, teacher -> teacher));

        // 2. 转换为DTO
        return paperList.stream().map(paper -> {
            StudentReviewDetailDTO dto = new StudentReviewDetailDTO();

            // 2.1 填充论文基础信息
            StudentReviewDetailDTO.PaperBasicDTO paperBasic = new StudentReviewDetailDTO.PaperBasicDTO();
            paperBasic.setPaperId(paper.getId());
            paperBasic.setPaperTitle(paper.getPaperTitle());
            paperBasic.setSubmitTime(paper.getSubmitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            // 填充教师姓名
//            if (teacher != null) {
//                paperBasic.setTeacherName(teacher.getRealName());
//            }
            dto.setPaperBasic(paperBasic);

            // 2.2 填充查重核心结果
            CheckTask task = taskMap.get(paper.getId());
            if (task != null) {
                StudentReviewDetailDTO.CheckCoreDTO checkCore = new StudentReviewDetailDTO.CheckCoreDTO();
                checkCore.setCheckRate(task.getCheckRate() != null ? task.getCheckRate().doubleValue() : 0.0);
//                checkCore.setCheckTime(task.getEndTime().format("yyyy-MM-dd HH:mm:ss"));
                // 构建查重报告下载URL
                CheckReport report = checkReportMapper.selectById(task.getReportId());
                if (report != null) {
                    String reportDownloadUrl = "/api/v1/student/reports/download?reportId=" + report.getId();
                    checkCore.setReportDownloadUrl(reportDownloadUrl);
                }
                dto.setCheckCore(checkCore);
            }

            // 2.3 填充审核结果信息
            ReviewRecord review = reviewMap.get(paper.getId());
            if (review != null) {
                StudentReviewDetailDTO.ReviewResultDTO reviewResult = new StudentReviewDetailDTO.ReviewResultDTO();
                reviewResult.setReviewStatusDesc(review.getReviewStatus().equals(DictConstants.PaperStatus.COMPLETED) ? "审核通过" : "审核不通过");
                reviewResult.setReviewTime(review.getReviewTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                // 审核意见（已清洗XSS，直接返回）
                reviewResult.setReviewOpinion(review.getReviewOpinion());

                // 填充审核附件（若有）
                String attachPath = review.getReviewAttach();
                if (StringUtils.hasText(attachPath) && Files.exists(Paths.get(attachPath))) {
                    StudentReviewDetailDTO.ReviewAttachDTO attachDTO = new StudentReviewDetailDTO.ReviewAttachDTO();
                    attachDTO.setAttachName("审核附件." + attachPath.substring(attachPath.lastIndexOf(".") + 1));
                    try {
                        attachDTO.setAttachSize(Files.size(Paths.get(attachPath)) / 1024); // 转换为KB
                    } catch (IOException e) {
                        attachDTO.setAttachSize(0L);
                    }
                    // 构建附件下载URL
                    String attachDownloadUrl = "/api/v1/student/reviews/download-attach?attachPath=" + URLEncoder.encode(attachPath, StandardCharsets.UTF_8);
                    attachDTO.setDownloadUrl(attachDownloadUrl);
                    reviewResult.setAttachInfo(attachDTO);
                }

                dto.setReviewResult(reviewResult);
            }

            // 2.4 填充最新重新提交记录（若有）
            StudentReviewDetailDTO.LatestResubmitDTO latestResubmit = getLatestResubmitRecord(paper.getId());
            if (latestResubmit != null) {
                dto.setLatestResubmit(latestResubmit);
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 获取论文状态描述
     */
    private String getPaperStatusDesc(Integer status) {
        return switch (status) {
            case 0 -> "待查重";
            case 1 -> "查重中";
            case 2 -> "待审核";
            case 3 -> "审核通过";
            case 4 -> "审核不通过";
            default -> "未知状态";
        };
    }

    /**
     * 保存修改后的论文文件（按学生ID+原论文ID分目录）
     */
    private String saveRevisedPaperFile(MultipartFile file, Long studentId, Long originalPaperId) {
        try {
            // 构建存储路径：basePath/student_xxx/original_xxx/yyyyMMdd/
            String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String studentDir = "student_" + studentId;
            String originalDir = "original_" + originalPaperId;
            java.nio.file.Path saveDirPath = Paths.get(paperBasePath, studentDir, originalDir, dateDir);
            java.io.File saveDir = saveDirPath.toFile();
            if (!saveDir.exists() && !saveDir.mkdirs()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "创建修改论文存储目录失败");
            }

            // 生成唯一文件名（UUID+原后缀）
            String originalFileName = file.getOriginalFilename();
            String fileSuffix = originalFileName.substring(originalFileName.lastIndexOf(".") + 1);
            String uniqueFileName = UUID.randomUUID().toString() + "." + fileSuffix;
            java.nio.file.Path saveFilePath = Paths.get(saveDirPath.toString(), uniqueFileName);

            // 保存文件
            file.transferTo(saveFilePath);
            log.info("修改后论文文件保存成功：{}", saveFilePath);
            return saveFilePath.toString();
        } catch (IOException e) {
            log.error("修改后论文文件保存失败：", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件保存异常，请重试");
        }
    }

    /**
     * 获取原论文的最新重新提交记录
     */
    private StudentReviewDetailDTO.LatestResubmitDTO getLatestResubmitRecord(Long originalPaperId) {
        // 查询标题含“修改版”且备注含原论文ID的最新论文
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(PaperInfo::getPaperTitle, "修改版")
//                .like(PaperInfo::getRemark, "原论文ID：" + originalPaperId)
                .eq(PaperInfo::getIsDeleted, 0)
                .orderByDesc(PaperInfo::getSubmitTime)
                .last("LIMIT 1");

        PaperInfo resubmitPaper = baseMapper.selectOne(wrapper);
        if (resubmitPaper == null) {
            return null;
        }

        // 转换为DTO
        StudentReviewDetailDTO.LatestResubmitDTO resubmitDTO = new StudentReviewDetailDTO.LatestResubmitDTO();
        resubmitDTO.setResubmitPaperId(resubmitPaper.getId());
        resubmitDTO.setResubmitTime(resubmitPaper.getSubmitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        // 提取修改说明（从备注中截取）
//        String remark = resubmitPaper.getRemark();
//        if (StringUtils.hasText(remark) && remark.contains("修改说明：")) {
//            String revisionDesc = remark.substring(remark.indexOf("修改说明：") + 5);
//            resubmitDTO.setRevisionDesc(revisionDesc);
//        } else {
//            resubmitDTO.setRevisionDesc("无");
//        }

        return resubmitDTO;
    }
}
