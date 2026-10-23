package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.service.AttachmentDownloadService;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.utils.ConversationIdUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.student.dto.ChatExportDTO;
import com.abin.checkrepeatservice.student.dto.FileUploadVO;
import com.abin.checkrepeatservice.student.dto.MessageSendDTO;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.vo.MessageSessionVO;
import com.abin.checkrepeatservice.student.vo.MessageVO;
import com.abin.checkrepeatservice.student.vo.SharedFileVO;
import com.abin.checkrepeatservice.teacher.service.TeacherMessageService;
import com.abin.checkrepeatservice.teacher.vo.StudentInfoVO;
import com.abin.checkrepeatservice.user.mapper.InstantMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 教师端消息服务实现。
 *
 * <p>与学生端共用 {@code messages} 表与同一套会话号规则，但**没有**复用
 * {@code StudentMessageServiceImpl}：那个实现里的角色假设（会话从 paper_info.student_id 反查、
 * 已读时靠 senderId 反推对方、发送 VO 硬编码 STUDENT）在教师视角下是错的，
 * 复用它只会把错误一起搬过来。这里按教师视角重写，形状对齐、语义重做。</p>
 */
@Slf4j
@Service
public class TeacherMessageServiceImpl implements TeacherMessageService {

    /** 消息状态：未读 / 已读（与 messages.status 列上的注释一致） */
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_READ = "READ";

    @Resource
    private InstantMessageMapper instantMessageMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private FileService fileService;

    @Resource
    private AttachmentDownloadService attachmentDownloadService;

    @Value("${file.upload.base-path}")
    private String uploadBasePath;

    // ------------------------------------------------------------------ 会话

    @Override
    public List<MessageSessionVO> getMessageSessions(Long teacherId) {
        log.info("获取教师消息会话列表 - 教师ID: {}", teacherId);

        List<Long> studentIds = listMyStudentIds(teacherId);
        if (studentIds.isEmpty()) {
            log.info("教师 {} 名下暂无学生，会话列表为空", teacherId);
            return Collections.emptyList();
        }

        SysUser teacher = sysUserMapper.selectById(teacherId);
        String teacherName = displayName(teacher);

        List<MessageSessionVO> sessions = new ArrayList<>();
        for (Long studentId : studentIds) {
            SysUser student = sysUserMapper.selectById(studentId);
            String studentName = displayName(student);
            Long conversationId = ConversationIdUtils.of(teacherId, studentId);

            MessageSessionVO session = new MessageSessionVO();
            session.setId(conversationId);
            // 前端拿 session.name.charAt(0) 当头像占位，所以这里绝不能是 null
            session.setName("与" + studentName + "的会话");
            session.setType("PRIVATE");
            session.setAvatar(student != null ? student.getAvatar() : null);

            List<MessageSessionVO.SessionMemberVO> members = new ArrayList<>();
            members.add(toMember(student, studentId, "STUDENT", studentName));
            members.add(toMember(teacher, teacherId, "TEACHER", teacherName));
            session.setMembers(members);

            InstantMessage lastMessage = getLastMessage(conversationId);
            session.setLastMessage(lastMessage != null ? summarize(lastMessage) : "");
            session.setLastTime(lastMessage != null && lastMessage.getSentTime() != null
                    ? lastMessage.getSentTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : "");
            session.setUnreadCount(countUnread(conversationId, teacherId));

            sessions.add(session);
        }

        // 有新消息的排前面；ISO 时间串可以直接按字典序比较
        sessions.sort((a, b) -> {
            String left = a.getLastTime();
            String right = b.getLastTime();
            if (!StringUtils.hasText(left)) {
                return StringUtils.hasText(right) ? 1 : 0;
            }
            if (!StringUtils.hasText(right)) {
                return -1;
            }
            return right.compareTo(left);
        });

        log.info("教师消息会话列表获取成功 - 教师ID: {}, 会话数: {}", teacherId, sessions.size());
        return sessions;
    }

    // ------------------------------------------------------------------ 消息

    @Override
    public Page<MessageVO> getMessageList(Long teacherId, Long sessionId, Integer pageNum, Integer pageSize) {
        requireMyConversation(teacherId, sessionId);
        log.info("获取教师消息列表 - 教师ID: {}, 会话ID: {}", teacherId, sessionId);

        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, sessionId)
                .eq(InstantMessage::getIsDeleted, 0)
                // 升序：聊天窗口自上而下读，最新的在底部，与页面的 scrollToBottom 一致
                .orderByAsc(InstantMessage::getSentTime);

