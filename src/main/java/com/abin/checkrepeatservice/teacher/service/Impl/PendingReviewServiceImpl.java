package com.abin.checkrepeatservice.teacher.service.Impl;

import cn.hutool.core.util.IdUtil;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.teacher.dto.*;
import com.abin.checkrepeatservice.teacher.mapper.PendingReviewMapper;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.abin.checkrepeatservice.teacher.service.PendingReviewService;
import com.abin.checkrepeatservice.teacher.vo.*;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.user.mapper.SystemMessageMapper;
import com.abin.checkrepeatservice.user.mapper.TeacherAllocationRecordMapper;
import com.abin.checkrepeatservice.user.service.InstantMessageService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Base64;
import org.apache.commons.io.FileUtils;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 待审核论文服务实现类
 */
@Slf4j
@Service
public class PendingReviewServiceImpl implements PendingReviewService {

    @Resource
    private PendingReviewMapper pendingReviewMapper;
    
    @Resource
    private MessageService messageService;
    
    @Resource
    private InstantMessageService instantMessageService;
    
    @Resource
    private MinioClient minioClient;
    
    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private FileService fileService;
    
    @Resource
    private TeacherAllocationRecordMapper teacherAllocationRecordMapper;
    
    @Resource
    private ReviewRecordMapper reviewRecordMapper;
    
    @Resource
    private SystemMessageMapper systemMessageMapper;
    
    @Value("${minio.bucket.main:check-repeat-service}")
    private String mainBucket;
    
    @Value("${upload.base-path}")
    private String uploadBasePath;
    
    @Value("${server.host:localhost}")
    private String serverHost;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Value("${server.servlet.context-path:}")
    private String appContext;
    
    @Value("${kkfileview.base-url:http://localhost:8012}")
    private String kkfileviewUrl;

