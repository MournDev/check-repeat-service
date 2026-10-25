package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper;
import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.admin.service.CollegeQuotaService;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.constant.PaperNoticeConstants;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.service.Impl.SubjectService;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.mapper.PaperAttachmentMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckRuleMapper;
import com.abin.checkrepeatservice.student.event.PaperSubmittedEvent;
import com.abin.checkrepeatservice.student.service.PaperInfoService;
import com.abin.checkrepeatservice.student.vo.PaperQueryRequest;
import com.abin.checkrepeatservice.student.dto.*;
import com.abin.checkrepeatservice.user.service.AdvisorAssignService;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * 论文信息服务实现类
 */
@Slf4j
@Service
public class PaperInfoServiceImpl extends ServiceImpl<PaperInfoMapper, PaperInfo> implements PaperInfoService {

    @Resource
    private FileService fileService;
    
    @Value("${file.upload.base-path}")
    private String uploadBasePath;

    @Resource
    private AdvisorAssignService advisorAssignService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private PaperSubmitMapper paperSubmitMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NotificationService notificationService;
    
    @Resource
    private NotificationFacadeService notificationFacadeService;
    
    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckTaskStateMachine checkTaskStateMachine;

    @Resource
    private CollegeQuotaService collegeQuotaService;
    
    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private CheckRuleMapper checkRuleMapper;

    @Resource
    private SystemConfigMapper systemConfigMapper;

    @Resource
    private PaperAttachmentMapper paperAttachmentMapper;

    @Resource
    private SubjectService subjectService;

    // M2.2 阶段门收口：论文状态流转统一校验（默认影子模式，只告警不阻断）
    @Resource
    private PaperStageGate paperStageGate;

    // 提交后分配导师改为事件驱动；查重由导师确认接收触发
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 由学科编码反查并写入学科ID —— 论文写路径的**唯一入口**。
     */
    private void applySubject(PaperInfo paperInfo, String subjectCode) {
        if (subjectCode == null || subjectCode.isBlank()) {
            return;
        }
        String code = subjectCode.trim();
        paperInfo.setSubjectCode(code);
        Long subjectId = subjectService.findIdByCode(code);
        if (subjectId == null) {
            log.warn("学科编码在 subject 表中不存在，subject_id 置空 - 论文ID: {}, subjectCode: {}",
                    paperInfo.getId(), code);
            paperInfo.setSubjectId(null);
            if (paperInfo.getId() != null) {
                paperInfoMapper.clearSubjectId(paperInfo.getId());
            }
            return;
        }
        paperInfo.setSubjectId(subjectId);
    }