        Page<InstantMessage> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        Page<InstantMessage> dbPage = instantMessageMapper.selectPage(page, wrapper);

        Page<MessageVO> result = new Page<>(dbPage.getCurrent(), dbPage.getSize(), dbPage.getTotal());
        result.setRecords(dbPage.getRecords().stream()
                .map(message -> toMessageVO(message, teacherId))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(MessageSendDTO sendDTO) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        if (sendDTO.getSessionId() == null || sendDTO.getReceiverId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "会话 ID 与接收者不能为空", null);
        }
        requireMyConversation(currentUserId, sendDTO.getSessionId());

        InstantMessage message = new InstantMessage();
        message.setSenderId(currentUserId);
        message.setReceiverId(sendDTO.getReceiverId());
        message.setConversationId(sendDTO.getSessionId());
        message.setContent(sendDTO.getContent());
        message.setMessageType(StringUtils.hasText(sendDTO.getMessageType())
                ? sendDTO.getMessageType() : "PRIVATE");
        message.setContentType("TEXT");
        message.setStatus(STATUS_SENT);
        message.setRelatedType(sendDTO.getRelatedType());
        message.setRelatedId(sendDTO.getRelatedId());
        // 附件随消息一起落库：聊天页"选完文件就发一条消息"走的是这条路径，
        // 附件不写进 attachments 的话，共享文件列表就永远看不到这个文件。
        message.setAttachments(buildAttachmentsJson(sendDTO.getAttachmentIds()));
        message.setSentTime(LocalDateTime.now());