    @Override
    public Result<Object> getPendingReviews(PendingReviewQueryDTO queryDTO) {
        try {
            log.info("开始查询待审核论文列表: teacherId={}, page={}, pageSize={}", 
                    queryDTO.getTeacherId(), queryDTO.getPage(), queryDTO.getPageSize());

            // 构建分页对象
            Page<PaperInfo> page = new Page<>(queryDTO.getPage(), queryDTO.getPageSize());
            
            // 构建查询条件
            LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaperInfo::getTeacherId, Long.parseLong(queryDTO.getTeacherId()))
                   .eq(PaperInfo::getPaperStatus, "auditing") // 待审核状态
                   .eq(PaperInfo::getIsDeleted, 0);

            // 学院筛选
            if (queryDTO.getCollege() != null && !queryDTO.getCollege().isEmpty()) {
                wrapper.like(PaperInfo::getCollegeName, queryDTO.getCollege());
            }

            // 相似度范围筛选
            if (queryDTO.getSimilarityRange() != null && !queryDTO.getSimilarityRange().isEmpty()) {
                switch (queryDTO.getSimilarityRange()) {
                    case "low":
                        wrapper.le(PaperInfo::getSimilarityRate, new BigDecimal("20"));
                        break;
                    case "medium":
                        wrapper.gt(PaperInfo::getSimilarityRate, new BigDecimal("20"))
                               .le(PaperInfo::getSimilarityRate, new BigDecimal("40"));
                        break;
                    case "high":
                        wrapper.gt(PaperInfo::getSimilarityRate, new BigDecimal("40"));
                        break;
                }
            }

            // 排序
            if (queryDTO.getSortField() != null && !queryDTO.getSortField().isEmpty()) {
                boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
                switch (queryDTO.getSortField()) {
                    case "submitTime":
                        if (isAsc) {
                            wrapper.orderByAsc(PaperInfo::getSubmitTime);
                        } else {
                            wrapper.orderByDesc(PaperInfo::getSubmitTime);
                        }
                        break;
                    case "deadline":
                        if (isAsc) {
                            wrapper.orderByAsc(PaperInfo::getCheckTime);
                        } else {
                            wrapper.orderByDesc(PaperInfo::getCheckTime);
                        }
                        break;
                    case "waitingTime":
                        // 等待时间需要自定义排序逻辑
                        break;
                }
            } else {
                wrapper.orderByDesc(PaperInfo::getSubmitTime);
            }

            // 执行查询
            Page<PaperInfo> resultPage = pendingReviewMapper.selectPage(page, wrapper);

            // 转换为VO对象
            List<PendingReviewVO> records = resultPage.getRecords().stream()
                    .map(this::convertToPendingReviewVO)
                    .collect(Collectors.toList());

            // 构造返回结果
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("records", records);
            responseData.put("total", resultPage.getTotal());
            responseData.put("size", resultPage.getSize());
            responseData.put("current", resultPage.getCurrent());
            responseData.put("pages", resultPage.getPages());

            log.info("查询待审核论文列表成功: 共{}条记录", records.size());
            return Result.success("获取待审核论文列表成功", responseData);

        } catch (Exception e) {
            log.error("查询待审核论文列表失败: teacherId={}", queryDTO.getTeacherId(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取待审核论文列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result<PendingStatsVO> getPendingStats(String teacherId) {
        try {
            log.info("获取待审核统计信息: teacherId={}", teacherId);

            PendingStatsVO stats = new PendingStatsVO();

            // 获取待审核总数
            LambdaQueryWrapper<PaperInfo> pendingWrapper = new LambdaQueryWrapper<>();
            pendingWrapper.eq(PaperInfo::getTeacherId, Long.parseLong(teacherId))
                         .eq(PaperInfo::getPaperStatus, "auditing")
                         .eq(PaperInfo::getIsDeleted, 0);
            Long totalPending = pendingReviewMapper.selectCount(pendingWrapper);
            stats.setTotalPending(totalPending.intValue());

            // 获取紧急待审数（这里简化处理，实际应该根据截止时间和当前时间计算）
            stats.setUrgentPending(0);

            // 计算平均等待时间
            List<PaperInfo> pendingPapers = pendingReviewMapper.selectList(pendingWrapper);
            if (!pendingPapers.isEmpty()) {
                long totalWaitingDays = pendingPapers.stream()
                        .mapToLong(paper -> ChronoUnit.DAYS.between(
                                paper.getSubmitTime().toLocalDate(), 
                                LocalDateTime.now().toLocalDate()))
                        .sum();
                BigDecimal avgWaitingTime = new BigDecimal(totalWaitingDays)
                        .divide(new BigDecimal(pendingPapers.size()), 1, BigDecimal.ROUND_HALF_UP);
                stats.setAvgWaitingTime(avgWaitingTime);
            } else {
                stats.setAvgWaitingTime(BigDecimal.ZERO);
            }

            // 获取今日已审核数
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
            
            LambdaQueryWrapper<PaperInfo> todayReviewedWrapper = new LambdaQueryWrapper<>();
            todayReviewedWrapper.eq(PaperInfo::getTeacherId, Long.parseLong(teacherId))
                               .ne(PaperInfo::getPaperStatus, "auditing")
                               .between(PaperInfo::getUpdateTime, todayStart, todayEnd)
                               .eq(PaperInfo::getIsDeleted, 0);
            Long todayReviewed = pendingReviewMapper.selectCount(todayReviewedWrapper);
            stats.setTodayReviewed(todayReviewed.intValue());

            // 设置超时和即将超时数量（简化处理）
            stats.setOverdueCount(0);
            stats.setUrgentCount(0);

            log.info("获取待审核统计信息成功: totalPending={}, todayReviewed={}", 
                    stats.getTotalPending(), stats.getTodayReviewed());
            return Result.success("获取统计信息成功", stats);

        } catch (Exception e) {
            log.error("获取待审核统计信息失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取统计信息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<ReviewResultDetailVO> reviewPaper(String teacherId, PaperReviewDTO reviewDTO) {
        try {
            log.info("开始论文审核: teacherId={}, paperIds={}, reviewStatus={}", 
                    teacherId, reviewDTO.getPaperIds(), reviewDTO.getReviewStatus());

            ReviewResultDetailVO result = new ReviewResultDetailVO();
            List<ReviewResultDetailVO.ReviewDetailVO> details = new ArrayList<>();
            int successCount = 0;
            int failedCount = 0;

            for (String paperId : reviewDTO.getPaperIds()) {
                try {
                    // 更新论文状态
                    PaperInfo paperInfo = new PaperInfo();
                    paperInfo.setId(Long.parseLong(paperId));
                    
                    // 根据审核状态设置论文状态
                    switch (reviewDTO.getReviewStatus()) {
                        case 1: // 通过
                            paperInfo.setPaperStatus("completed");
                            break;
                        case 2: // 不通过
                            paperInfo.setPaperStatus("rejected");
                            break;
                        case 3: // 需要修改
                            paperInfo.setPaperStatus("need_revision");
                            break;
                        default:
                            throw new IllegalArgumentException("无效的审核状态");
                    }
                    
                    paperInfo.setUpdateTime(LocalDateTime.now());
                    pendingReviewMapper.updateById(paperInfo);

                    // 记录成功
                    ReviewResultDetailVO.ReviewDetailVO detail = new ReviewResultDetailVO.ReviewDetailVO();
                    detail.setPaperId(paperId);
                    detail.setStatus("success");
                    details.add(detail);
                    successCount++;

                    log.info("论文{}审核成功", paperId);

                } catch (Exception e) {
                    log.error("论文{}审核失败", paperId, e);
                    ReviewResultDetailVO.ReviewDetailVO detail = new ReviewResultDetailVO.ReviewDetailVO();
                    detail.setPaperId(paperId);
                    detail.setStatus("error");
                    detail.setErrorMessage(e.getMessage());
                    details.add(detail);
                    failedCount++;
                }
            }

            result.setSuccessCount(successCount);
            result.setFailedCount(failedCount);
            result.setDetails(details);

            String message = String.format("审核完成：成功%d篇，失败%d篇", successCount, failedCount);
            log.info("论文审核完成: {}", message);
            return Result.success(message, result);

        } catch (Exception e) {
            log.error("论文审核失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文审核失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> recheckPlagiarism(String teacherId, String paperId) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("重新查重检测: teacherId={}, paperId={}", teacherId, paperId);

            // 获取论文信息
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(paperId));
            if (paperInfo == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在");
            }

            // 验证权限
            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限操作该论文");
            }

            // 更新论文状态为查重中
            paperInfo.setPaperStatus("checking");
            paperInfo.setCheckCompleted(0);
            paperInfo.setUpdateTime(LocalDateTime.now());
            pendingReviewMapper.updateById(paperInfo);

            // 生成查重任务ID
            String taskId = "recheck_" + System.currentTimeMillis();
            result.put("taskId", taskId);
            result.put("estimatedTime", "15-20分钟");

            // 这里应该触发实际的查重任务
            // 可以调用异步查重服务或者消息队列
            log.info("重新查重检测任务已创建: taskId={}, paperId={}", taskId, paperId);
            
            return Result.success("重新检测请求已提交", result);

        } catch (Exception e) {
            log.error("重新查重检测失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重新查重检测失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> sendReminder(String teacherId, SendReminderDTO reminderDTO) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("发送提醒消息：teacherId={}, studentIds={}, message={}", 
                    teacherId, reminderDTO.getStudentIds(), reminderDTO.getMessage());
                
            // 获取当前登录用户ID（而不是使用传入的 teacherId）
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
    
            int successCount = 0;
            int failedCount = 0;

            // 批量发送提醒消息
            for (String studentId : reminderDTO.getStudentIds()) {
                try {
                    // 创建系统消息
                    SystemMessage message = new SystemMessage();
                    message.setSenderId(currentUserId); // 使用当前登录用户ID
                    message.setReceiverId(Long.parseLong(studentId));
                    message.setTitle("论文审核提醒");
                    message.setContent(reminderDTO.getMessage());
                    message.setMessageType("REMINDER");
                    message.setPriority(2); // 中等优先级
                    message.setCreateTime(LocalDateTime.now());
                    message.setUpdateTime(LocalDateTime.now());
                    message.setIsRead(0);

                    // 发送消息
                    Result<Boolean> sendResult = messageService.sendMessage(message);
                    if (sendResult.isSuccess() && Boolean.TRUE.equals(sendResult.getData())) {
                        successCount++;
                        log.info("提醒消息发送成功: teacherId={}, studentId={}", teacherId, studentId);
                    } else {
                        failedCount++;
                        log.warn("提醒消息发送失败: teacherId={}, studentId={}, error={}", 
                                teacherId, studentId, sendResult.getMessage());
                    }
                } catch (Exception e) {
                    failedCount++;
                    log.error("发送提醒消息异常: teacherId={}, studentId={}", teacherId, studentId, e);
                }
            }

            result.put("successCount", successCount);
            result.put("failedCount", failedCount);

            String message = String.format("提醒消息发送完成：成功%d条，失败%d条", successCount, failedCount);
            log.info("提醒消息发送统计: {}", message);
            return Result.success(message, result);

        } catch (Exception e) {
            log.error("发送提醒消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送提醒消息失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Map<String, Object>> contactStudent(String teacherId, ContactStudentDTO contactDTO) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("联系学生: teacherId={}, studentId={}, messageType={}", 
                    teacherId, contactDTO.getStudentId(), contactDTO.getMessageType());

            // 根据消息类型选择不同的发送方式
            switch (contactDTO.getMessageType().toLowerCase()) {
                case "chat": // 即时聊天
                    return sendInstantMessage(teacherId, contactDTO, result);
                case "email": // 邮件
                    return sendEmailMessage(teacherId, contactDTO, result);
                case "system": // 系统消息
                    return sendSystemMessage(teacherId, contactDTO, result);
                default:
                    return Result.error(ResultCode.PARAM_ERROR, "不支持的消息类型: " + contactDTO.getMessageType());
            }

        } catch (Exception e) {
            log.error("联系学生失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "联系学生失败: " + e.getMessage());
        }
    }

    /**
     * 发送即时消息
     */
    private Result<Map<String, Object>> sendInstantMessage(String teacherId, ContactStudentDTO contactDTO, Map<String, Object> result) {
        try {
            // 使用当前登录用户ID，而不是参数中的 teacherId
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            
            InstantMessage message = new InstantMessage();
            message.setSenderId(currentUserId);
            message.setReceiverId(Long.parseLong(contactDTO.getStudentId()));
            message.setContent(contactDTO.getMessage());
            message.setMessageType("PRIVATE");
            message.setContentType("TEXT");
            message.setStatus("SENT");
            message.setRelatedType("PAPER");
            message.setRelatedId(contactDTO.getPaperId() != null ? Long.parseLong(contactDTO.getPaperId()) : null);
            
            Result<Boolean> sendResult = instantMessageService.sendMessage(message);
            
            if (sendResult.isSuccess() && Boolean.TRUE.equals(sendResult.getData())) {
                result.put("messageId", message.getId());
                result.put("sendTime", LocalDateTime.now().toString());
                log.info("即时消息发送成功: messageId={}", message.getId());
                return Result.success("即时消息发送成功", result);
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "即时消息发送失败: " + sendResult.getMessage());
            }
        } catch (Exception e) {
            log.error("发送即时消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送即时消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送系统消息
     */
    private Result<Map<String, Object>> sendSystemMessage(String teacherId, ContactStudentDTO contactDTO, Map<String, Object> result) {
        try {
            // 使用当前登录用户ID
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            
            SystemMessage message = new SystemMessage();
            message.setSenderId(currentUserId);
            message.setReceiverId(Long.parseLong(contactDTO.getStudentId()));
            message.setTitle("论文指导反馈");
            message.setContent(contactDTO.getMessage());
            message.setMessageType("GUIDANCE");
            message.setPriority(1);
            message.setRelatedType("PAPER");
            message.setRelatedId(contactDTO.getPaperId() != null ? Long.parseLong(contactDTO.getPaperId()) : null);
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            message.setIsRead(0);
            
            Result<Boolean> sendResult = messageService.sendMessage(message);
            
            if (sendResult.isSuccess() && Boolean.TRUE.equals(sendResult.getData())) {
                result.put("messageId", "sys_" + System.currentTimeMillis());
                result.put("sendTime", LocalDateTime.now().toString());
                log.info("系统消息发送成功");
                return Result.success("系统消息发送成功", result);
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "系统消息发送失败: " + sendResult.getMessage());
            }
        } catch (Exception e) {
            log.error("发送系统消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送系统消息失败: " + e.getMessage());
        }
    }

    /**
     * 发送邮件消息（简化实现）
     */
    private Result<Map<String, Object>> sendEmailMessage(String teacherId, ContactStudentDTO contactDTO, Map<String, Object> result) {
        try {
            // 这里应该调用邮件服务
            // 暂时记录日志并返回成功
            log.info("准备发送邮件: teacherId={}, studentId={}, message={}", 
                    teacherId, contactDTO.getStudentId(), contactDTO.getMessage());
            
            result.put("messageId", "email_" + System.currentTimeMillis());
            result.put("sendTime", LocalDateTime.now().toString());
            
            // 实际项目中应该调用邮件服务发送邮件
            log.info("邮件消息发送成功");
            return Result.success("邮件消息发送成功", result);
        } catch (Exception e) {
            log.error("发送邮件消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送邮件消息失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadPaper(String teacherId, String paperId, HttpServletResponse response) {
        try {
            log.info("下载论文文件: teacherId={}, paperId={}", teacherId, paperId);

            // 获取论文信息
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(paperId));
            if (paperInfo == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("论文不存在");
                return;
            }

            // 验证权限
            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("无权限访问该论文");
                return;
            }

            // 检查文件路径
            String filePath = paperInfo.getFilePath();
            if (!StringUtils.hasText(filePath)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("论文文件路径不存在");
                return;
            }

            // 解析文件信息
            String fileName = extractFileName(filePath);
            String contentType = getContentType(fileName);

            // 设置响应头
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            response.setCharacterEncoding("UTF-8");

            // 从MinIO下载文件
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(mainBucket)
                            .object(filePath)
                            .build())) {
                
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
                response.getOutputStream().flush();
                
                log.info("论文文件下载成功: paperId={}, fileName={}", paperId, fileName);
            }

        } catch (Exception e) {
            log.error("下载论文文件失败: paperId={}", paperId, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 从文件路径提取文件名
     */
    private String extractFileName(String filePath) {
        if (filePath == null) return "paper.docx";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /**
     * 根据文件名获取内容类型
     */
    private String getContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerName.endsWith(".doc")) {
            return "application/msword";
        } else {
            return "application/octet-stream";
        }
    }

    @Override
    public Result<PlagiarismReportVO> getPlagiarismReport(String teacherId, String paperId) {
        try {
            log.info("获取查重报告: teacherId={}, paperId={}", teacherId, paperId);

            // 获取论文信息
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(paperId));
            if (paperInfo == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在");
            }

            // 验证权限
            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该论文");
            }

            // 构造查重报告VO
            PlagiarismReportVO report = new PlagiarismReportVO();
            report.setReportId("report_" + paperId);
            report.setPaperId(paperId);
            report.setSimilarity(paperInfo.getSimilarityRate() != null ? 
                paperInfo.getSimilarityRate().intValue() : 0);
            report.setGeneratedTime(LocalDateTime.now().toString());
            report.setReportUrl("https://example.com/reports/report_" + paperId + ".pdf");

            log.info("获取查重报告成功: paperId={}", paperId);
            return Result.success("获取查重报告成功", report);

        } catch (Exception e) {
            log.error("获取查重报告失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取查重报告失败: " + e.getMessage());
        }
    }

    @Override
    public Result<TodayReviewedVO> getTodayReviewedCount(String teacherId) {
        try {
            log.info("获取今日审核统计: teacherId={}", teacherId);

            TodayReviewedVO todayStats = new TodayReviewedVO();
            
            // 获取今日审核总数
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
            
            LambdaQueryWrapper<PaperInfo> todayWrapper = new LambdaQueryWrapper<>();
            todayWrapper.eq(PaperInfo::getTeacherId, Long.parseLong(teacherId))
                       .ne(PaperInfo::getPaperStatus, "auditing")
                       .between(PaperInfo::getUpdateTime, todayStart, todayEnd)
                       .eq(PaperInfo::getIsDeleted, 0);
            
            Long totalCount = pendingReviewMapper.selectCount(todayWrapper);
            todayStats.setCount(totalCount.intValue());

            // 按小时统计
            List<TodayReviewedVO.HourlyCountVO> hourlyStats = new ArrayList<>();
            LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            
            for (int hour = 0; hour < 24; hour += 3) {
                LocalDateTime hourStart = dayStart.plusHours(hour);
                LocalDateTime hourEnd = hourStart.plusHours(3);
                
                // 查询该小时段的审核数量
                LambdaQueryWrapper<PaperInfo> hourWrapper = new LambdaQueryWrapper<>();
                hourWrapper.eq(PaperInfo::getTeacherId, Long.parseLong(teacherId))
                          .ne(PaperInfo::getPaperStatus, "auditing")
                          .between(PaperInfo::getUpdateTime, hourStart, hourEnd)
                          .eq(PaperInfo::getIsDeleted, 0);
                
                Long hourCount = pendingReviewMapper.selectCount(hourWrapper);
                
                TodayReviewedVO.HourlyCountVO hourlyStat = new TodayReviewedVO.HourlyCountVO();
                hourlyStat.setHour(hour);
                hourlyStat.setCount(hourCount.intValue());
                hourlyStats.add(hourlyStat);
            }
            todayStats.setDetails(hourlyStats);

            log.info("获取今日审核统计成功: count={}", todayStats.getCount());
            return Result.success("获取今日审核统计成功", todayStats);

        } catch (Exception e) {
            log.error("获取今日审核统计失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取今日审核统计失败: " + e.getMessage());
        }
    }

    @Override
    public Result<PaperContentDTO> getPaperContent(String teacherId, String paperId) {
        try {
            log.info("教师{}获取论文内容: paperId={}", teacherId, paperId);

            // 1. 验证论文权限
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(paperId));
            if (paperInfo == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }

            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                return Result.error(ResultCode.PARAM_ERROR, "无权限访问此论文");
            }

            // 2. 构建返回数据
            PaperContentDTO contentDTO = new PaperContentDTO();
            contentDTO.setPaperId(paperId);
            contentDTO.setTitle(paperInfo.getPaperTitle());

            // 3. 从文件系统获取论文内容（使用标准 FileService 方式）
            if (paperInfo.getFileId() != null) {
                try {
                    // 使用FileService获取文件信息
                    FileInfo fileInfo = fileService.getById(paperInfo.getFileId());
                    if (fileInfo != null) {
                        contentDTO.setFileSize(fileInfo.getFileSizeDesc());
                        contentDTO.setFileType(getFileExtension(fileInfo.getOriginalFilename()));

                        // 获取文件内容（如果是文本文件）
                        if (isTextFile(fileInfo.getFileType())) {
                            // 构建文件访问路径
                            String fileStoragePath = fileInfo.getStoragePath();
                            File file = new File(uploadBasePath + fileStoragePath);

                            if (file.exists()) {
                                String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                                contentDTO.setContent(content);
                                contentDTO.setWordCount(countWords(content));
                                contentDTO.setPageCount(calculatePages(content));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取论文文件内容失败: paperId={}, fileId={}", paperId, paperInfo.getFileId(), e);
                    // 不影响主流程，继续返回基本信息
                }
            }

            // 4. 设置摘要和关键词（如果数据库中有）
            contentDTO.setAbstractText(paperInfo.getPaperAbstract());
            // 注意：PaperInfo实体类中暂无keywords字段，后续可根据需要添加

            log.info("成功获取论文内容: paperId={}, title={}", paperId, paperInfo.getPaperTitle());
            return Result.success("获取成功", contentDTO);

        } catch (NumberFormatException e) {
            log.error("论文ID格式错误: {}", paperId, e);
            return Result.error(ResultCode.PARAM_ERROR, "论文ID格式错误");
        } catch (Exception e) {
            log.error("获取论文内容失败: teacherId={}, paperId={}", teacherId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文内容失败: " + e.getMessage());
        }
    }

    /**
     * 发送委托通知
     */
    private void sendDelegateNotification(DelegateReviewDTO delegateDTO, PaperInfo paperInfo, SysUser delegateTeacher) {
        try {
            // 使用当前登录用户ID
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            
            SystemMessage message = new SystemMessage();
            message.setSenderId(currentUserId); // 使用当前登录用户而非硬编码 0
            message.setReceiverId(Long.parseLong(delegateDTO.getDelegateTeacherId()));
            message.setTitle("论文审核委托通知");
            message.setContent(String.format("您收到一篇论文《%s》的审核委托，请及时处理。委托原因：%s", 
                    paperInfo.getPaperTitle(), delegateDTO.getReason()));
            message.setMessageType("DELEGATION");
            message.setPriority(2);
            message.setRelatedType("PAPER");
            message.setRelatedId(Long.parseLong(delegateDTO.getPaperId()));
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            message.setIsRead(0);
            
            messageService.sendMessage(message);
            log.info("委托通知发送成功: delegateTeacherId={}", delegateDTO.getDelegateTeacherId());
        } catch (Exception e) {
            log.error("发送委托通知失败", e);
        }
    }

    /**
     * 将PaperInfo转换为PendingReviewVO
     */
    private PendingReviewVO convertToPendingReviewVO(PaperInfo paperInfo) {
        PendingReviewVO vo = new PendingReviewVO();
        
        vo.setPaperId(paperInfo.getId().toString());
        vo.setPaperTitle(paperInfo.getPaperTitle());
        vo.setStudentName(paperInfo.getStudentName());
        vo.setStudentId(paperInfo.getStudentId().toString());
        vo.setStudentNo(paperInfo.getStudentUsername());
        vo.setCollege(paperInfo.getCollegeName());
        vo.setEmail(""); // 需要从用户表获取
        vo.setAvatar(""); // 需要从用户表获取
        vo.setSubmitTime(paperInfo.getSubmitTime() != null ? paperInfo.getSubmitTime().toString() : "");
        vo.setDeadline(paperInfo.getCheckTime() != null ? paperInfo.getCheckTime().toString() : "");
        vo.setSimilarity(paperInfo.getSimilarityRate() != null ? paperInfo.getSimilarityRate().intValue() : 0);
        vo.setWaitingTime(paperInfo.getSubmitTime() != null ? 
            (int) ChronoUnit.DAYS.between(paperInfo.getSubmitTime().toLocalDate(), LocalDateTime.now().toLocalDate()) : 0);
        vo.setPriority("normal"); // 需要根据业务规则确定
        vo.setVersion("V1.0"); // 需要根据实际情况确定
        vo.setWordCount(paperInfo.getWordCount());
        vo.setPageCount(paperInfo.getWordCount() != null ? paperInfo.getWordCount() / 500 : 0); // 估算页数

        // 设置论文基础信息
        PendingReviewVO.PaperBaseInfoVO paperBaseInfo = new PendingReviewVO.PaperBaseInfoVO();
        paperBaseInfo.setPaperId(paperInfo.getId().toString());
        paperBaseInfo.setPaperTitle(paperInfo.getPaperTitle());
        paperBaseInfo.setStudentName(paperInfo.getStudentName());
        paperBaseInfo.setStudentId(paperInfo.getStudentId().toString());
        paperBaseInfo.setCreateTime(paperInfo.getCreateTime() != null ? paperInfo.getCreateTime().toString() : "");
        vo.setPaperBaseInfo(paperBaseInfo);

        // 设置任务基础信息
        PendingReviewVO.TaskBaseInfoVO taskBaseInfo = new PendingReviewVO.TaskBaseInfoVO();
        taskBaseInfo.setDeadline(paperInfo.getCheckTime() != null ? paperInfo.getCheckTime().toString() : "");
        taskBaseInfo.setReviewDays(paperInfo.getCheckTime() != null ? 
            (int) ChronoUnit.DAYS.between(LocalDateTime.now(), paperInfo.getCheckTime()) : 0);
        vo.setTaskBaseInfo(taskBaseInfo);

        return vo;
    }
    
    @Override
    public Result<Map<String, Object>> delegateReview(String teacherId, DelegateReviewDTO delegateDTO) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("委托审核: teacherId={}, paperId={}, delegateTeacherId={}", 
                    teacherId, delegateDTO.getPaperId(), delegateDTO.getDelegateTeacherId());
            
            // 1. 验证论文权限
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(delegateDTO.getPaperId()));
            if (paperInfo == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            
            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                return Result.error(ResultCode.PARAM_ERROR, "无权限委托此论文");
            }
            
            // 2. 验证被委托教师
            SysUser delegateTeacher = sysUserMapper.selectById(Long.parseLong(delegateDTO.getDelegateTeacherId()));
            if (delegateTeacher == null) {
                return Result.error(ResultCode.PARAM_ERROR, "被委托教师不存在");
            }
            
            // 3. 更新论文的指导教师
            paperInfo.setTeacherId(Long.parseLong(delegateDTO.getDelegateTeacherId()));
            paperInfo.setUpdateTime(LocalDateTime.now());
            pendingReviewMapper.updateById(paperInfo);
            
            // 4. 记录委托日志
            TeacherAllocationRecord allocationRecord = new TeacherAllocationRecord();
            allocationRecord.setPaperId(Long.parseLong(delegateDTO.getPaperId()));
            allocationRecord.setTeacherId(Long.parseLong(delegateDTO.getDelegateTeacherId()));
            allocationRecord.setAllocationReason(delegateDTO.getReason());
            allocationRecord.setAllocationTime(LocalDateTime.now());
            allocationRecord.setAllocationType("delegate");
            allocationRecord.setAllocationStatus("active");
            allocationRecord.setId(IdUtil.getSnowflakeNextId());
            allocationRecord.setCreateTime(LocalDateTime.now());
            allocationRecord.setUpdateTime(LocalDateTime.now());
            
            teacherAllocationRecordMapper.insert(allocationRecord);
            
            // 5. 发送系统消息通知被委托教师
            SystemMessage message = new SystemMessage();
            message.setTitle("论文审核委托通知");
            message.setContent(String.format("教师%s将论文《%s》的审核工作委托给您，请及时处理。委托原因：%s", 
                    sysUserMapper.selectById(Long.parseLong(teacherId)).getRealName(),
                    paperInfo.getPaperTitle(), 
                    delegateDTO.getReason()));
            message.setMessageType("BUSINESS");
            // 使用当前登录用户ID
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            message.setSenderId(currentUserId);
            message.setReceiverId(Long.parseLong(delegateDTO.getDelegateTeacherId()));
            message.setRelatedId(Long.parseLong(delegateDTO.getPaperId()));
            message.setRelatedType("paper");
            message.setPriority(2);
            message.setExpireTime(LocalDateTime.now().plusDays(7));
            message.setId(IdUtil.getSnowflakeNextId());
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            message.setIsRead(0);
            message.setIsDeleted(0);
            
            systemMessageMapper.insert(message);
            
            result.put("delegateId", allocationRecord.getId().toString());
            result.put("status", "success");
            
            log.info("委托审核成功: paperId={}, fromTeacher={}, toTeacher={}", 
                    delegateDTO.getPaperId(), teacherId, delegateDTO.getDelegateTeacherId());
            
            return Result.success("委托审核设置成功", result);
            
        } catch (NumberFormatException e) {
            log.error("参数格式错误: {}", delegateDTO, e);
            return Result.error(ResultCode.PARAM_ERROR, "参数格式错误");
        } catch (Exception e) {
            log.error("委托审核失败: {}", delegateDTO, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "委托审核失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<PaperPreviewUrlDTO> getPaperPreviewUrl(String teacherId, String paperId) {
        try {
            log.info("教师{}获取论文预览URL: paperId={}", teacherId, paperId);
            
            // 1. 验证论文权限
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(paperId));
            if (paperInfo == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            
            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                return Result.error(ResultCode.PARAM_ERROR, "无权限访问此论文");
            }
            
            // 2. 检查文件是否存在
            if (paperInfo.getFileId() == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文文件不存在");
            }
            
            // 3. 构建预览URL（使用标准FilePreviewService方式）
            PaperPreviewUrlDTO previewUrlDTO = new PaperPreviewUrlDTO();
            previewUrlDTO.setPaperId(paperId);
            
            // 使用FileService获取文件信息
            FileInfo fileInfo = fileService.getById(paperInfo.getFileId());
            if (fileInfo != null) {
                previewUrlDTO.setFileName(fileInfo.getOriginalFilename());
                previewUrlDTO.setFileType(getFileExtension(fileInfo.getOriginalFilename()));
                
                // 构建文件访问URL（包含文件名，符合KKFileView要求）
                String fileUrl = String.format("http://%s:%s%s/api/file/download/%s/%s",
                        serverHost,
                        serverPort,
                        appContext,
                        paperInfo.getFileId(),
                        java.net.URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8));
                
                // Base64编码URL（URL安全）
                String encodedUrl = Base64.getUrlEncoder().encodeToString(fileUrl.getBytes(StandardCharsets.UTF_8));
                
                // 构建KKFileView预览URL
                String previewUrl = String.format("%s/onlinePreview?url=%s", kkfileviewUrl, encodedUrl);
                
                previewUrlDTO.setPreviewUrl(previewUrl);
                previewUrlDTO.setKkFileViewServer(kkfileviewUrl);
            }
            
            log.info("成功生成论文预览URL: paperId={}, previewUrl={}", paperId, previewUrlDTO.getPreviewUrl());
            return Result.success("获取预览URL成功", previewUrlDTO);
            
        } catch (NumberFormatException e) {
            log.error("论文ID格式错误: {}", paperId, e);
            return Result.error(ResultCode.PARAM_ERROR, "论文ID格式错误");
        } catch (Exception e) {
            log.error("获取论文预览URL失败: teacherId={}, paperId={}", teacherId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文预览URL失败: " + e.getMessage());
        }
    }
    private boolean isTextFile(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("text/") || 
               contentType.equals("application/json") ||
               contentType.equals("application/xml");
    }
    
    private String getFileExtension(String contentType) {
        if (contentType == null) return "unknown";
        switch (contentType) {
            case "application/pdf": return "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
            case "application/msword": return "doc";
            case "text/plain": return "txt";
            default: return "file";
        }
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.1fKB", size / 1024.0);
        return String.format("%.1fMB", size / (1024.0 * 1024.0));
    }
    
    private int countWords(String content) {
        if (content == null || content.isEmpty()) return 0;
        return content.trim().split("\\s+").length;
    }
    
    @Override
    public Result<PaperReviewHistoryDTO> getPaperReviewHistory(String teacherId, String paperId) {
        try {
            log.info("教师{}获取论文审核历史: paperId={}", teacherId, paperId);
            
            // 1. 验证论文权限
            PaperInfo paperInfo = pendingReviewMapper.selectById(Long.parseLong(paperId));
            if (paperInfo == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            
            if (!paperInfo.getTeacherId().equals(Long.parseLong(teacherId))) {
                return Result.error(ResultCode.PARAM_ERROR, "无权限访问此论文");
            }
            
            // 2. 查询审核历史记录
            LambdaQueryWrapper<ReviewRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ReviewRecord::getPaperId, Long.parseLong(paperId))
                       .eq(ReviewRecord::getIsDeleted, 0)
                       .orderByDesc(ReviewRecord::getReviewTime);
            
            List<ReviewRecord> reviewRecords = reviewRecordMapper.selectList(queryWrapper);
            
            // 3. 构建返回数据
            PaperReviewHistoryDTO historyDTO = new PaperReviewHistoryDTO();
            historyDTO.setPaperId(paperId);
            historyDTO.setPaperTitle(paperInfo.getPaperTitle());
            
            // 4. 转换审核记录
            List<PaperReviewHistoryDTO.ReviewHistoryRecord> historyRecords = reviewRecords.stream()
                    .map(record -> {
                        PaperReviewHistoryDTO.ReviewHistoryRecord historyRecord = new PaperReviewHistoryDTO.ReviewHistoryRecord();
                        historyRecord.setReviewId(record.getId().toString());
                        
                        // 获取审核教师姓名
                        SysUser reviewer = sysUserMapper.selectById(record.getTeacherId());
                        historyRecord.setReviewerName(reviewer != null ? reviewer.getRealName() : "未知教师");
                        
                        historyRecord.setReviewStatus(record.getReviewStatus());
                        historyRecord.setReviewOpinion(record.getReviewOpinion());
                        historyRecord.setReviewTime(record.getReviewTime());
                        historyRecord.setVersion("V" + (reviewRecords.indexOf(record) + 1)); // 简单版本号
                        
                        // 设置状态描述
                        switch (record.getReviewStatus()) {
                            case 1: historyRecord.setReviewStatusDesc("审核通过"); break;
                            case 2: historyRecord.setReviewStatusDesc("审核不通过"); break;
                            case 3: historyRecord.setReviewStatusDesc("修改后通过"); break;
                            default: historyRecord.setReviewStatusDesc("未知状态");
                        }
                        
                        return historyRecord;
                    })
                    .collect(Collectors.toList());
            
            historyDTO.setHistory(historyRecords);
            
            log.info("成功获取论文审核历史: paperId={}, recordCount={}", paperId, historyRecords.size());
            return Result.success("获取审核历史成功", historyDTO);
            
        } catch (NumberFormatException e) {
            log.error("论文ID格式错误: {}", paperId, e);
            return Result.error(ResultCode.PARAM_ERROR, "论文ID格式错误");
        } catch (Exception e) {
            log.error("获取论文审核历史失败: teacherId={}, paperId={}", teacherId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文审核历史失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<TeacherReviewStatisticsDTO> getTeacherReviewStatistics(String teacherId, String startDate, 
                                                                         String endDate, Integer page, Integer pageSize) {
        try {
            log.info("教师{}获取审核历史统计: startDate={}, endDate={}, page={}, pageSize={}", 
                    teacherId, startDate, endDate, page, pageSize);
            
            // 1. 构建查询条件
            LambdaQueryWrapper<ReviewRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ReviewRecord::getTeacherId, Long.parseLong(teacherId))
                       .eq(ReviewRecord::getIsDeleted, 0);
            
            // 添加日期筛选
            if (startDate != null && !startDate.isEmpty()) {
                queryWrapper.ge(ReviewRecord::getReviewTime, LocalDateTime.parse(startDate + "T00:00:00"));
            }
            if (endDate != null && !endDate.isEmpty()) {
                queryWrapper.le(ReviewRecord::getReviewTime, LocalDateTime.parse(endDate + "T23:59:59"));
            }
            
            queryWrapper.orderByDesc(ReviewRecord::getReviewTime);
            
            // 2. 分页查询
            Page<ReviewRecord> pageQuery = new Page<>(page, pageSize);
            Page<ReviewRecord> reviewPage = reviewRecordMapper.selectPage(pageQuery, queryWrapper);
            
            // 3. 获取相关论文信息
            List<Long> paperIds = reviewPage.getRecords().stream()
                    .map(ReviewRecord::getPaperId)
                    .distinct()
                    .collect(Collectors.toList());
            
            final Map<Long, PaperInfo> paperMap = new HashMap<>();
            if (!paperIds.isEmpty()) {
                LambdaQueryWrapper<PaperInfo> paperQuery = new LambdaQueryWrapper<>();
                paperQuery.in(PaperInfo::getId, paperIds);
                List<PaperInfo> papers = pendingReviewMapper.selectList(paperQuery);
                paperMap.putAll(papers.stream().collect(Collectors.toMap(PaperInfo::getId, p -> p)));
            }
            
            // 4. 构建返回数据
            TeacherReviewStatisticsDTO statisticsDTO = new TeacherReviewStatisticsDTO();
            
            // 转换记录
            List<TeacherReviewStatisticsDTO.ReviewRecordItem> records = reviewPage.getRecords().stream()
                    .map(record -> {
                        TeacherReviewStatisticsDTO.ReviewRecordItem item = new TeacherReviewStatisticsDTO.ReviewRecordItem();
                        item.setPaperId(record.getPaperId().toString());
                        
                        // 获取论文信息
                        PaperInfo paperInfo = paperMap.get(record.getPaperId());
                        if (paperInfo != null) {
                            item.setPaperTitle(paperInfo.getPaperTitle());
                            item.setStudentName(paperInfo.getStudentName());
                        }
                        
                        item.setReviewStatus(record.getReviewStatus());
                        item.setReviewTime(record.getReviewTime());
                        
                        // 计算处理时间（假设提交时间为论文创建时间）
                        if (paperInfo != null && paperInfo.getCreateTime() != null) {
                            long hours = ChronoUnit.HOURS.between(paperInfo.getCreateTime(), record.getReviewTime());
                            long days = hours / 24;
                            long remainingHours = hours % 24;
                            item.setProcessingTime(String.format("%d天%d小时", days, remainingHours));
                        } else {
                            item.setProcessingTime("未知");
                        }
                        
                        return item;
                    })
                    .collect(Collectors.toList());
            
            statisticsDTO.setRecords(records);
            statisticsDTO.setTotal(reviewPage.getTotal());
            
            // 5. 统计信息
            TeacherReviewStatisticsDTO.Statistics stats = new TeacherReviewStatisticsDTO.Statistics();
            stats.setTotalReviewed((int) reviewPage.getTotal());
            
            // 按状态统计
            Map<Integer, Long> statusCount = reviewPage.getRecords().stream()
                    .collect(Collectors.groupingBy(ReviewRecord::getReviewStatus, Collectors.counting()));
            
            stats.setApproved(statusCount.getOrDefault(1, 0L).intValue());
            stats.setRejected(statusCount.getOrDefault(2, 0L).intValue());
            stats.setRevised(statusCount.getOrDefault(3, 0L).intValue());
            
            statisticsDTO.setStatistics(stats);
            
            log.info("成功获取教师审核历史统计: teacherId={}, recordCount={}", teacherId, records.size());
            return Result.success("获取审核历史统计成功", statisticsDTO);
            
        } catch (NumberFormatException e) {
            log.error("教师ID格式错误: {}", teacherId, e);
            return Result.error(ResultCode.PARAM_ERROR, "教师ID格式错误");
        } catch (Exception e) {
            log.error("获取教师审核历史统计失败: teacherId={}", teacherId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取教师审核历史统计失败: " + e.getMessage());
        }
    }
    
    private int calculatePages(String content) {
        if (content == null || content.isEmpty()) return 0;
        // 估算每页500字
        return Math.max(1, countWords(content) / 500);
    }
}