    @Override
    public Page<PaperInfo> getStudentPaperPage(PaperQueryRequest request) {
        try {
            // 参数校验
            if (request.getStudentId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "学生ID不能为空");
            }
            // 构建查询条件
            LambdaQueryWrapper<PaperInfo> queryWrapper = buildQueryWrapper(request);
            // 分页查询
            Page<PaperInfo> page = new Page<>(request.getPageNum(), request.getPageSize());
            Page<PaperInfo> resultPage = page(page, queryWrapper);
            // 重提闭环字段由服务端计算，前端直接用 paper.canResubmit 判定，不再猜状态
            enrichResubmitInfo(resultPage.getRecords());
            return resultPage;
        }  catch (Exception e) {
            log.error("论文列表分页查询失败，学生ID：{}", request.getStudentId(), e);
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文列表查询失败");
        }
    }

    @Override
    public void enrichResubmitInfo(List<PaperInfo> papers) {
        if (papers == null || papers.isEmpty()) {
            return;
        }
        Integer maxRound = resolveMaxRevisionRound();
        LocalDateTime deadline = resolveResubmitDeadline();
        LocalDateTime now = LocalDateTime.now();
        for (PaperInfo paper : papers) {
            fillResubmitInfo(paper, maxRound, deadline, now);
        }
    }

    @Override
    public void enrichResubmitInfo(PaperInfo paper) {
        if (paper == null) {
            return;
        }
        fillResubmitInfo(paper, resolveMaxRevisionRound(), resolveResubmitDeadline(), LocalDateTime.now());
    }

    /**
     * 计算单篇论文的重提资格与下一步动作：
     * canResubmit = 状态 rejected 且未超重提上限、未过截止、且不存在更新的版本。
     */
    private void fillResubmitInfo(PaperInfo paper, Integer maxRound,
                                  LocalDateTime deadline, LocalDateTime now) {
        int round = paper.getVersion() == null ? 0 : Math.max(0, paper.getVersion() - 1);
        paper.setRevisionRound(round);
        paper.setMaxRevisionRound(maxRound);
        paper.setResubmitDeadline(deadline);

        boolean rejected = DictConstants.PaperStatus.REJECTED.equals(paper.getPaperStatus());
        boolean withinLimit = maxRound == null || round < maxRound;
        boolean beforeDeadline = deadline == null || !now.isAfter(deadline);
        boolean noNewerVersion = !hasNewerVersion(paper);
        boolean canResubmit = rejected && withinLimit && beforeDeadline && noNewerVersion;
        paper.setCanResubmit(canResubmit);
        paper.setNextAction(buildNextAction(paper, canResubmit));
    }

    /**
     * 是否已存在同根链上更高版本的论文（重提会生成 version+1 的新记录，
     * 旧被驳回版本不应再次允许重提）。
     */
    private boolean hasNewerVersion(PaperInfo paper) {
        if (paper.getId() == null || paper.getVersion() == null) {
            return false;
        }
        Long rootId = paper.getParentPaperId() != null ? paper.getParentPaperId() : paper.getId();
        Long count = paperInfoMapper.selectCount(new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getIsDeleted, 0)
                .gt(PaperInfo::getVersion, paper.getVersion())
                .and(w -> w.eq(PaperInfo::getId, rootId)
                        .or()
                        .eq(PaperInfo::getParentPaperId, rootId)));
        return count != null && count > 0;
    }

    /** 重提上限：取默认查重规则；无默认则取最新一条；未配置返回 null（不限） */
    private Integer resolveMaxRevisionRound() {
        CheckRule rule = checkRuleMapper.selectOne(new LambdaQueryWrapper<CheckRule>()
                .eq(CheckRule::getIsDefault, 1)
                .orderByDesc(CheckRule::getId)
                .last("LIMIT 1"));
        if (rule == null) {
            rule = checkRuleMapper.selectOne(new LambdaQueryWrapper<CheckRule>()
                    .orderByDesc(CheckRule::getId)
                    .last("LIMIT 1"));
        }
        return rule == null ? null : rule.getMaxReSubmitCount();
    }

    /** 重提截止：system_config.resubmit_deadline（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）；未配置返回 null */
    private LocalDateTime resolveResubmitDeadline() {
        SystemConfig config = systemConfigMapper.selectByConfigKey("resubmit_deadline");
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return null;
        }
        String value = config.getConfigValue().trim();
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atTime(23, 59, 59);
            } catch (Exception e) {
                log.warn("system_config.resubmit_deadline 格式非法：{}", value);
                return null;
            }
        }
    }

    /** 下一步动作要素：前端按 actionCode 做路由映射，bizId 直接定位论文 */
    private Map<String, Object> buildNextAction(PaperInfo paper, boolean canResubmit) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("bizType", "PAPER");
        // 雪花ID超出 JS 安全整数范围，按字符串下发避免精度丢失
        action.put("bizId", paper.getId() == null ? null : String.valueOf(paper.getId()));
        if (canResubmit) {
            action.put("type", "REVISION_REQUIRED");
            action.put("actionCode", "REVISE_PAPER");
        } else if (DictConstants.PaperStatus.REJECTED.equals(paper.getPaperStatus())) {
            action.put("type", "REVISION_REQUIRED");
            action.put("actionCode", "VIEW_PAPER");
        } else if (DictConstants.PaperStatus.AUDITING.equals(paper.getPaperStatus())) {
            action.put("type", "REVIEW_PENDING");
            action.put("actionCode", "VIEW_PAPER");
        } else {
            action.put("type", "VIEW_PAPER");
            action.put("actionCode", "VIEW_PAPER");
        }
        return action;
    }
    /**
     * 构建查询条件包装器
     */
    private LambdaQueryWrapper<PaperInfo> buildQueryWrapper(PaperQueryRequest request) {
        LambdaQueryWrapper<PaperInfo> queryWrapper = new LambdaQueryWrapper<>();

        // 基础条件：学生ID和未删除
        queryWrapper.eq(PaperInfo::getStudentId, request.getStudentId())
                .eq(PaperInfo::getIsDeleted, 0);

        // 论文状态条件（支持查询所有状态）
        if (StringUtils.hasText(request.getPaperStatus())) {
            queryWrapper.eq(PaperInfo::getPaperStatus, request.getPaperStatus());
        }

        // 论文类型条件。
        // 学生端「查重历史」的论文类型下拉此前一直是个空转控件：前端传了 paperType，
        // 但这个 DTO 上没有该字段，请求体里那个值在反序列化时就被丢掉了，
        // 于是无论选什么都返回全量 —— 而且它给的还是 THESIS/JOURNAL 这套从未存在过的值。
        if (StringUtils.hasText(request.getPaperType())) {
            queryWrapper.eq(PaperInfo::getPaperType, request.getPaperType());
        }

        // 论文名称搜索（模糊查询）
        if (StringUtils.hasText(request.getPaperTitle())) {
            queryWrapper.like(PaperInfo::getPaperTitle, request.getPaperTitle());
        }

        // 时间范围搜索
        if (request.getStartTime() != null) {
            queryWrapper.ge(PaperInfo::getSubmitTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            queryWrapper.le(PaperInfo::getSubmitTime, request.getEndTime());
        }

        // 按提交时间倒序
        queryWrapper.orderByDesc(PaperInfo::getSubmitTime);

        return queryWrapper;
    }
    /**
     * 软删除论文（标记删除而不是物理删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePaper(Long paperId, Long studentId) {
        try {
            log.info("开始删除论文 - 论文ID: {}, 操作人ID: {}", paperId, studentId);

            // 1. 验证论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                log.warn("论文不存在 - 论文ID: {}", paperId);
                return false;
            }

            // 2. 验证论文状态
            if (!DictConstants.PaperStatus.PENDING.equals(paperInfo.getPaperStatus())) {
                log.warn("论文状态不允许删除 - 论文ID: {}, 状态: {}", paperId, paperInfo.getPaperStatus());
                return false;
            }

            // 3. 更新论文状态为已撤回,软删除
            paperStageGate.guard(paperInfo.getPaperStatus(),
                    DictConstants.PaperStatus.WITHDRAWN, "deletePaper");
            PaperInfo updateInfo = new PaperInfo();
            updateInfo.setId(paperId);
            updateInfo.setPaperStatus(DictConstants.PaperStatus.WITHDRAWN);
            updateInfo.setUpdateTime(LocalDateTime.now());
            updateInfo.setIsDeleted(1);

            int result = paperInfoMapper.updateById(updateInfo);
            boolean success = result > 0;

            if (success) {
                // 4. 更新提交记录状态
                PaperSubmit submitUpdate = new PaperSubmit();
                paperSubmitMapper.update(submitUpdate,
                        new LambdaQueryWrapper<PaperSubmit>()
                                .eq(PaperSubmit::getPaperId, paperId)
                                .orderByDesc(PaperSubmit::getSubmitVersion)
                                .last("LIMIT 1"));
            }

            log.info("论文软删除完成 - 论文ID: {}, 结果: {}", paperId, success);
            return success;

        } catch (Exception e) {
            log.error("软删除论文失败 - 论文ID: {}", paperId, e);
            throw new RuntimeException("删除论文失败", e);
        }
    }

    /**
     * 完整的论文提交流程（包含文件上传和信息录入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperInfo submitPaper(MultipartFile multipartFile, String subjectCode, String paperTitle,
                                 String paperAbstract, Long collegeId, Long majorId, String paperType,
                                 String degreeType, Long studentId) {
        
        log.info("开始完整论文提交流程 - 学生ID: {}, 论文标题: {}", studentId, paperTitle);
        
        try {
            // 1. 文件上传处理
            FileInfo uploadedFile = uploadPaperFile(multipartFile, studentId);
            
            // 2. 调用文件ID方式提交 - 修复参数类型
            return submitPaperByFileId(subjectCode, paperTitle, paperAbstract, collegeId, majorId, 
                                     paperType, degreeType, uploadedFile.getId(), uploadedFile.getMd5(), studentId);
            
        } catch (Exception e) {
            log.error("完整论文提交流程失败 - 学生ID: {}, 论文标题: {}", studentId, paperTitle, e);
            // 发送提交失败通知
            sendPaperSubmitFailedNotification(paperTitle, studentId, e.getMessage());
            throw new RuntimeException("论文提交失败: " + e.getMessage());
        }
    }
    
    /**
     * 文件上传处理
     */
    private FileInfo uploadPaperFile(MultipartFile multipartFile, Long studentId) {
        try {
            // 调用文件服务上传文件，获取文件 ID
            Long fileId = fileService.uploadFile(multipartFile, studentId);
                
            // 通过文件 ID 查询文件信息
            FileInfo fileInfo = fileInfoMapper.selectById(fileId);
            
            if (fileInfo == null) {
                throw new RuntimeException("文件上传后查询失败，文件ID: " + fileId);
            }
            
            log.info("文件上传成功 - 文件ID: {}, 文件名: {}", fileInfo.getId(), fileInfo.getOriginalFilename());
            return fileInfo;
        } catch (Exception e) {
            log.error("文件上传失败 - 学生ID: {}", studentId, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 文件ID方式提交论文
     * 根据文件ID提交论文，不涉及文件上传
     */
    @Transactional(rollbackFor = Exception.class)
    public PaperInfo submitPaperByFileId(String subjectCode,String paperTitle, String paperAbstract,
                                         Long collegeId, Long majorId, String paperType, String degreeType,
                                         Long fileId, String fileMd5, Long studentId) {

        log.info("开始文件ID方式提交论文 - 学生ID: {}, 论文标题: {}, 文件ID: {}",
                studentId, paperTitle, fileId);

        try {
            // 1. 验证文件信息
            validateFileInfo(fileId, fileMd5);

            // 2. 查找是否已存在该学生的论文（根据标题判断）
            PaperInfo existingPaper = findExistingPaper(studentId, paperTitle);
            // 学位类型：显式传入优先；未传时保留原论文值，新建默认本科
            String effectiveDegreeType = resolveDegreeType(degreeType, existingPaper);

            PaperInfo paperInfo;
            boolean isNewPaper = false;//是否为新论文
            if (existingPaper != null) {
                // 更新现有论文信息
                paperInfo = updatePaperInfo(existingPaper, subjectCode, paperTitle, paperAbstract,
                        collegeId, majorId, paperType, effectiveDegreeType, fileId, fileMd5);
                log.info("更新现有论文 - 论文ID: {}", paperInfo.getId());
            } else {
                // 创建新论文信息
                paperInfo = createPaperInfo(subjectCode,paperTitle, paperAbstract, collegeId,
                        majorId, paperType, effectiveDegreeType, fileId, fileMd5, studentId);
                log.info("创建新论文 - 论文ID: {}", paperInfo.getId());
                isNewPaper = true;
            }

            // 3. 创建提交记录
            createPaperSubmitRecord(paperInfo, fileId, fileMd5, studentId);
            // 4. 发送论文提交成功通知
            sendPaperSubmitSuccessNotification(paperInfo, studentId, isNewPaper);
            // 5. 事务提交后异步分配指导老师并触发查重（事件驱动，修复 @Async 自调用失效）
            eventPublisher.publishEvent(
                    new PaperSubmittedEvent(this, paperInfo.getId(), studentId));

            log.info("文件ID方式提交论文成功 - 论文ID: {}, 文件ID: {}", paperInfo.getId(), fileId);
            return paperInfo;

        } catch (Exception e) {
            log.error("文件ID方式提交论文失败 - 学生ID: {}, 论文标题: {}", studentId, paperTitle, e);
            throw new RuntimeException("论文提交失败: " + e.getMessage());
        }
    }
    /**
     * 论文提交后的导师分配（不再触发查重）。
     *
     * <p>查重触发点已统一收敛：学生提交只负责分配导师，查重必须等导师在
     * {@code TeacherAssignmentServiceImpl#confirmAssignment} 中确认接收后再触发。</p>
     *
     * <p>已有导师的再次提交（修订版重提、撤回后重提、同标题重提）不重新分配，
     * 只补发“待确认”站内信，让导师确认新版本后再触发查重。</p>
     *
     * <p>由 {@link com.abin.checkrepeatservice.student.listener.PaperSubmissionAllocationListener}
     * 在论文提交事务 AFTER_COMMIT 后以独立 bean 异步调用。</p>
     */
    public void allocateAdvisor(Long paperSubmitId, Long studentId) {
        try {
            log.info("开始异步分配指导老师 - 论文ID: {}, 学生ID: {}", paperSubmitId, studentId);

            PaperInfo paperInfo = paperInfoMapper.selectById(paperSubmitId);
            if (paperInfo == null) {
                log.warn("论文不存在，跳过导师分配 - 论文ID: {}", paperSubmitId);
                return;
            }

            // 已有导师：不重新分配，只提醒导师确认新版本；查重由确认动作触发
            if (paperInfo.getTeacherId() != null) {
                if (DictConstants.AllocationStatus.PENDING.equals(paperInfo.getAllocationStatus())) {
                    notificationService.sendTeacherAssignmentNotice(
                            paperInfo.getId(), paperInfo.getPaperTitle(), studentId, paperInfo.getTeacherId());
                }
                log.info("论文已有指导教师，等待导师确认后触发查重 - 论文ID: {}", paperSubmitId);
                return;
            }

            // 无导师：走自动分配；分配成功通知由 autoAssignAdvisor 内部统一发送
            Result<Boolean> result = advisorAssignService.autoAssignAdvisor(paperSubmitId);
            if (result.isSuccess()) {
                log.info("指导老师分配成功，等待导师确认接收 - 论文ID: {}", paperSubmitId);
            } else {
                log.warn("指导老师分配失败 - 论文ID: {}, 原因: {}", paperSubmitId, result.getMessage());
                sendAdvisorAllocateFailedNotification(paperSubmitId, studentId, result.getMessage());
            }

        } catch (BusinessException e) {
            log.error("异步分配指导老师业务异常 - 论文ID: {}, 错误: {}", paperSubmitId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("异步分配指导老师系统异常 - 论文ID: {}", paperSubmitId, e);
        }
    }

    /**
     * 获取论文状态标签
     */
    public String getPaperStatusLabel(String statusValue) {
        if (!StringUtils.hasText(statusValue)) {
            return "未知状态";
        }

        switch (statusValue) {
            case DictConstants.PaperStatus.PENDING:
                return "待分配指导老师";
            case DictConstants.PaperStatus.CHECKING:
                return "待重中";
            case DictConstants.PaperStatus.AUDITING:
                return "待审核";
            case DictConstants.PaperStatus.COMPLETED:
                return "已完成";
            case DictConstants.PaperStatus.REJECTED:
                return "已拒绝";
            case DictConstants.PaperStatus.WITHDRAWN:
                return "已撤回";
            default:
                return "未知状态";
        }
    }

    /**
     * 验证文件信息
     */
    private void validateFileInfo(Long fileId, String fileMd5) {
        if (fileId == null) {
            throw new RuntimeException("文件 ID 不能为空");
        }
    
        // 验证文件是否存在
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null) {
            throw new RuntimeException("文件不存在，文件 ID: " + fileId);
        }

        // 验证MD5（如果提供了MD5）
        if (StringUtils.hasText(fileMd5) && StringUtils.hasText(fileInfo.getMd5())) {
            if (!fileMd5.equals(fileInfo.getMd5())) {
                throw new RuntimeException("文件MD5校验失败，文件可能已被修改");
            }
        }

        log.debug("文件验证通过 - 文件ID: {}, 文件名: {}", fileId, fileInfo.getOriginalFilename());
    }

    /**
     * 查找已存在的论文
     */
    private PaperInfo findExistingPaper(Long studentId, String paperTitle) {
        return paperInfoMapper.selectOne(new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getPaperTitle, paperTitle)
                .orderByDesc(PaperInfo::getCreateTime)
                .last("LIMIT 1"));
    }

    /**
     * 学位类型：显式传入优先；未传时保留原论文值；新建论文默认本科。
     * 合法值由 PaperSubmitRequest @Pattern 兜底（undergraduate/master/doctor）。
     */
    private String resolveDegreeType(String degreeType, PaperInfo existingPaper) {
        if (StringUtils.hasText(degreeType)) {
            return degreeType.trim().toLowerCase();
        }
        if (existingPaper != null && StringUtils.hasText(existingPaper.getDegreeType())) {
            return existingPaper.getDegreeType();
        }
        return "undergraduate";
    }

    /**
     * 创建新论文信息
     */
    private PaperInfo createPaperInfo(String subjectCode,String paperTitle, String paperAbstract,
                                      Long collegeId, Long majorId, String paperType, String degreeType,
                                      Long fileId, String fileMd5, Long studentId) {
        PaperInfo paperInfo = new PaperInfo();
        // 设置作者姓名
        SysUser student = sysUserMapper.selectById(studentId);
        if (student != null) {
            paperInfo.setAuthor(student.getRealName()); // 或者使用 username，根据需求
        } else {
            log.warn("未找到学生信息，学生ID: {}", studentId);
            paperInfo.setAuthor("未知作者"); // 设置默认值
        }
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        applySubject(paperInfo, subjectCode);
        paperInfo.setPaperTitle(paperTitle);
        paperInfo.setPaperAbstract(paperAbstract);
        paperInfo.setCollegeId(collegeId);
        paperInfo.setMajorId(majorId);
        paperInfo.setPaperType(paperType);
        paperInfo.setDegreeType(degreeType);
        paperInfo.setFileId(fileId);
        paperInfo.setFileMd5(fileMd5);
        paperInfo.setFilePath(fileInfo.getStoragePath());
        paperInfo.setWordCount(fileInfo.getWordCount());
        paperInfo.setStudentId(studentId);
        paperInfo.setPaperStatus(DictConstants.PaperStatus.PENDING);
        // similarity_rate 不落值：NULL = 未检测（前端按 null 显示「未检测」、隐藏查重报告入口），
        // 落 0.00 会让从未查重的论文被误判为已检测
        paperInfo.setSubmitTime(LocalDateTime.now());
        paperInfo.setCreateTime(LocalDateTime.now());
        paperInfo.setUpdateTime(LocalDateTime.now());

        int result = paperInfoMapper.insert(paperInfo);
        if (result <= 0) {
            throw new RuntimeException("创建论文信息失败");
        }

        return paperInfo;
    }

    /**
     * 更新现有论文信息
     */
    private PaperInfo updatePaperInfo(PaperInfo existingPaper, String  subjectCode,String paperTitle, String paperAbstract,
                                      Long collegeId, Long majorId, String paperType, String degreeType,
                                      Long fileId, String fileMd5) {

        applySubject(existingPaper, subjectCode);
        existingPaper.setPaperTitle(paperTitle);
        existingPaper.setPaperAbstract(paperAbstract);
        existingPaper.setCollegeId(collegeId);
        existingPaper.setMajorId(majorId);
        existingPaper.setPaperType(paperType);
        existingPaper.setDegreeType(degreeType);
        existingPaper.setFileId(fileId);
        existingPaper.setFileMd5(fileMd5);
        existingPaper.setFilePath(fileInfoMapper.selectById(fileId).getStoragePath());
        paperStageGate.guard(existingPaper.getPaperStatus(),
                DictConstants.PaperStatus.PENDING, "updatePaperInfo");
        existingPaper.setPaperStatus(DictConstants.PaperStatus.PENDING); // 重置状态
        // 查重触发点已收敛到导师确认：已有导师的再次提交必须重新进入待确认，
        // 否则 autoAssignAdvisor 会因 teacherId 非空直接返回，论文永远等不到查重。
        if (existingPaper.getTeacherId() != null) {
            existingPaper.setAllocationStatus(DictConstants.AllocationStatus.PENDING);
        }
        existingPaper.setWordCount(fileInfoMapper.selectById(fileId).getWordCount());//论文字数
        // 重置相似度为 NULL（= 未检测）：updateById 按非空策略跳过 null 字段，必须显式置空
        existingPaper.setSimilarityRate(null);
        existingPaper.setSubmitTime(LocalDateTime.now());
        existingPaper.setUpdateTime(LocalDateTime.now());

        int result = paperInfoMapper.updateById(existingPaper);
        if (result <= 0) {
            throw new RuntimeException("更新论文信息失败");
        }
        paperInfoMapper.update(null, new LambdaUpdateWrapper<PaperInfo>()
                .eq(PaperInfo::getId, existingPaper.getId())
                .set(PaperInfo::getSimilarityRate, null));

        return existingPaper;
    }

    /**
     * 创建提交记录
     */
    private void createPaperSubmitRecord(PaperInfo paperInfo, Long fileId, String fileMd5, Long studentId) {
        // 获取当前版本号
        Integer currentVersion = getCurrentVersion(paperInfo.getId());
        Integer newVersion = currentVersion + 1;

        PaperSubmit paperSubmit = new PaperSubmit();
        paperSubmit.setPaperId(paperInfo.getId());
        paperSubmit.setStudentId(studentId);
        paperSubmit.setSubmitVersion(newVersion);
        paperSubmit.setFileId(fileId);
        paperSubmit.setFileMd5(fileMd5);
        paperSubmit.setSubmitTime(LocalDateTime.now());
        // 备注字段可用于记录提交说明
        paperSubmit.setRemark("第" + newVersion + "次提交");
        paperSubmit.setCreateTime(LocalDateTime.now());

        int result = paperSubmitMapper.insert(paperSubmit);
        if (result <= 0) {
            throw new RuntimeException("创建提交记录失败");
        }

        log.debug("创建提交记录成功 - 论文ID: {}, 版本: {}", paperInfo.getId(), newVersion);
    }

    /**
     * 获取当前版本号
     */
    private Integer getCurrentVersion(Long paperId) {
        PaperSubmit lastSubmit = paperSubmitMapper.selectOne(
                new LambdaQueryWrapper<PaperSubmit>()
                        .eq(PaperSubmit::getPaperId, paperId)
                        .orderByDesc(PaperSubmit::getSubmitVersion)
                        .last("LIMIT 1")
        );
        return lastSubmit != null ? lastSubmit.getSubmitVersion() : 0;
    }

    /**
     * 发送论文提交成功通知
     */
    private void sendPaperSubmitSuccessNotification(PaperInfo paperInfo, Long studentId, boolean isNewPaper) {
        try {
            String title = isNewPaper ? "论文提交成功" : "论文重新提交成功";
            String content = String.format("您的论文《%s》已成功%s，系统将自动分配指导老师并进行查重检测。",
                    paperInfo.getPaperTitle(), isNewPaper ? "提交" : "重新提交");

            notificationService.sendSystemNotice(
                    studentId,
                    title,
                    content,
                    PaperNoticeConstants.NOTICE_TYPE_PAPER_SUBMIT_SUCCESS,
                    paperInfo.getId()
            );
            log.info("论文提交成功通知发送成功 - 论文ID: {}, 学生ID: {}", paperInfo.getId(), studentId);
        } catch (Exception e) {
            log.error("发送论文提交成功通知失败 - 论文ID: {}, 学生ID: {}", paperInfo.getId(), studentId, e);
        }
    }

    /**
     * 发送论文提交失败通知
     */
    private void sendPaperSubmitFailedNotification(String paperTitle, Long studentId, String errorMsg) {
        try {
            String title = "论文提交失败";
            String content = String.format("您的论文《%s》提交失败。%s",
                    paperTitle,
                    StringUtils.hasText(errorMsg) ? "原因：" + errorMsg : "请稍后重试或联系管理员。");

            notificationService.sendSystemNotice(
                    studentId,
                    title,
                    content,
                    PaperNoticeConstants.NOTICE_TYPE_PAPER_SUBMIT_FAILED,
                    null
            );
            log.info("论文提交失败通知发送成功 - 学生ID: {}", studentId);
        } catch (Exception e) {
            log.error("发送论文提交失败通知失败 - 学生ID: {}", studentId, e);
        }
    }

// ========== 指导老师分配相关通知 ==========

    // 分配成功通知不在此类发送：autoAssignAdvisor 的 sendAssignmentNotifications 已统一发
    // （此前此处再发一条「指导老师分配成功」，学生端同事件收到两条站内消息 + 两条 WS 弹窗）

    /**
     * 发送指导老师分配失败通知
     */
    private void sendAdvisorAllocateFailedNotification(Long paperId, Long studentId, String errorMsg) {
        try {
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) return;

            String title = "指导老师分配失败";
            String content = String.format("您的论文《%s》指导老师分配失败。%s",
                    paperInfo.getPaperTitle(),
                    StringUtils.hasText(errorMsg) ? "原因：" + errorMsg : "请稍后重试或联系管理员。");

            notificationService.sendSystemNotice(
                    studentId,
                    title,
                    content,
                    PaperNoticeConstants.NOTICE_TYPE_ADVISOR_ASSIGN_FAILED,
                    paperId
            );
            log.info("指导老师分配失败通知发送成功 - 论文ID: {}", paperId);
        } catch (Exception e) {
            log.error("发送指导老师分配失败通知失败 - 论文ID: {}", paperId, e);
        }
    }
    
    // ==================== 新增接口实现 ====================
    
    /**
     * 论文撤回接口实现
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean withdrawPaper(Long paperId, Long studentId, String reason) {
        try {
            log.info("开始撤回论文 - 论文 ID: {}, 学生 ID: {}, 原因：{}", paperId, studentId, reason);
                
            // 1. 验证论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                log.warn("论文不存在 - 论文 ID: {}", paperId);
                return false;
            }
                
            // 2. 验证论文归属
            if (!paperInfo.getStudentId().equals(studentId)) {
                log.warn("无权限撤回他人论文 - 论文 ID: {}, 学生 ID: {}", paperId, studentId);
                return false;
            }
                
            // 3. 验证论文状态：只有待分配/已分配可撤回；查重中直接拦截，
            //    避免任务跑完后把 withdrawn 覆盖回 auditing。
            String currentStatus = paperInfo.getPaperStatus();
            if (DictConstants.PaperStatus.CHECKING.equals(currentStatus)) {
                throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS,
                        "论文正在查重中，无法撤回");
            }
            if (!(DictConstants.PaperStatus.PENDING.equals(currentStatus)
                    || DictConstants.PaperStatus.ASSIGNED.equals(currentStatus))) {
                String statusLabel = getPaperStatusLabel(currentStatus);
                log.warn("论文状态不允许撤回 - 论文 ID: {}, 状态：{}", paperId, statusLabel);
                throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS,
                        "当前状态不允许撤回：" + statusLabel);
            }
                
            // 【新增】4. 检查撤回次数限制
            Long withdrawCount = paperSubmitMapper.selectCount(
                new LambdaQueryWrapper<PaperSubmit>()
                    .eq(PaperSubmit::getPaperId, paperId)
                    .like(PaperSubmit::getRemark, "%撤回%")
            );
                
            int maxWithdrawCount = 3; // 最多撤回 3 次
            if (withdrawCount >= maxWithdrawCount) {
                log.warn("论文撤回次数已达上限 - 论文 ID: {}, 已撤回：{}次", paperId, withdrawCount);
                throw new BusinessException(ResultCode.BUSINESS_NO_SAFE, 
                    String.format("该论文撤回次数已达上限（最多%d次），请联系管理员", maxWithdrawCount));
            }
                
            // 5. 条件更新为已撤回：以读取时的 pending/assigned 作为条件，
            //    防止并发确认接收把论文改成 checking 后又被撤回覆盖。
            paperStageGate.guard(paperInfo.getPaperStatus(),
                    DictConstants.PaperStatus.WITHDRAWN, "withdrawPaper");
            int result = paperInfoMapper.update(null, new LambdaUpdateWrapper<PaperInfo>()
                    .eq(PaperInfo::getId, paperId)
                    .in(PaperInfo::getPaperStatus,
                            DictConstants.PaperStatus.PENDING,
                            DictConstants.PaperStatus.ASSIGNED)
                    .eq(PaperInfo::getIsDeleted, 0)
                    .set(PaperInfo::getPaperStatus, DictConstants.PaperStatus.WITHDRAWN)
                    .set(PaperInfo::getUpdateTime, LocalDateTime.now()));
            if (result == 0) {
                throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS,
                        "论文状态已变化，撤回失败，请刷新后重试");
            }
            // 6. 撤回成功后取消该论文所有 pending 任务（含退额度），防止撤回后任务继续执行
            cancelPendingCheckTasks(paperId);
            // 7. 发送撤回成功通知
            sendPaperWithdrawSuccessNotification(paperInfo, studentId, reason);
            log.info("论文撤回成功 - 论文ID: {}", paperId);
            return true;
            
        } catch (BusinessException e) {
            log.warn("论文撤回被业务规则拦截 - 论文ID: {}, 原因: {}", paperId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("论文撤回失败 - 论文ID: {}", paperId, e);
            throw new RuntimeException("论文撤回失败: " + e.getMessage());
        }
    }

    /**
     * 撤回论文时取消所有 pending 查重任务。
     * 条件状态流转保证不会误取消已被调度器抢占的 checking 任务；
     * 取消成功后按 quota_charged 幂等退还学院额度。
     */
    private void cancelPendingCheckTasks(Long paperId) {
        List<CheckTask> pendingTasks = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                        .eq(CheckTask::getPaperId, paperId)
                        .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.PENDING)
                        .eq(CheckTask::getIsDeleted, 0));
        for (CheckTask task : pendingTasks) {
            boolean cancelled = checkTaskStateMachine.transitionStatus(
                    task.getId(),
                    CheckTaskStatusEnum.PENDING,
                    CheckTaskStatusEnum.CANCELLED,
                    "论文已撤回，取消待执行查重任务");
            if (!cancelled) {
                log.warn("撤回论文时取消查重任务失败（状态已变化）- paperId={}, taskId={}",
                        paperId, task.getId());
                continue;
            }
            try {
                collegeQuotaService.refundForFailedTask(task.getId());
            } catch (Exception e) {
                log.error("撤回论文时退还查重额度失败 - paperId={}, taskId={}", paperId, task.getId(), e);
            }
        }
    }
    
    /**
     * 撤回后重新提交论文
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaperInfo resubmitAfterWithdraw(Long paperId, PaperReSubmitAfterWithdrawRequest request, Long studentId) {
        try {
            log.info("开始撤回后重新提交 - 论文 ID: {}, 学生 ID: {}", paperId, studentId);
            
            // 1. 验证文件信息
            FileInfo fileInfo = fileService.getById(request.getFileId());
            if (fileInfo == null) {
                throw new BusinessException(ResultCode.BUSINESS_NO_SAFE, "文件不存在或已被删除");
            }
            
            // 2. 更新论文基本信息
            // 撤回后重提只允许从 withdrawn 回到 pending；显式校验，避免任意状态被重置
            PaperInfo currentPaper = paperInfoMapper.selectById(paperId);
            if (currentPaper == null
                    || !DictConstants.PaperStatus.WITHDRAWN.equals(currentPaper.getPaperStatus())) {
                throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS,
                        "仅已撤回的论文可重新提交，当前状态："
                                + (currentPaper == null ? "未知" : currentPaper.getPaperStatus()));
            }
            paperStageGate.guard(DictConstants.PaperStatus.WITHDRAWN,
                    DictConstants.PaperStatus.PENDING, "resubmitAfterWithdraw");
            PaperInfo updatePaper = new PaperInfo();
            updatePaper.setId(paperId);
            updatePaper.setPaperTitle(request.getPaperTitle());
            updatePaper.setPaperAbstract(request.getPaperAbstract());
            // 学位类型可选：不传则保留原论文的学位类型
            if (StringUtils.hasText(request.getDegreeType())) {
                updatePaper.setDegreeType(request.getDegreeType().trim().toLowerCase());
            }
            // 查重读取的是 file_path，必须与主提交链路保持同一份相对路径；
            // 只更新 file_id 会让查重继续比对旧文件。
            String effectiveMd5 = StringUtils.hasText(fileInfo.getMd5())
                    ? fileInfo.getMd5() : request.getFileMd5();
            updatePaper.setFileId(fileInfo.getId());
            updatePaper.setFileMd5(effectiveMd5);
            updatePaper.setFilePath(fileInfo.getStoragePath());
            updatePaper.setWordCount(fileInfo.getWordCount());
            // 撤回后重提也允许改学科。request.subjectCode 这个字段一直存在、前端 submitParams
            // 也一直在传，只是服务层从来没读过 —— 所以老论文撤回重提时改了学科也不生效。
            applySubject(updatePaper, request.getSubjectCode());

            // 3. 获取当前版本号
            Integer currentVersion = getCurrentVersion(paperId);
            Integer newVersion = currentVersion+1;
            // 4. 重置状态为待分配；已有导师时重新进入待确认，查重由导师确认后触发
            updatePaper.setPaperStatus(DictConstants.PaperStatus.PENDING);
            if (currentPaper.getTeacherId() != null) {
                updatePaper.setAllocationStatus(DictConstants.AllocationStatus.PENDING);
            }
            updatePaper.setUpdateTime(LocalDateTime.now());
            
            int updateResult = paperInfoMapper.updateById(updatePaper);
            if (updateResult == 0) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR,"更新论文信息失败");
            }
            
            // 5. 创建新的提交记录
            PaperSubmit submitRecord = new PaperSubmit();
            submitRecord.setPaperId(paperId);
            submitRecord.setStudentId(studentId);
            submitRecord.setSubmitVersion(newVersion);
            submitRecord.setFileId(fileInfo.getId());
            submitRecord.setFileMd5(effectiveMd5);
            submitRecord.setSubmitTime(LocalDateTime.now());
            submitRecord.setRemark("撤回后重新提交");
            int submitResult = paperSubmitMapper.insert(submitRecord);
            if (submitResult == 0) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR,"创建提交记录失败");
            }
            
            // 6. 事务提交后只分配导师/提醒已有导师确认，查重由导师确认触发
            eventPublisher.publishEvent(new PaperSubmittedEvent(this, paperId, studentId));
            
            PaperInfo updatedPaper = paperInfoMapper.selectById(paperId);
            log.info("撤回后重新提交成功 - 论文 ID: {}, 新版本号：{}", paperId, newVersion);
            
            return updatedPaper;
            
        } catch (BusinessException e) {
            log.error("撤回后重新提交失败 - 论文 ID: {}", paperId, e);
            throw e;
        } catch (Exception e) {
            log.error("撤回后重新提交失败 - 论文 ID: {}", paperId, e);
            throw new RuntimeException("重新提交失败：" + e.getMessage());
        }
    }
    
    /**
     * 申请修改已通过论文接口实现
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean requestPaperModification(Long paperId, Long studentId, String reason) {
        try {
            log.info("开始申请修改论文 - 论文ID: {}, 学生ID: {}, 原因: {}", paperId, studentId, reason);
            
            // 1. 验证论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                log.warn("论文不存在 - 论文ID: {}", paperId);
                return false;
            }
            
            // 2. 验证论文归属
            if (!paperInfo.getStudentId().equals(studentId)) {
                log.warn("无权限申请修改他人论文 - 论文ID: {}, 学生ID: {}", paperId, studentId);
                return false;
            }
            
            // 3. 验证论文状态：只有已通过的论文才能申请修改
            if (!DictConstants.PaperStatus.COMPLETED.equals(paperInfo.getPaperStatus())) {
                String statusLabel = getPaperStatusLabel(paperInfo.getPaperStatus());
                log.warn("论文状态不允许申请修改 - 论文ID: {}, 状态: {}", paperId, statusLabel);
                return false;
            }
            
            // 4. 更新论文状态为待审核（申请修改状态）
            paperStageGate.guard(paperInfo.getPaperStatus(),
                    DictConstants.PaperStatus.AUDITING, "applyPaperModify");
            PaperInfo updateInfo = new PaperInfo();
            updateInfo.setId(paperId);
            updateInfo.setPaperStatus(DictConstants.PaperStatus.AUDITING); // 或者自定义一个申请修改状态
            updateInfo.setUpdateTime(LocalDateTime.now());
            
            int result = paperInfoMapper.updateById(updateInfo);
            boolean success = result > 0;
            
            if (success) {
                // 5. 发送申请修改通知
                sendPaperModifyRequestNotification(paperInfo, studentId, reason);
                log.info("申请修改论文成功 - 论文ID: {}", paperId);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("申请修改论文失败 - 论文ID: {}", paperId, e);
            throw new RuntimeException("申请修改失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量下载论文接口实现
     */
    @Override
    public void batchDownloadPapers(List<Long> paperIds, Long studentId, HttpServletResponse response) {
        try {
            log.info("开始批量下载论文 - 学生ID: {}, 论文数量: {}", studentId, paperIds.size());
            
            // 1. 验证论文权限
            List<PaperInfo> papers = validateAndFilterPapers(paperIds, studentId);
            if (papers.isEmpty()) {
                throw new RuntimeException("没有可下载的论文");
            }
            
            // 2. 设置响应头
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=papers_" + 
                System.currentTimeMillis() + ".zip");
            
            // 3. 创建ZIP输出流
            try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
                for (PaperInfo paper : papers) {
                    FileInfo fileInfo = fileInfoMapper.selectById(paper.getFileId());
                    if (fileInfo != null && StringUtils.hasText(fileInfo.getStoragePath())) {
                        // 添加文件到ZIP
                        ZipEntry zipEntry = new ZipEntry(paper.getPaperTitle() + ".pdf");
                        zipOut.putNextEntry(zipEntry);
                        
                        // 从文件系统读取文件内容并写入ZIP
                        String fullPath = Paths.get(uploadBasePath, fileInfo.getStoragePath()).toString();
                        File file = new File(fullPath);
                        if (file.exists()) {
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = fis.read(buffer)) > 0) {
                                    zipOut.write(buffer, 0, len);
                                }
                            }
                        } else {
                            // 文件不存在时写入占位符
                            zipOut.write("文件不存在".getBytes(StandardCharsets.UTF_8));
                        }
                        zipOut.closeEntry();
                    }
                }
            }
            
            log.info("批量下载论文完成 - 学生ID: {}, 成功下载: {}篇", studentId, papers.size());
            
        } catch (Exception e) {
            log.error("批量下载论文失败 - 学生ID: {}", studentId, e);
            throw new RuntimeException("批量下载失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量删除论文接口实现
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchDeletePapers(List<Long> paperIds, Long studentId) {
        Map<String, Object> result = new HashMap<>();
        List<Long> deletedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        
        try {
            log.info("开始批量删除论文 - 学生ID: {}, 论文数量: {}", studentId, paperIds.size());
            
            for (Long paperId : paperIds) {
                try {
                    if (deletePaper(paperId, studentId)) {
                        deletedIds.add(paperId);
                    } else {
                        failedIds.add(paperId);
                    }
                } catch (Exception e) {
                    log.error("删除论文失败 - 论文ID: {}", paperId, e);
                    failedIds.add(paperId);
                }
            }
            
            result.put("deletedCount", deletedIds.size());
            result.put("failedIds", failedIds);
            result.put("success", failedIds.isEmpty());
            
            log.info("批量删除论文完成 - 学生ID: {}, 成功: {}, 失败: {}", 
                studentId, deletedIds.size(), failedIds.size());
            
        } catch (Exception e) {
            log.error("批量删除论文失败 - 学生ID: {}", studentId, e);
            throw new RuntimeException("批量删除失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取论文版本历史列表接口实现
     *
     * <p>此前前端「展开版本历史」调 GET /api/papers/{paperId}/versions 一直 404
     * （后端只有版本详情/对比/下载端点，从未实现列表）。数据以 paper_submit 为准，
     * 按 submit_version 升序，最新一条标记 isCurrent。
     */
    @Override
    public List<PaperVersionItemDTO> getPaperVersions(Long paperId, Long studentId) {
        // 1. 论文归属校验（与 getPaperVersion 同口径）
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "论文不存在或无权限访问");
        }

        // 2. 查询全部提交版本（升序，时间轴按提交先后展示）
        List<PaperSubmit> submits = paperSubmitMapper.selectList(
                new LambdaQueryWrapper<PaperSubmit>()
                        .eq(PaperSubmit::getPaperId, paperId)
                        .orderByAsc(PaperSubmit::getSubmitVersion));

        // 3. 映射 DTO，最后一条（版本号最大）为当前版本
        return submits.stream().map(s -> {
            PaperVersionItemDTO dto = new PaperVersionItemDTO();
            dto.setId(s.getId());
            dto.setPaperId(s.getPaperId());
            dto.setSubmitVersion(s.getSubmitVersion());
            dto.setSubmitTime(s.getSubmitTime());
            dto.setFileId(s.getFileId());
            dto.setRemark(s.getRemark());
            dto.setIsCurrent(s == submits.get(submits.size() - 1));
            return dto;
        }).toList();
    }

    /**
     * 获取论文版本详情接口实现
     */
    @Override
    public PaperVersionDTO getPaperVersion(Long paperId, Long versionId, Long studentId) {
        try {
            log.info("获取论文版本详情 - 论文ID: {}, 版本ID: {}, 学生ID: {}", paperId, versionId, studentId);
            
            // 1. 验证论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new RuntimeException("论文不存在或无权限访问");
            }
            
            // 2. 查询版本信息
            PaperSubmit paperSubmit = paperSubmitMapper.selectById(versionId);
            if (paperSubmit == null || !paperSubmit.getPaperId().equals(paperId)) {
                throw new RuntimeException("版本不存在");
            }
            
            // 3. 构造返回DTO
            PaperVersionDTO versionDTO = new PaperVersionDTO();
            versionDTO.setId(paperSubmit.getId());
            versionDTO.setPaperId(paperSubmit.getPaperId());
            versionDTO.setVersion(paperSubmit.getSubmitVersion());
            versionDTO.setSubmitTime(paperSubmit.getSubmitTime());
            versionDTO.setFileId(paperSubmit.getFileId());
            
            // 4. 判断是否为当前版本
            PaperSubmit latestSubmit = paperSubmitMapper.selectOne(
                new LambdaQueryWrapper<PaperSubmit>()
                    .eq(PaperSubmit::getPaperId, paperId)
                    .orderByDesc(PaperSubmit::getSubmitVersion)
                    .last("LIMIT 1")
            );
            versionDTO.setIsCurrent(latestSubmit != null && latestSubmit.getId().equals(versionId));
            
            // 5. 获取查重信息（如果有）- 从查重任务表查询最新完成的查重记录
            CheckTask latestCheckTask = checkTaskMapper.selectOne(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .orderByDesc(CheckTask::getEndTime)
                    .last("LIMIT 1")
            );
            if (latestCheckTask != null && latestCheckTask.getCheckRate() != null) {
                versionDTO.setSimilarityRate(latestCheckTask.getCheckRate());
            } else {
                versionDTO.setSimilarityRate(paperInfo.getSimilarityRate());
            }
            
            // 6. 获取字数信息
            FileInfo fileInfo = fileInfoMapper.selectById(paperSubmit.getFileId());
            versionDTO.setWordCount(fileInfo != null ? fileInfo.getWordCount() : 0);
            
            return versionDTO;
            
        } catch (Exception e) {
            log.error("获取论文版本详情失败 - 论文ID: {}, 版本ID: {}", paperId, versionId, e);
            throw new RuntimeException("获取版本详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 版本对比接口实现
     */
    @Override
    public VersionCompareResult comparePaperVersions(Long paperId, List<Long> versionIds, Long studentId) {
        try {
            log.info("开始版本对比 - 论文ID: {}, 版本数量: {}, 学生ID: {}", paperId, versionIds.size(), studentId);
            
            if (versionIds.size() != 2) {
                throw new RuntimeException("必须选择两个版本进行对比");
            }
            
            // 1. 验证论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new RuntimeException("论文不存在或无权限访问");
            }
            
            // 2. 获取两个版本信息
            PaperSubmit versionA = paperSubmitMapper.selectById(versionIds.get(0));
            PaperSubmit versionB = paperSubmitMapper.selectById(versionIds.get(1));
            
            if (versionA == null || versionB == null || 
                !versionA.getPaperId().equals(paperId) || !versionB.getPaperId().equals(paperId)) {
                throw new RuntimeException("版本信息不正确");
            }
            
            // 3. 构造对比结果
            VersionCompareResult result = new VersionCompareResult();
                        
            // 版本 A 信息 - 从该版本提交时间之前的查重记录获取相似度
            VersionCompareResult.VersionInfo infoA = new VersionCompareResult.VersionInfo();
            infoA.setId(versionA.getId());
            infoA.setVersion(versionA.getSubmitVersion());
            // 查询该版本对应的查重记录（根据提交时间匹配）
            CheckTask checkTaskA = checkTaskMapper.selectOne(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .le(CheckTask::getEndTime, versionA.getSubmitTime())
                    .orderByDesc(CheckTask::getEndTime)
                    .last("LIMIT 1")
            );
            if (checkTaskA != null && checkTaskA.getCheckRate() != null) {
                infoA.setSimilarityRate(checkTaskA.getCheckRate());
            } else {
                infoA.setSimilarityRate(paperInfo.getSimilarityRate());
            }
            FileInfo fileInfoA = fileInfoMapper.selectById(versionA.getFileId());
            infoA.setWordCount(fileInfoA != null ? fileInfoA.getWordCount() : 0);
            result.setVersionA(infoA);
                        
            // 版本 B 信息 - 同理从历史查重记录获取
            VersionCompareResult.VersionInfo infoB = new VersionCompareResult.VersionInfo();
            infoB.setId(versionB.getId());
            infoB.setVersion(versionB.getSubmitVersion());
            CheckTask checkTaskB = checkTaskMapper.selectOne(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .le(CheckTask::getEndTime, versionB.getSubmitTime())
                    .orderByDesc(CheckTask::getEndTime)
                    .last("LIMIT 1")
            );
            if (checkTaskB != null && checkTaskB.getCheckRate() != null) {
                infoB.setSimilarityRate(checkTaskB.getCheckRate());
            } else {
                infoB.setSimilarityRate(paperInfo.getSimilarityRate());
            }
            FileInfo fileInfoB = fileInfoMapper.selectById(versionB.getFileId());
            infoB.setWordCount(fileInfoB != null ? fileInfoB.getWordCount() : 0);
            result.setVersionB(infoB);
            
            // 4. 计算差异
            List<VersionCompareResult.DiffItem> diffItems = new ArrayList<>();
            
            // 相似度差异
            VersionCompareResult.DiffItem similarityDiff = new VersionCompareResult.DiffItem();
            similarityDiff.setField("相似度");
            similarityDiff.setBefore(infoA.getSimilarityRate());
            similarityDiff.setAfter(infoB.getSimilarityRate());
            if (infoA.getSimilarityRate() != null && infoB.getSimilarityRate() != null) {
                similarityDiff.setChange(infoB.getSimilarityRate().subtract(infoA.getSimilarityRate()));
            }
            diffItems.add(similarityDiff);
            
            // 字数差异
            VersionCompareResult.DiffItem wordCountDiff = new VersionCompareResult.DiffItem();
            wordCountDiff.setField("字数");
            wordCountDiff.setBefore(infoA.getWordCount());
            wordCountDiff.setAfter(infoB.getWordCount());
            wordCountDiff.setChange(infoB.getWordCount() - infoA.getWordCount());
            diffItems.add(wordCountDiff);
            
            result.setDiffData(diffItems);
            
            log.info("版本对比完成 - 论文ID: {}", paperId);
            return result;
            
        } catch (Exception e) {
            log.error("版本对比失败 - 论文ID: {}", paperId, e);
            throw new RuntimeException("版本对比失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载版本对比报告接口实现
     */
    @Override
    public void downloadVersionCompareReport(Long paperId, List<Long> versionIds, Long studentId, HttpServletResponse response) {
        try {
            log.info("开始下载版本对比报告 - 论文ID: {}, 学生ID: {}", paperId, studentId);
            
            // 1. 执行版本对比
            VersionCompareResult compareResult = comparePaperVersions(paperId, versionIds, studentId);
            
            // 2. 生成对比报告（这里简单返回JSON格式，实际应该生成PDF）
            response.setContentType("application/json");
            response.setHeader("Content-Disposition", "attachment; filename=compare_report_" + 
                System.currentTimeMillis() + ".json");
            
            String jsonReport = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(compareResult);
            response.getWriter().write(jsonReport);
            response.getWriter().flush();
            
            log.info("版本对比报告下载完成 - 论文ID: {}", paperId);
            
        } catch (Exception e) {
            log.error("下载版本对比报告失败 - 论文ID: {}", paperId, e);
            throw new RuntimeException("下载对比报告失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载论文版本接口实现
     */
    @Override
    public void downloadPaperVersion(Long versionId, Long studentId, HttpServletResponse response) {
        try {
            log.info("开始下载论文版本 - 版本ID: {}, 学生ID: {}", versionId, studentId);
            
            // 1. 查询版本信息
            PaperSubmit paperSubmit = paperSubmitMapper.selectById(versionId);
            if (paperSubmit == null) {
                throw new RuntimeException("版本不存在");
            }
            
            // 2. 验证论文归属
            PaperInfo paperInfo = paperInfoMapper.selectById(paperSubmit.getPaperId());
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new RuntimeException("无权限下载此版本");
            }
            
            // 3. 下载文件 - 直接操作response
            FileInfo fileInfo = fileService.getById(paperSubmit.getFileId());
            if (fileInfo != null && StringUtils.hasText(fileInfo.getStoragePath())) {
                String fullPath = Paths.get(uploadBasePath, fileInfo.getStoragePath()).toString();
                File file = new File(fullPath);
                
                if (file.exists()) {
                    // 设置响应头
                    String fileName = fileInfo.getOriginalFilename() != null ? 
                        fileInfo.getOriginalFilename() : "paper_version_" + versionId + ".pdf";
                    response.setContentType(getContentType(fileName));
                    response.setHeader("Content-Disposition", 
                        "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
                    response.setContentLength((int) file.length());
                    
                    // 写入文件内容
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            response.getOutputStream().write(buffer, 0, len);
                        }
                        response.getOutputStream().flush();
                    }
                } else {
                    throw new RuntimeException("文件不存在");
                }
            } else {
                throw new RuntimeException("文件信息不存在");
            }
            
            log.info("论文版本下载完成 - 版本ID: {}", versionId);
            
        } catch (Exception e) {
            log.error("下载论文版本失败 - 版本ID: {}", versionId, e);
            throw new RuntimeException("下载版本失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载附件接口实现
     */
    @Override
    public void downloadAttachment(String attachmentId, Long studentId, HttpServletResponse response) {
        try {
            log.info("开始下载附件 - 附件ID: {}, 学生ID: {}", attachmentId, studentId);
            
            // 1. 验证附件存在且属于该学生
            PaperAttachment attachment = paperAttachmentMapper.selectById(attachmentId);
            if (attachment == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "附件不存在");
            }
            if (!attachment.getStudentId().equals(studentId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该附件");
            }
                        
            // 2. 下载文件 - 直接操作 response
            String fullPath = Paths.get(uploadBasePath, attachment.getStoragePath()).toString();
            File file = new File(fullPath);
            
            if (!file.exists()) {
                throw new RuntimeException("附件文件不存在");
            }
            
            // 设置响应头
            String fileName = attachment.getOriginalFilename() != null ? 
                attachment.getOriginalFilename() : "attachment_" + attachmentId;
            response.setContentType(getContentType(fileName));
            response.setHeader("Content-Disposition", 
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
            response.setContentLength((int) file.length());
            
            // 写入文件内容
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    response.getOutputStream().write(buffer, 0, len);
                }
                response.getOutputStream().flush();
            }
            
            log.info("附件下载完成 - 附件ID: {}", attachmentId);
            
        } catch (Exception e) {
            log.error("下载附件失败 - 附件ID: {}", attachmentId, e);
            throw new RuntimeException("下载附件失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载论文接口实现
     */
    @Override
    public void downloadPaper(Long paperId, Long studentId, HttpServletResponse response) {
        try {
            log.info("开始下载论文 - 论文ID: {}, 学生ID: {}", paperId, studentId);
            
            // 1. 查询论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                throw new RuntimeException("论文不存在");
            }
            
            // 2. 验证论文归属
            if (!paperInfo.getStudentId().equals(studentId)) {
                throw new RuntimeException("无权限下载此论文");
            }
            
            // 3. 检查论文状态（只有通过审核的论文才能下载）
            if (!"completed".equals(paperInfo.getPaperStatus())) {
                throw new RuntimeException("论文未通过审核，无法下载");
            }
            
            // 4. 下载文件 - 直接操作response
            FileInfo fileInfo = fileService.getById(paperInfo.getFileId());
            if (fileInfo != null && StringUtils.hasText(fileInfo.getStoragePath())) {
                String fullPath = Paths.get(uploadBasePath, fileInfo.getStoragePath()).toString();
                File file = new File(fullPath);
                
                if (file.exists()) {
                    // 设置响应头
                    String fileName = fileInfo.getOriginalFilename() != null ? 
                        fileInfo.getOriginalFilename() : "paper_" + paperId + ".pdf";
                    response.setContentType(getContentType(fileName));
                    response.setHeader("Content-Disposition", 
                        "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
                    response.setContentLength((int) file.length());
                    
                    // 写入文件内容
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            response.getOutputStream().write(buffer, 0, len);
                        }
                        response.getOutputStream().flush();
                    }
                    
                    log.info("论文下载完成 - 论文ID: {}, 文件名: {}", paperId, fileName);
                } else {
                    throw new RuntimeException("论文文件不存在");
                }
            } else {
                throw new RuntimeException("论文文件信息不存在");
            }
            
        } catch (Exception e) {
            log.error("下载论文失败 - 论文ID: {}", paperId, e);
            throw new RuntimeException("下载论文失败: " + e.getMessage());
        }
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
        } else if (lowerName.endsWith(".txt")) {
            return "text/plain";
        } else {
            return "application/octet-stream";
        }
    }
    
    /**
     * 验证并过滤论文列表
     */
    private List<PaperInfo> validateAndFilterPapers(List<Long> paperIds, Long studentId) {
        List<PaperInfo> validPapers = new ArrayList<>();
        
        for (Long paperId : paperIds) {
            PaperInfo paper = paperInfoMapper.selectById(paperId);
            if (paper != null && paper.getStudentId().equals(studentId) && 
                paper.getIsDeleted() == 0) {
                validPapers.add(paper);
            }
        }
        
        return validPapers;
    }
    
    /**
     * 发送论文撤回成功通知
     */
    private void sendPaperWithdrawSuccessNotification(PaperInfo paperInfo, Long studentId, String reason) {
        try {
            String title = "论文撤回成功";
            String content = String.format("您的论文《%s》已成功撤回。%s",
                paperInfo.getPaperTitle(),
                StringUtils.hasText(reason) ? "撤回原因：" + reason : "");
            
            notificationService.sendSystemNotice(
                studentId,
                title,
                content,
                PaperNoticeConstants.NOTICE_TYPE_PAPER_WITHDRAW_SUCCESS,
                paperInfo.getId()
            );
        } catch (Exception e) {
            log.error("发送论文撤回成功通知失败", e);
        }
    }
    
    /**
     * 发送申请修改论文通知
     */
    private void sendPaperModifyRequestNotification(PaperInfo paperInfo, Long studentId, String reason) {
        try {
            String title = "修改申请已提交";
            String content = String.format("您对论文《%s》的修改申请已提交，等待导师审核。%s",
                paperInfo.getPaperTitle(),
                StringUtils.hasText(reason) ? "申请原因：" + reason : "");
            
            notificationService.sendSystemNotice(
                studentId,
                title,
                content,
                PaperNoticeConstants.NOTICE_TYPE_PAPER_MODIFY_REQUEST,
                paperInfo.getId()
            );
        } catch (Exception e) {
            log.error("发送申请修改论文通知失败", e);
        }
    }
    
    // ==================== 查重历史相关方法 ====================
    
    @Override
    public CheckHistoryResponseDTO getCheckHistory(Long paperId, Long studentId) {
        try {
            log.info("获取论文查重历史记录 - 论文ID: {}, 学生ID: {}", paperId, studentId);
            
            // 1. 验证论文权限
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该论文");
            }
            
            // 2. 查询所有查重任务历史
            List<CheckTask> checkTasks = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .orderByDesc(CheckTask::getCreateTime)
            );
            
            // 3. 构建历史记录
            List<CheckHistoryDTO> history = new ArrayList<>();
            BigDecimal lowestSimilarity = null;
            BigDecimal currentSimilarity = null;
            
            for (int i = 0; i < checkTasks.size(); i++) {
                CheckTask task = checkTasks.get(i);
                CheckHistoryDTO historyDTO = new CheckHistoryDTO();
                
                // 版本号（按时间倒序）
                historyDTO.setVersion(checkTasks.size() - i);
                
                // 报告信息
                if (task.getReportId() != null) {
                    CheckReport report = checkReportMapper.selectById(task.getReportId());
                    if (report != null) {
                        historyDTO.setReportId(report.getReportNo());
                    }
                }
                
                // 查重时间和相似度
                historyDTO.setCheckTime(task.getCreateTime());
                historyDTO.setSimilarity(task.getCheckRate());
                
                // 评级
                historyDTO.setRating(calculateRating(task.getCheckRate()));
                
                // 是否为当前版本
                historyDTO.setIsCurrent(i == 0);
                if (i == 0) {
                    currentSimilarity = task.getCheckRate();
                }
                
                // 改进说明：按相邻两版的真实相似度差值生成，不再套固定文案
                BigDecimal previousSimilarity = (i + 1 < checkTasks.size())
                        ? checkTasks.get(i + 1).getCheckRate() : null;
                historyDTO.setChanges(generateChangesDescription(i, task.getCheckRate(), previousSimilarity));
                
                // 相比上一版本的改进
                if (i < checkTasks.size() - 1) {
                    CheckTask previousTask = checkTasks.get(i + 1);
                    BigDecimal improvement = previousTask.getCheckRate().subtract(task.getCheckRate());
                    historyDTO.setImprovementFromPrevious(improvement);
                }
                
                // 章节变化 - 从查重报告中提取真实数据
                historyDTO.setSectionChanges(extractSectionChangesFromReport(task));
                
                history.add(historyDTO);
                
                // 更新最低相似度
                if (lowestSimilarity == null || task.getCheckRate().compareTo(lowestSimilarity) < 0) {
                    lowestSimilarity = task.getCheckRate();
                }
            }
            
            // 4. 构建趋势分析
            CheckHistoryResponseDTO.TrendAnalysisDTO trendAnalysis = buildTrendAnalysis(checkTasks);
            
            // 5. 构建论文信息
            CheckHistoryResponseDTO.PaperInfoDTO paperInfoDTO = new CheckHistoryResponseDTO.PaperInfoDTO();
            paperInfoDTO.setTitle(paperInfo.getPaperTitle());
            paperInfoDTO.setCurrentSimilarity(currentSimilarity);
            paperInfoDTO.setLowestSimilarity(lowestSimilarity);
            paperInfoDTO.setVersionCount(checkTasks.size());
            
            // 6. 构建最终响应
            CheckHistoryResponseDTO response = new CheckHistoryResponseDTO();
            response.setHistory(history);
            response.setTrendAnalysis(trendAnalysis);
            response.setPaperInfo(paperInfoDTO);
            
            log.info("查重历史记录获取成功 - 论文ID: {}, 记录数: {}", paperId, history.size());
            return response;
            
        } catch (Exception e) {
            log.error("获取查重历史记录失败 - 论文ID: {}", paperId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取查重历史记录失败");
        }
    }
    
    @Override
    public SimilarityTrendDTO getSimilarityTrend(Long paperId, Long studentId, Integer period) {
        try {
            log.info("获取相似度趋势数据 - 论文ID: {}, 学生ID: {}, 周期: {}天", paperId, studentId, period);
            
            // 1. 验证权限
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该论文");
            }
            
            // 2. 计算时间范围
            LocalDateTime endDate = LocalDateTime.now();
            LocalDateTime startDate = endDate.minusDays(period);
            
            // 3. 查询指定时间范围内的查重任务
            List<CheckTask> checkTasks = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .ge(CheckTask::getCreateTime, startDate)
                    .le(CheckTask::getCreateTime, endDate)
                    .eq(CheckTask::getIsDeleted, 0)
                    .orderByAsc(CheckTask::getCreateTime)
            );
            
            // 4. 构建趋势数据
            List<String> dates = new ArrayList<>();
            List<BigDecimal> similarities = new ArrayList<>();
            
            for (CheckTask task : checkTasks) {
                dates.add(task.getCreateTime().toLocalDate().toString());
                similarities.add(task.getCheckRate());
            }
            
            // 5. 没有数据时如实返回空数组，不再伪造一条 0% 的数据点

            SimilarityTrendDTO trendDTO = new SimilarityTrendDTO();
            trendDTO.setDates(dates);
            trendDTO.setSimilarities(similarities);
            
            log.info("相似度趋势数据获取成功 - 论文ID: {}, 数据点数: {}", paperId, dates.size());
            return trendDTO;
            
        } catch (Exception e) {
            log.error("获取相似度趋势数据失败 - 论文ID: {}", paperId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取相似度趋势数据失败");
        }
    }
    
    @Override
    public VersionCompareResponseDTO compareVersions(Long paperId, Long studentId, VersionCompareRequestDTO request) {
        try {
            log.info("版本对比分析 - 论文ID: {}, 学生ID: {}, 从版本: {}, 到版本: {}", 
                paperId, studentId, request.getFromVersion(), request.getToVersion());
            
            // 1. 验证权限
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该论文");
            }
            
            // 2. 查询所有查重任务
            List<CheckTask> checkTasks = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .orderByDesc(CheckTask::getCreateTime)
            );
            
            // 3. 验证版本号
            if (request.getFromVersion() < 1 || request.getToVersion() < 1 ||
                request.getFromVersion() > checkTasks.size() || request.getToVersion() > checkTasks.size()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "版本号超出范围");
            }
            
            // 4. 获取指定版本的任务
            int fromIndex = checkTasks.size() - request.getFromVersion();
            int toIndex = checkTasks.size() - request.getToVersion();
            
            CheckTask fromTask = checkTasks.get(fromIndex);
            CheckTask toTask = checkTasks.get(toIndex);
            
            // 5. 计算总体变化
            BigDecimal overallChange = fromTask.getCheckRate().subtract(toTask.getCheckRate());
            
            // 6. 构建章节对比 - 从查重报告中提取章节信息（如果有）
            List<VersionCompareResponseDTO.SectionComparisonDTO> sectionComparisons = new ArrayList<>();
            
            // 尝试从两个版本的查重报告中提取章节对比数据
            CheckReport reportFrom = checkReportMapper.selectOne(
                new LambdaQueryWrapper<CheckReport>()
                    .eq(CheckReport::getTaskId, fromTask.getId())
                    .eq(CheckReport::getIsDeleted, 0)
                    .last("LIMIT 1")
            );
            
            CheckReport reportTo = checkReportMapper.selectOne(
                new LambdaQueryWrapper<CheckReport>()
                    .eq(CheckReport::getTaskId, toTask.getId())
                    .eq(CheckReport::getIsDeleted, 0)
                    .last("LIMIT 1")
            );
            
            // 如果报告中有 repeatDetails，尝试解析章节信息
            if (reportFrom != null && reportTo != null && 
                StringUtils.hasText(reportFrom.getRepeatDetails()) && 
                StringUtils.hasText(reportTo.getRepeatDetails())) {
                try {
                    // 解析 JSON 格式的重复详情
                    List<Map<String, Object>> detailsFrom = JSON.parseObject(
                        reportFrom.getRepeatDetails(), 
                        new TypeReference<List<Map<String, Object>>>() {}
                    );
                    List<Map<String, Object>> detailsTo = JSON.parseObject(
                        reportTo.getRepeatDetails(), 
                        new TypeReference<List<Map<String, Object>>>() {}
                    );
                    
                    // 从详情中提取章节信息（如果有的话）
                    // 这里简化处理，实际应该根据具体的章节结构解析
                    sectionComparisons = buildSectionComparisons(detailsFrom, detailsTo);
                } catch (Exception e) {
                    log.warn("解析章节对比数据失败，章节对比将返回空列表");
                }
            }
            
            // 报告中没有章节字段时不编造章节对比，如实返回空列表（前端隐藏章节模块）
            
            // 7. 构建响应
            VersionCompareResponseDTO response = new VersionCompareResponseDTO();
            response.setFromVersion(request.getFromVersion());
            response.setToVersion(request.getToVersion());
            response.setOverallChange(overallChange);
            response.setSectionComparison(sectionComparisons);
            
            log.info("版本对比分析完成 - 论文ID: {}, 总体变化: {}", paperId, overallChange);
            return response;
            
        } catch (Exception e) {
            log.error("版本对比分析失败 - 论文ID: {}", paperId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "版本对比分析失败");
        }
    }
    
    @Override
    public StatisticsDTO getPaperStatistics(Long paperId, Long studentId) {
        try {
            log.info("获取论文统计分析数据 - 论文ID: {}, 学生ID: {}", paperId, studentId);
            
            // 1. 验证权限
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || !paperInfo.getStudentId().equals(studentId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该论文");
            }
            
            // 2. 查询所有查重任务
            List<CheckTask> checkTasks = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .orderByAsc(CheckTask::getCreateTime)
            );
            
            if (checkTasks.isEmpty()) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "暂无查重记录");
            }
            
            // 3. 计算统计数据
            BigDecimal firstSimilarity = checkTasks.get(0).getCheckRate();
            BigDecimal latestSimilarity = checkTasks.get(checkTasks.size() - 1).getCheckRate();
            
            // 总改进率
            BigDecimal improvement = firstSimilarity.subtract(latestSimilarity);
            int improvementRate = firstSimilarity.compareTo(BigDecimal.ZERO) > 0 ?
                improvement.multiply(BigDecimal.valueOf(100))
                          .divide(firstSimilarity, 0, BigDecimal.ROUND_HALF_UP)
                          .intValue() :
                0;
            
            // 平均相似度
            BigDecimal sum = BigDecimal.ZERO;
            for (CheckTask task : checkTasks) {
                sum = sum.add(task.getCheckRate());
            }
            BigDecimal averageSimilarity = sum.divide(BigDecimal.valueOf(checkTasks.size()), 2, BigDecimal.ROUND_HALF_UP);
            
            // 改进速度评估
            String improvementSpeed = evaluateImprovementSpeed(checkTasks);
            
            // 4. 构建响应
            StatisticsDTO statistics = new StatisticsDTO();
            statistics.setImprovementRate(improvementRate);
            statistics.setAverageSimilarity(averageSimilarity);
            statistics.setImprovementSpeed(improvementSpeed);
            statistics.setTotalChecks(checkTasks.size());
            statistics.setFirstCheckSimilarity(firstSimilarity);
            statistics.setLatestCheckSimilarity(latestSimilarity);
            
            log.info("论文统计分析完成 - 论文ID: {}, 总查重次数: {}", paperId, checkTasks.size());
            return statistics;
            
        } catch (Exception e) {
            log.error("获取论文统计分析数据失败 - 论文ID: {}", paperId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取统计分析数据失败");
        }
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 计算评级
     */
    private String calculateRating(BigDecimal similarity) {
        if (similarity == null) return "unknown";
        
        double rate = similarity.doubleValue();
        if (rate <= 10) return "excellent";
        if (rate <= 20) return "good";
        if (rate <= 30) return "fair";
        return "poor";
    }
    
    /**
     * 构建章节对比数据（从查重报告详情中提取）
     */
    private List<VersionCompareResponseDTO.SectionComparisonDTO> buildSectionComparisons(
            List<Map<String, Object>> detailsFrom, 
            List<Map<String, Object>> detailsTo) {
        
        List<VersionCompareResponseDTO.SectionComparisonDTO> comparisons = new ArrayList<>();
        
        try {
            // 尝试从详情中提取章节信息
            // 这里假设 detail 中包含 section 字段
            for (Map<String, Object> detail : detailsFrom) {
                if (detail.containsKey("section")) {
                    String sectionName = detail.get("section").toString();
                    BigDecimal fromRate = detail.containsKey("similarity") ? 
                        new BigDecimal(detail.get("similarity").toString()) : BigDecimal.ZERO;
                    
                    // 查找对应章节在 toVersion 中的数据
                    BigDecimal toRate = BigDecimal.ZERO;
                    for (Map<String, Object> toDetail : detailsTo) {
                        if (sectionName.equals(toDetail.get("section"))) {
                            toRate = toDetail.containsKey("similarity") ? 
                                new BigDecimal(toDetail.get("similarity").toString()) : BigDecimal.ZERO;
                            break;
                        }
                    }
                    
                    VersionCompareResponseDTO.SectionComparisonDTO dto = 
                        new VersionCompareResponseDTO.SectionComparisonDTO();
                    dto.setName(sectionName);
                    dto.setFrom(fromRate);
                    dto.setTo(toRate);
                    dto.setChange(fromRate.subtract(toRate));
                    
                    comparisons.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("构建章节对比数据失败", e);
        }
        
        return comparisons;
    }
    
    /**
     * 生成修改说明
     */
    private String generateChangesDescription(int versionIndex, BigDecimal currentSimilarity,
                                              BigDecimal previousSimilarity) {
        if (versionIndex == 0) {
            return "初次提交查重";
        }
        if (currentSimilarity == null || previousSimilarity == null) {
            return "缺少上一版本查重数据，无法对比";
        }
        BigDecimal change = previousSimilarity.subtract(currentSimilarity);
        int cmp = change.compareTo(BigDecimal.ZERO);
        if (cmp == 0) {
            return String.format("与上一版本相似度持平（%.2f%%）", currentSimilarity.doubleValue());
        }
        return String.format("相似度由 %.2f%% %s %.2f%%（%s %.2f 个百分点）",
                previousSimilarity.doubleValue(), cmp > 0 ? "降至" : "升至",
                currentSimilarity.doubleValue(), cmp > 0 ? "下降" : "上升",
                change.abs().doubleValue());
    }
    
    /**
     * 从查重报告中提取章节变化数据
     */
    private Map<String, CheckHistoryDTO.SectionChangeDTO> extractSectionChangesFromReport(CheckTask task) {
        Map<String, CheckHistoryDTO.SectionChangeDTO> sectionChanges = new HashMap<>();
        
        try {
            // 1. 查询查重报告
            if (task.getReportId() == null) {
                return sectionChanges; // 没有报告，返回空数据
            }
            
            CheckReport report = checkReportMapper.selectById(task.getReportId());
            if (report == null || !StringUtils.hasText(report.getRepeatDetails())) {
                return sectionChanges; // 报告不存在或没有重复详情，返回空数据
            }
            
            // 2. 解析 JSON 格式的重复详情
            List<Map<String, Object>> details = JSON.parseObject(
                report.getRepeatDetails(), 
                new TypeReference<List<Map<String, Object>>>() {}
            );
            
            if (details == null || details.isEmpty()) {
                return sectionChanges;
            }
            
            // 3. 尝试从详情中提取章节信息
            // 假设 detail 中可能包含 section、chapter 等字段
            for (Map<String, Object> detail : details) {
                String sectionName = null;
                BigDecimal similarity = null;
                
                // 尝试不同的字段名获取章节名称
                if (detail.containsKey("section")) {
                    sectionName = detail.get("section").toString();
                } else if (detail.containsKey("chapter")) {
                    sectionName = detail.get("chapter").toString();
                } else if (detail.containsKey("source")) {
                    // 如果没有明确的章节名，使用来源作为标识
                    sectionName = detail.get("source").toString();
                }
                
                // 获取相似度
                if (detail.containsKey("similarity")) {
                    Object simValue = detail.get("similarity");
                    if (simValue instanceof Number) {
                        similarity = new BigDecimal(simValue.toString());
                    } else if (simValue instanceof String) {
                        try {
                            similarity = new BigDecimal((String) simValue);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析相似度值：{}", simValue);
                        }
                    }
                }
                
                // 如果提取到有效数据，构建 SectionChangeDTO
                if (sectionName != null && similarity != null) {
                    CheckHistoryDTO.SectionChangeDTO sectionChange = new CheckHistoryDTO.SectionChangeDTO();
                    sectionChange.setFrom(similarity); // 当前版本的相似度
                    sectionChange.setTo(similarity);   // 单篇论文比对，from 和 to 相同
                    sectionChange.setChange(BigDecimal.ZERO); // 单版本内无变化
                    
                    sectionChanges.put(sectionName, sectionChange);
                }
            }
            
            // 4. 报告中没有章节信息时如实返回空数据，不再按随机波动编造章节
        } catch (Exception e) {
            log.error("从查重报告提取章节变化失败 - taskId: {}", task.getId(), e);
        }
        
        return sectionChanges;
    }
    
    /**
     * 构建趋势分析
     */
    private CheckHistoryResponseDTO.TrendAnalysisDTO buildTrendAnalysis(List<CheckTask> checkTasks) {
        CheckHistoryResponseDTO.TrendAnalysisDTO trend = new CheckHistoryResponseDTO.TrendAnalysisDTO();
        
        if (checkTasks.size() < 2) {
            trend.setDirection("stable");
            trend.setTotalImprovement(BigDecimal.ZERO);
            trend.setAverageImprovementPerVersion(BigDecimal.ZERO);
            trend.setBestVersion(1);
            return trend;
        }
        
        // 计算总改进值
        BigDecimal firstRate = checkTasks.get(checkTasks.size() - 1).getCheckRate();
        BigDecimal lastRate = checkTasks.get(0).getCheckRate();
        BigDecimal totalImprovement = firstRate.subtract(lastRate);
        
        // 判断趋势方向
        String direction = totalImprovement.compareTo(BigDecimal.ZERO) < 0 ? "decreasing" : 
                          totalImprovement.compareTo(BigDecimal.ZERO) > 0 ? "increasing" : "stable";
        
        // 计算平均改进值
        BigDecimal averageImprovement = totalImprovement.divide(
            BigDecimal.valueOf(checkTasks.size() - 1), 2, BigDecimal.ROUND_HALF_UP);
        
        // 找到最佳版本
        int bestVersion = 1;
        BigDecimal lowestRate = firstRate;
        for (int i = 0; i < checkTasks.size(); i++) {
            BigDecimal rate = checkTasks.get(i).getCheckRate();
            if (rate.compareTo(lowestRate) < 0) {
                lowestRate = rate;
                bestVersion = checkTasks.size() - i;
            }
        }
        
        trend.setDirection(direction);
        trend.setTotalImprovement(totalImprovement.abs());
        trend.setAverageImprovementPerVersion(averageImprovement.abs());
        trend.setBestVersion(bestVersion);
        
        return trend;
    }
    
    /**
     * 评估改进速度
     */
    private String evaluateImprovementSpeed(List<CheckTask> checkTasks) {
        if (checkTasks.size() < 2) return "暂无数据";
        
        BigDecimal totalImprovement = checkTasks.get(0).getCheckRate()
            .subtract(checkTasks.get(checkTasks.size() - 1).getCheckRate());
        
        double avgImprovement = Math.abs(totalImprovement.doubleValue() / (checkTasks.size() - 1));
        
        if (avgImprovement >= 10) return "很快";
        if (avgImprovement >= 5) return "较快";
        if (avgImprovement >= 2) return "一般";
        return "较慢";
    }
}