        if (instantMessageMapper.insert(message) <= 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "消息发送失败", null);
        }
        log.info("教师发送消息成功 - 消息ID: {}, 会话ID: {}", message.getId(), message.getConversationId());
        return toMessageVO(message, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadVO uploadFile(MultipartFile file, Long sessionId) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();

        // 先校验会话归属再落盘：先把文件传上去再发现会话不是自己的，会留下孤儿文件
        Long receiverId = sessionId == null ? null : requireMyConversation(currentUserId, sessionId);

        Long fileId = fileService.uploadFile(file, currentUserId);
        FileInfo fileInfo = fileService.getById(fileId);

        String fileName = fileInfo != null && StringUtils.hasText(fileInfo.getOriginalFilename())
                ? fileInfo.getOriginalFilename() : file.getOriginalFilename();
        Long fileSize = fileInfo != null && fileInfo.getFileSize() != null
                ? fileInfo.getFileSize() : file.getSize();

        FileUploadVO vo = new FileUploadVO();
        vo.setId(fileId);
        vo.setName(fileName);
        vo.setSize(fileSize);
        vo.setType(getFileExtension(fileName));
        vo.setUrl("/api/teacher/message/files/" + fileId);
        vo.setUploadTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 关键：共享文件列表是从「带附件字段的消息」里反查出来的，
        // 只把文件传到文件服务、不落这条消息的话，getSharedFiles 永远是空的。
        if (sessionId != null) {
            InstantMessage message = new InstantMessage();
            message.setSenderId(currentUserId);
            message.setReceiverId(receiverId);
            message.setConversationId(sessionId);
            message.setMessageType("PRIVATE");
            message.setContentType("FILE");
            message.setContent("[文件] " + fileName);
            message.setAttachments(MessageVO.toAttachmentsJson(Collections.singletonList(
                    MessageVO.attachment(String.valueOf(fileId), fileName, fileSize, getFileExtension(fileName)))));
            message.setStatus(STATUS_SENT);
            message.setSentTime(LocalDateTime.now());
            instantMessageMapper.insert(message);
            log.info("共享文件已入库 - fileId: {}, 会话ID: {}", fileId, sessionId);
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearMessages(Long teacherId, Long sessionId) {
        requireMyConversation(teacherId, sessionId);

        // 用 UpdateWrapper 显式写 SET：is_deleted 带 @TableLogic，
        // 走"实体 + update()"时它在 SET 子句里的去留依赖 MP 的内部判断，
        // 一旦被排除就会生成空的 SET 子句直接报错。
        LambdaUpdateWrapper<InstantMessage> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, sessionId)
                .eq(InstantMessage::getIsDeleted, 0)
                .set(InstantMessage::getIsDeleted, 1);

        int cleared = instantMessageMapper.update(null, wrapper);
        log.info("清空会话成功 - 教师ID: {}, 会话ID: {}, 清空条数: {}", teacherId, sessionId, cleared);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMessagesRead(Long teacherId, Long sessionId) {
        requireMyConversation(teacherId, sessionId);

        // 教师视角下"对方"就是固定的一方，不需要像学生端那样从历史消息里反推，
        // 直接按「这个会话里发给我的」圈定范围即可。
        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, sessionId)
                .eq(InstantMessage::getReceiverId, teacherId)
                .eq(InstantMessage::getStatus, STATUS_SENT)
                .eq(InstantMessage::getIsDeleted, 0);

        InstantMessage update = new InstantMessage();
        update.setStatus(STATUS_READ);
        update.setReadTime(LocalDateTime.now());

        int updated = instantMessageMapper.update(update, wrapper);
        log.info("标记会话已读 - 教师ID: {}, 会话ID: {}, 更新条数: {}", teacherId, sessionId, updated);
    }

    @Override
    public void exportChatRecords(Long teacherId, ChatExportDTO exportDTO, HttpServletResponse response) {
        requireMyConversation(teacherId, exportDTO.getSessionId());

        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, exportDTO.getSessionId())
                .eq(InstantMessage::getIsDeleted, 0)
                .orderByAsc(InstantMessage::getSentTime);

        List<InstantMessage> messages = instantMessageMapper.selectList(wrapper);
        String format = StringUtils.hasText(exportDTO.getFormat()) ? exportDTO.getFormat() : "txt";

        try {
            String fileName = "chat_export_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + "." + format;
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
            response.getWriter().write(buildExportText(messages));
            response.getWriter().flush();
            log.info("导出聊天记录成功 - 会话ID: {}, 消息数: {}", exportDTO.getSessionId(), messages.size());
        } catch (Exception e) {
            log.error("导出聊天记录失败 - 会话ID: {}", exportDTO.getSessionId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "导出聊天记录失败", null);
        }
    }

    // -------------------------------------------------------------- 共享文件

    @Override
    public List<SharedFileVO> getSharedFiles(Long teacherId, Long sessionId) {
        requireMyConversation(teacherId, sessionId);

        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, sessionId)
                .eq(InstantMessage::getIsDeleted, 0)
                .isNotNull(InstantMessage::getAttachments)
                .ne(InstantMessage::getAttachments, "")
                .orderByDesc(InstantMessage::getSentTime);

        List<SharedFileVO> files = new ArrayList<>();
        for (InstantMessage message : instantMessageMapper.selectList(wrapper)) {
            for (MessageVO.MessageAttachmentVO attachment : MessageVO.parseAttachments(message.getAttachments())) {
                files.add(toSharedFileVO(attachment, message));
            }
        }
        // 下载次数：按 attachment_id 批量聚合，避免逐文件查库
        Map<Long, Integer> downloadCounts = attachmentDownloadService.countByAttachmentIds(
                files.stream().map(file -> parseAttachmentId(file.getId())).toList());
        files.forEach(file -> file.setDownloadCount(
                downloadCounts.getOrDefault(parseAttachmentId(file.getId()), 0)));
        log.info("获取共享文件列表 - 会话ID: {}, 文件数: {}", sessionId, files.size());
        return files;
    }

    @Override
    public void downloadSharedFile(Long fileId, Long teacherId, HttpServletResponse response) {
        InstantMessage message = findAttachmentMessage(fileId);
        requireInvolves(message, teacherId);
        writeFileToResponse(fileId, response);
        // 下载成功才留痕（记录失败不影响下载本身）
        attachmentDownloadService.record(fileId, teacherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSharedFile(Long fileId, Long teacherId) {
        InstantMessage message = findAttachmentMessage(fileId);
        requireInvolves(message, teacherId);

        // 一条消息可以带多个附件，所以只摘掉这一个；摘空了才把整条消息软删。
        // （messages 有 @TableLogic，这里改 is_deleted 就是软删，不是物理删除。）
        List<MessageVO.MessageAttachmentVO> remaining = MessageVO.parseAttachments(message.getAttachments()).stream()
                .filter(attachment -> !String.valueOf(fileId).equals(attachment.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            // 同 clearMessages：只改 is_deleted 时必须显式写 SET
            LambdaUpdateWrapper<InstantMessage> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(InstantMessage::getId, message.getId())
                    .set(InstantMessage::getIsDeleted, 1);
            instantMessageMapper.update(null, wrapper);
        } else {
            InstantMessage update = new InstantMessage();
            update.setId(message.getId());
            update.setAttachments(MessageVO.toAttachmentsJson(remaining));
            instantMessageMapper.updateById(update);
        }

        log.info("删除共享文件成功 - fileId: {}, 消息ID: {}", fileId, message.getId());
    }

    // ------------------------------------------------------------ 学生信息

    @Override
    public StudentInfoVO getStudentInfo(Long studentId) {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        requireMyStudent(teacherId, studentId);

        SysUser student = sysUserMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "学生不存在", null);
        }

        StudentInfoVO vo = new StudentInfoVO();
        vo.setName(displayName(student));
        vo.setEmail(student.getEmail());
        vo.setPhone(student.getPhone());
        return vo;
    }

    // ------------------------------------------------------------ 内部工具

    /**
     * 校验会话号确实属于当前教师，并返回对方（学生）的用户 ID。
     *
     * <p>会话号是哈希值，猜到的概率极低，但这不构成"不需要校验"的理由 ——
     * 归属判断的依据与 getMessageSessions 完全相同：存在一条把两人连起来的论文记录。</p>
     */
    private Long requireMyConversation(Long teacherId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "会话 ID 不能为空", null);
        }
        Long counterpartId = resolveCounterpartId(teacherId, sessionId);
        if (counterpartId == null) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "该会话不属于当前教师", null);
        }
        return counterpartId;
    }

    /** 会话号能反推出的对方用户 ID；不属于当前教师时返回 null */
    private Long resolveCounterpartId(Long teacherId, Long sessionId) {
        for (Long studentId : listMyStudentIds(teacherId)) {
            if (ConversationIdUtils.of(teacherId, studentId).equals(sessionId)) {
                return studentId;
            }
        }
        return null;
    }

    /** 当前教师名下指导的学生 ID（按论文关系去重） */
    private List<Long> listMyStudentIds(Long teacherId) {
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getTeacherId, teacherId)
                .eq(PaperInfo::getIsDeleted, 0)
                .isNotNull(PaperInfo::getStudentId);

        return paperInfoMapper.selectList(wrapper).stream()
                .map(PaperInfo::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void requireMyStudent(Long teacherId, Long studentId) {
        if (studentId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "学生 ID 不能为空", null);
        }
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getTeacherId, teacherId)
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0);
        if (paperInfoMapper.selectCount(wrapper) <= 0) {
            // 用 403 而不是 404：这不是"查不到"，是"不给查"
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "只能查看本人指导的学生", null);
        }
    }

    private void requireInvolves(InstantMessage message, Long teacherId) {
        if (!teacherId.equals(message.getSenderId()) && !teacherId.equals(message.getReceiverId())) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权访问该文件", null);
        }
    }

    private InstantMessage getLastMessage(Long conversationId) {
        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, conversationId)
                .eq(InstantMessage::getIsDeleted, 0)
                .orderByDesc(InstantMessage::getSentTime)
                .last("LIMIT 1");
        return instantMessageMapper.selectOne(wrapper);
    }

    private Integer countUnread(Long conversationId, Long teacherId) {
        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getConversationId, conversationId)
                .eq(InstantMessage::getReceiverId, teacherId)
                .eq(InstantMessage::getStatus, STATUS_SENT)
                .eq(InstantMessage::getIsDeleted, 0);
        return Math.toIntExact(instantMessageMapper.selectCount(wrapper));
    }

    private MessageVO toMessageVO(InstantMessage message, Long currentUserId) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setSenderId(message.getSenderId());

        SysUser sender = message.getSenderId() != null ? sysUserMapper.selectById(message.getSenderId()) : null;
        // 前端拿 senderName.charAt(0) 当头像占位，兜底不能省
        vo.setSenderName(displayName(sender));
        vo.setSenderAvatar(sender != null ? sender.getAvatar() : null);

        boolean mine = message.getSenderId() != null && message.getSenderId().equals(currentUserId);
        vo.setSenderRole(mine ? "TEACHER" : "STUDENT");
        vo.setSender(mine ? "teacher" : "student");

        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setMessageType(message.getMessageType());
        vo.setAttachments(MessageVO.parseAttachments(message.getAttachments()));
        vo.setSendTime(message.getSentTime() != null
                ? message.getSentTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
        return vo;
    }

    private MessageSessionVO.SessionMemberVO toMember(SysUser user, Long fallbackId, String role, String name) {
        MessageSessionVO.SessionMemberVO member = new MessageSessionVO.SessionMemberVO();
        member.setUserId(user != null ? user.getId() : fallbackId);
        member.setUserName(name);
        member.setUserRole(role);
        member.setAvatar(user != null ? user.getAvatar() : null);
        return member;
    }

    private SharedFileVO toSharedFileVO(MessageVO.MessageAttachmentVO attachment, InstantMessage message) {
        SharedFileVO vo = new SharedFileVO();
        vo.setId(attachment.getId());
        vo.setName(attachment.getName());
        vo.setType(getFileExtension(attachment.getName()));
        vo.setSize(attachment.getSize() == null ? 0L : attachment.getSize());
        vo.setUploader(displayName(sysUserMapper.selectById(message.getSenderId())));
        vo.setUploadTime(message.getSentTime() != null
                ? message.getSentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "");
        return vo;
    }

    private Long parseAttachmentId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 前端传来的附件 ID 列表 → 入库 JSON（形状见 {@link MessageVO#toAttachmentsJson}） */
    private String buildAttachmentsJson(List<String> attachmentIds) {
        return MessageVO.toAttachmentsJson(MessageVO.fromFileIds(attachmentIds, fileService::getById));
    }

    private boolean containsAttachment(String attachmentsJson, Long fileId) {
        String target = String.valueOf(fileId);
        return MessageVO.parseAttachments(attachmentsJson).stream()
                .anyMatch(attachment -> target.equals(attachment.getId()));
    }

    private InstantMessage findAttachmentMessage(Long fileId) {
        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getIsDeleted, 0)
                .isNotNull(InstantMessage::getAttachments)
                // 先用 LIKE 粗筛再逐个解析确认：JSON 里的键序不保证，不能只靠字符串匹配
                .like(InstantMessage::getAttachments, String.valueOf(fileId));

        return instantMessageMapper.selectList(wrapper).stream()
                .filter(message -> containsAttachment(message.getAttachments(), fileId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被删除", null));
    }

    private void writeFileToResponse(Long fileId, HttpServletResponse response) {
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null || !StringUtils.hasText(fileInfo.getStoragePath())) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "附件信息不存在", null);
        }

        File file = new File(Paths.get(uploadBasePath, fileInfo.getStoragePath()).toString());
        if (!file.exists()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "附件文件不存在", null);
        }

        String fileName = StringUtils.hasText(fileInfo.getOriginalFilename())
                ? fileInfo.getOriginalFilename() : "attachment_" + fileId;
        try {
            response.setContentType(getContentType(fileName));
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
            response.setContentLength((int) file.length());
            try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) > 0) {
                    response.getOutputStream().write(buffer, 0, len);
                }
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            log.error("文件下载失败 - fileId: {}", fileId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "文件下载失败", null);
        }
    }

    private String buildExportText(List<InstantMessage> messages) {
        StringBuilder content = new StringBuilder();
        content.append("聊天记录导出\n");
        content.append("导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        content.append("========================================\n\n");

        for (InstantMessage message : messages) {
            String senderName = displayName(sysUserMapper.selectById(message.getSenderId()));
            String time = message.getSentTime() != null
                    ? message.getSentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
            content.append("[").append(time).append("] ").append(senderName).append(": ")
                    .append(message.getContent() == null ? "" : message.getContent())
                    .append("\n");
        }
        return content.toString();
    }

    private String displayName(SysUser user) {
        return user != null && StringUtils.hasText(user.getRealName()) ? user.getRealName() : "未知用户";
    }

    private String summarize(InstantMessage message) {
        if (!StringUtils.hasText(message.getContent())
                && "FILE".equalsIgnoreCase(message.getContentType())) {
            return "[文件]";
        }
        String content = message.getContent() == null ? "" : message.getContent();
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private String getContentType(String fileName) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerName.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

}
