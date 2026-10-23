package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.AttachmentDownloadService;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.utils.ConversationIdUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.dto.ChatExportDTO;
import com.abin.checkrepeatservice.student.dto.MessageSendDTO;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.StudentMessageService;
import com.abin.checkrepeatservice.student.vo.AdvisorInfoVO;
import com.abin.checkrepeatservice.student.vo.MessageSessionVO;
import com.abin.checkrepeatservice.student.vo.MessageVO;
import com.abin.checkrepeatservice.student.vo.SharedFileVO;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.user.service.Impl.UserQueryService;
import com.abin.checkrepeatservice.user.service.ConversationService;
import com.abin.checkrepeatservice.user.mapper.InstantMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 *学端消息服务实现类
 */
@Slf4j
@Service
public class StudentMessageServiceImpl implements StudentMessageService {

    @Resource
    private InstantMessageMapper instantMessageMapper;
    
    @Resource
    private ConversationService conversationService;
    
    @Resource
    private FileService fileService;
    
    @Resource
    private UserQueryService userQueryService;
    
    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private AttachmentDownloadService attachmentDownloadService;
    
    @Value("${file.upload.base-path}")
    private String uploadBasePath;
    
    /**
     * 生成师生之间的唯一会话 ID。
     *
     * <p>算法本身已搬到 {@link ConversationIdUtils}：教师端要用同一套规则找到同一个会话，
     * 各留一份副本迟早会算岔，导致师生看到两个会话。这里只做委托，行为与原先完全一致。</p>
     */
    private Long generateConversationId(Long userId1, Long userId2) {
        return ConversationIdUtils.of(userId1, userId2);
    }

    /**
     * 校验会话号确实属于当前学生，并返回对方（教师）的用户 ID。
     *
     * <p>会话号是两个用户 ID 的对称哈希，猜中概率低但不等于可跳过校验；
     * 归属依据与 getMessageSessions 一致：存在一条把学生与教师连起来的论文记录。</p>
     */
    private Long requireMyConversation(Long studentId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "会话 ID 不能为空", null);
        }
        Long teacherId = resolveCounterpartId(studentId, sessionId);
        if (teacherId == null) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "该会话不属于当前学生", null);
        }
        return teacherId;
    }

    /** 会话号能反推出的对方（教师）用户 ID；不属于当前学生时返回 null */
    private Long resolveCounterpartId(Long studentId, Long sessionId) {
        if (studentId == null) {
            return null;
        }
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0)
                .isNotNull(PaperInfo::getTeacherId);
        List<Long> teacherIds = paperInfoMapper.selectList(wrapper).stream()
                .map(PaperInfo::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (Long teacherId : teacherIds) {
            if (ConversationIdUtils.of(studentId, teacherId).equals(sessionId)) {
                return teacherId;
            }
        }
        return null;
    }
    
    /**
     * 根据用户 ID 获取会话 ID（用于查询现有会话）
     * 
     * @param userId1 用户 ID 1
     * @param userId2 用户 ID 2
     * @return 会话 ID，如果不存在则返回 null
     */
    private Long getExistingConversationId(Long userId1, Long userId2) {
        // 查询数据库中是否已存在该会话的消息
        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getSenderId, userId1)
               .eq(InstantMessage::getReceiverId, userId2)
               .or()
               .eq(InstantMessage::getSenderId, userId2)
               .eq(InstantMessage::getReceiverId, userId1)
               .last("LIMIT 1"); // 只需要一条记录
        
        InstantMessage message = instantMessageMapper.selectOne(wrapper);
        return message != null ? message.getConversationId() : null;
    }

    @Override
    public List<MessageSessionVO> getMessageSessions(Long studentId) {
        try {
            log.info("获取学生消息会话列表 - 学生 ID: {}", studentId);
                
            // 1. 获取学生的所有导师 ID（通过论文关联）
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .eq(PaperInfo::getIsDeleted, 0)
                       .isNotNull(PaperInfo::getTeacherId);
            List<PaperInfo> papers = paperInfoMapper.selectList(paperWrapper);
                
            // 2. 去重：同一个导师只创建一个会话（不管有多少篇论文）
            Map<Long, String> teacherMap = new LinkedHashMap<>(); // teacherId -> teacherName
            for (PaperInfo paper : papers) {
                if (!teacherMap.containsKey(paper.getTeacherId())) {
                    teacherMap.put(paper.getTeacherId(), paper.getTeacherName());
                }
            }
                
            log.info("学生 {} 共有 {} 位导师，将会话数量：{}", studentId, teacherMap.size(), teacherMap.size());
                
            // 3. 为每位导师创建会话
            List<MessageSessionVO> sessions = new ArrayList<>();
                
            for (Map.Entry<Long, String> entry : teacherMap.entrySet()) {
                Long teacherId = entry.getKey();
                String teacherName = entry.getValue();
                    
                // 【关键】生成独立的会话 ID（不依赖论文 ID）
                Long conversationId = generateConversationId(studentId, teacherId);
                log.info("生成会话 - 学生 ID: {}, 导师 ID: {}, 会话 ID: {}", studentId, teacherId, conversationId);
                    
                MessageSessionVO session = new MessageSessionVO();
                session.setId(conversationId); // ✅ 使用独立会话 ID
                session.setName("与" + teacherName + "的会话");
                session.setType("PRIVATE");
                
                // 获取导师信息
                SysUser teacher = sysUserMapper.selectById(teacherId);
                List<MessageSessionVO.SessionMemberVO> members = new ArrayList<>();
                    
                // 获取当前学生信息
                SysUser student = sysUserMapper.selectById(studentId);
                String studentName = student != null ? student.getRealName() : "未知用户";
                    
                if (teacher != null) {
                    MessageSessionVO.SessionMemberVO teacherMember = new MessageSessionVO.SessionMemberVO();
                    teacherMember.setUserId(teacher.getId());
                    teacherMember.setUserName(teacher.getRealName());
                    teacherMember.setUserRole("TEACHER");
                    teacherMember.setAvatar(teacher.getAvatar());
                    members.add(teacherMember);
                        
                    MessageSessionVO.SessionMemberVO studentMember = new MessageSessionVO.SessionMemberVO();
                    studentMember.setUserId(studentId);
                    studentMember.setUserName(studentName);
                    studentMember.setUserRole("STUDENT");
                    studentMember.setAvatar(student != null ? student.getAvatar() : "");
                    members.add(studentMember);
                        
                    session.setMembers(members);
                }
                    
                // 【关键】获取最后一条消息（使用师生 ID，不再依赖论文）
                InstantMessage lastMessage = getLastMessage(studentId, teacherId);
                if (lastMessage != null) {
                    session.setLastMessage(lastMessage.getContent());
                    session.setLastTime(lastMessage.getSentTime() != null ? 
                        lastMessage.getSentTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "");
                } else {
                    session.setLastMessage("暂无消息");
                    session.setLastTime(null);
                }
                    
                // 【关键】获取未读消息数（使用师生 ID）
                session.setUnreadCount(getUnreadMessageCount(studentId, teacherId));
                    
                // 设置会话头像为导师的头像 URL
                if (!members.isEmpty() && members.get(0).getAvatar() != null) {
                    session.setAvatar(members.get(0).getAvatar());
                } else {
                    session.setAvatar(null);
                }
                    
                sessions.add(session);
            }
                        
            log.info("获取消息会话列表成功 - 学生 ID: {}, 会话数量：{}", studentId, sessions.size());
            return sessions;
            
        } catch (Exception e) {
            log.error("获取消息会话列表失败 -学生ID: {}", studentId, e);
            throw new RuntimeException("获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public Page<MessageVO> getMessageList(Long studentId, Long sessionId, Integer pageNum, Integer pageSize) {
        // 归属校验：会话号必须属于当前学生（对称哈希，猜中概率低但不等于可跳过校验）
        requireMyConversation(studentId, sessionId);
        try {
            log.info("获取消息列表 -学生ID: {}, 会话ID: {}, 页码: {}, 页大小: {}", 
                    studentId, sessionId, pageNum, pageSize);
            
            // 【关键】sessionId 现在是独立的会话 ID（conversation_id）
            // 直接按 conversation_id 查询该会话的所有消息
            LambdaQueryWrapper<InstantMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper
                .eq(InstantMessage::getConversationId, sessionId) // ✅ 使用 conversation_id 查询
                .eq(InstantMessage::getIsDeleted, 0)
                .orderByDesc(InstantMessage::getSentTime);
            
            // 使用分页查询
            Page<InstantMessage> messagePage = new Page<>(pageNum, pageSize);
            Page<InstantMessage> dbPage = instantMessageMapper.selectPage(messagePage, messageWrapper);
            
            //为VO
            List<MessageVO> messageVOs = dbPage.getRecords().stream()
                .map(message -> convertToMessageVO(message, studentId))
                .collect(Collectors.toList());
            
            Page<MessageVO> page = new Page<>(pageNum, pageSize);
            page.setRecords(messageVOs);
            page.setTotal(dbPage.getTotal());
            
            log.info("获取消息列表成功");
            return page;
            
        } catch (Exception e) {
            log.error("获取消息列表失败", e);
            throw new RuntimeException("获取消息列表失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(Long studentId, MessageSendDTO sendDTO) {
        try {
            // 使用当前登录用户 ID，而不是参数中的 studentId
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();

            // 归属校验：会话必须属于当前学生，且接收者必须是该会话对应的教师
            Long teacherId = requireMyConversation(currentUserId, sendDTO.getSessionId());
            if (sendDTO.getReceiverId() != null && !sendDTO.getReceiverId().equals(teacherId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "接收者与会话不匹配", null);
            }
                    
            log.info("发送消息 - 当前用户 ID: {}, 会话 ID: {},接收者 ID: {}", 
                    currentUserId, sendDTO.getSessionId(), teacherId);
                    
            // 【关键】确保 conversation_id 正确设置（使用前端传递的会话 ID）
            // 前端传递的 sessionId 现在应该是真正的会话 ID（conversation_id）
            InstantMessage message = new InstantMessage();
            message.setSenderId(currentUserId); // 使用当前登录用户 ID
            message.setReceiverId(teacherId);
            message.setConversationId(sendDTO.getSessionId()); // ✅ 直接使用前端传递的会话 ID
            message.setContent(sendDTO.getContent());
            message.setMessageType(sendDTO.getMessageType());
            message.setContentType("TEXT");
            message.setStatus("SENT");
            message.setRelatedType(sendDTO.getRelatedType());
            message.setRelatedId(sendDTO.getRelatedId());
            // 附件随消息一起落库。此前只有一个 // TODO 占位，attachmentIds 收下就丢了，
            // 于是「带附件发消息」实际只发出去一句纯文本，学生端共享文件列表也永远是空的。
            // 形状统一走 MessageVO，读端（getMessageList / getSharedFiles）按同一份定义解析。
            List<MessageVO.MessageAttachmentVO> attachments =
                    MessageVO.fromFileIds(sendDTO.getAttachmentIds(), fileService::getById);
            message.setAttachments(MessageVO.toAttachmentsJson(attachments));
            message.setSentTime(LocalDateTime.now());
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());
            
            //直插入数据库
            int result = instantMessageMapper.insert(message);
            
            if (result > 0) {
                //为 VO
                MessageVO messageVO = new MessageVO();
                messageVO.setId(message.getId());
                messageVO.setSenderId(currentUserId); // 使用当前登录用户 ID
                            
                // 【修复】获取真实的学生姓名，而不是硬编码"我"
                SysUser currentUser = sysUserMapper.selectById(currentUserId);
                String realSenderName = currentUser != null ? currentUser.getRealName() : "未知用户";
                messageVO.setSenderName(realSenderName); // ✅ 使用真实姓名
                            
                messageVO.setSenderRole("STUDENT");
                messageVO.setContent(sendDTO.getContent());
                String sendTimeStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                messageVO.setSendTime(sendTimeStr);
                messageVO.setFormattedTime(messageVO.getFormattedTime()); // 设置格式化时间
                messageVO.setStatus("SENT");
                messageVO.setMessageType(sendDTO.getMessageType());
                            
                // 【新增】设置 sender 字段，兼容前端判断
                messageVO.setSender("student"); // ✅ 添加 sender 字段
                            
                messageVO.setAttachments(attachments);
                            
                log.info("消息发送成功 - 消息ID: {}, 发送者：{}", message.getId(), realSenderName);
                return messageVO;
            } else {
                throw new RuntimeException("消息发送失败: 数据库插入返回值为 " + result);
            }
            
        } catch (BusinessException e) {
            // 归属/参数校验异常原样抛出，交给全局异常处理映射状态码
            throw e;
        } catch (Exception e) {
            log.error("发送消息失败", e);
            throw new RuntimeException("消息发送失败: " + e.getMessage());
        }
    }

    @Override
    public com.abin.checkrepeatservice.student.dto.FileUploadVO uploadFile(MultipartFile file, Long studentId) {
        try {
            log.info("上传文件 -学生ID: {}, 文件名: {}, 文件大小: {}", 
                    studentId, file.getOriginalFilename(), file.getSize());
            
            // 使用文件服务上传文件
            Long fileId = fileService.uploadFile(file, studentId);
                        
            //构建响应 VO
            com.abin.checkrepeatservice.student.dto.FileUploadVO fileVO = 
                new com.abin.checkrepeatservice.student.dto.FileUploadVO();
            fileVO.setId(fileId);
            fileVO.setName(file.getOriginalFilename());
            fileVO.setSize(file.getSize());
            fileVO.setType(getFileExtension(file.getOriginalFilename()));
            fileVO.setUrl("/api/student/messages/attachment/" + fileId);
            fileVO.setUploadTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            log.info("文件上传成功 - 文件ID: {}", fileId);
            return fileVO;
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadAttachment(Long attachmentId, Long studentId, HttpServletResponse response) {
        try {
            log.info("下载附件 - 学生 ID: {}, 附件 ID: {}", studentId, attachmentId);

            // 归属校验：附件必须出现在该学生参与的会话消息中，防止按 ID 越权下载
            InstantMessage message = findAttachmentMessage(attachmentId);
            requireInvolves(message, studentId);
                
            // 获取文件信息
            FileInfo fileInfo = fileService.getById(attachmentId);
            if (fileInfo != null && StringUtils.hasText(fileInfo.getStoragePath())) {
                String fullPath = Paths.get(uploadBasePath, fileInfo.getStoragePath()).toString();
                File file = new File(fullPath);
                
                if (file.exists()) {
                    // 设置响应头
                    String fileName = fileInfo.getOriginalFilename() != null ? 
                        fileInfo.getOriginalFilename() : "attachment_" + attachmentId;
                    response.setContentType(getContentType(fileName));
                    response.setHeader("Content-Disposition", 
                        "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
                    response.setContentLength((int) file.length());
                    
                    //写入文件内容
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            response.getOutputStream().write(buffer, 0, len);
                        }
                        response.getOutputStream().flush();
                    }
                    
                    log.info("附件下载成功 - 文件名: {}", fileName);
                    // 下载成功才留痕（记录失败不影响下载本身）
                    attachmentDownloadService.record(attachmentId, studentId);
                } else {
                    throw new RuntimeException("附件文件不存在");
                }
            } else {
                throw new RuntimeException("附件信息不存在");
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载附件失败 - 附件ID: {}", attachmentId, e);
            throw new RuntimeException("附件下载失败: " + e.getMessage());
        }
    }

    /**
     * 查找包含指定附件的消息（LIKE 粗筛 + 精确解析，避免 JSON 键序/ID 子串误命中）
     */
    private InstantMessage findAttachmentMessage(Long fileId) {
        LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InstantMessage::getIsDeleted, 0)
                .isNotNull(InstantMessage::getAttachments)
                .like(InstantMessage::getAttachments, String.valueOf(fileId));

        return instantMessageMapper.selectList(wrapper).stream()
                .filter(message -> MessageVO.parseAttachments(message.getAttachments()).stream()
                        .anyMatch(attachment -> String.valueOf(fileId).equals(attachment.getId())))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "附件不存在"));
    }

    /**
     * 附件所在消息必须由当前学生发送或接收
     */
    private void requireInvolves(InstantMessage message, Long studentId) {
        if (!Objects.equals(studentId, message.getSenderId())
                && !Objects.equals(studentId, message.getReceiverId())) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权下载该附件");
        }
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

    @Override
    public void clearMessages(Long studentId, Long sessionId) {
        requireMyConversation(studentId, sessionId);
        try {
            log.info("清空消息 -学生ID: {}, 会话ID: {}", studentId, sessionId);
            
            // 实现消息清空逻辑
            // 将会话中所有消息标记为已删除。
            // 注意：sessionId 是会话号（conversation_id），不是"对方的用户 ID"。
            // 原先这里写的是 senderId/receiverId 与 sessionId 的两组配对 ——
            // 拿会话号去比用户 ID 永远匹配不到，等于这个清空功能一直没生效过；
            // 而且那个 .and(...).or(...).eq(isDeleted, 0) 的写法里，
            // isDeleted 只约束了 OR 的后半支（AND 优先于 OR）。这里一并改掉。
            LambdaQueryWrapper<InstantMessage> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper
                .eq(InstantMessage::getConversationId, sessionId)
                .eq(InstantMessage::getIsDeleted, 0);

            // 更新删除状态
            InstantMessage updateMessage = new InstantMessage();
            updateMessage.setIsDeleted(1);
            updateMessage.setUpdateTime(LocalDateTime.now());
            
            int deletedCount = instantMessageMapper.update(updateMessage, deleteWrapper);
            log.info("消息清空成功 - 删除消息数: {}", deletedCount);
            
        } catch (Exception e) {
            log.error("清空消息失败", e);
            throw new RuntimeException("清空消息失败: " + e.getMessage());
        }
    }

    @Override
    public void exportChatRecords(Long studentId, ChatExportDTO exportDTO, HttpServletResponse response) {
        requireMyConversation(studentId, exportDTO == null ? null : exportDTO.getSessionId());
        try {
            log.info("导出聊天记录 - 学生ID: {}, 会话ID: {},格: {}", 
                    studentId, exportDTO.getSessionId(), exportDTO.getFormat());
            
            // 实现聊天记录导出逻辑
            // 查询会话中的所有消息。
            // exportDTO.getSessionId() 是会话号（conversation_id），不是对方用户 ID ——
            // 原先拿它去比 senderId/receiverId，恒匹配 0 行，导出的永远是空文件。
            LambdaQueryWrapper<InstantMessage> messageWrapper = new LambdaQueryWrapper<>();
            messageWrapper
                .eq(InstantMessage::getConversationId, exportDTO.getSessionId())
                .eq(InstantMessage::getIsDeleted, 0)
                .orderByAsc(InstantMessage::getSentTime);
                        
            List<InstantMessage> messages = instantMessageMapper.selectList(messageWrapper);
                        
            //根据格式生成导出内容
            String exportContent = generateChatExportContent(messages, exportDTO.getFormat());
                        
            // 设置响应头
            String fileName = "chat_export_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + 
                             "." + exportDTO.getFormat();
            response.setContentType("application/octet-stream");
            String headerValue = "attachment; filename=" + fileName;
            response.setHeader("Content-Disposition", headerValue);
                        
            //写导出内容
            response.getWriter().write(exportContent);
            response.getWriter().flush();
                        
            log.info("聊天记录导出成功 - 消息数量: {},格: {}", messages.size(), exportDTO.getFormat());
            
        } catch (Exception e) {
            log.error("导出聊天记录失败", e);
            throw new RuntimeException("导出聊天记录失败: " + e.getMessage());
        }
    }

    @Override
    public List<SharedFileVO> getSharedFiles(Long studentId, Long sessionId) {
        requireMyConversation(studentId, sessionId);
        try {
            log.info("获取共享文件列表 -学生ID: {}, 会话ID: {}", studentId, sessionId);
            
            // 实现共享文件列表获取逻辑
            // 查询会话中的共享文件（通过附件消息）。
            // sessionId 是会话号（conversation_id），不是对方用户 ID ——
            // 原先的错误配对让这个列表恒为空，学生端「文件管理」页一直拿不到任何文件。
            LambdaQueryWrapper<InstantMessage> fileWrapper = new LambdaQueryWrapper<>();
            fileWrapper
                .eq(InstantMessage::getConversationId, sessionId)
                .eq(InstantMessage::getIsDeleted, 0)
                .isNotNull(InstantMessage::getAttachments)
                .ne(InstantMessage::getAttachments, "")
                .orderByDesc(InstantMessage::getSentTime);
                        
            List<InstantMessage> messagesWithAttachments = instantMessageMapper.selectList(fileWrapper);
                        
            List<SharedFileVO> files = new ArrayList<>();
            for (InstantMessage message : messagesWithAttachments) {
                // 按 MessageVO 里的统一定义解析，与写入端、教师端共用同一份形状。
                // 原先是就地 new ObjectMapper 反序列化成 Map，再靠字符串键取值，
                // size 缺失时 .toString() 直接 NPE，被下面那层 catch 吞成"这条消息没有附件"。
                for (MessageVO.MessageAttachmentVO attachment : MessageVO.parseAttachments(message.getAttachments())) {
                    SharedFileVO fileVO = new SharedFileVO();
                    fileVO.setId(attachment.getId());
                    fileVO.setName(attachment.getName());
                    fileVO.setType(getFileExtension(attachment.getName()));
                    fileVO.setSize(attachment.getSize() == null ? 0L : attachment.getSize());

                    //上传者信息
                    String senderName = "未知用户";
                    SysUser sender = sysUserMapper.selectById(message.getSenderId());
                    if (sender != null) {
                        senderName = sender.getRealName();
                    }
                    fileVO.setUploader(senderName);
                    fileVO.setUploadTime(message.getSentTime() != null ?
                        message.getSentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "");

                    files.add(fileVO);
                }
            }

            // 下载次数：按 attachment_id 批量聚合，避免逐文件查库
            Map<Long, Integer> downloadCounts = attachmentDownloadService.countByAttachmentIds(
                    files.stream().map(file -> parseAttachmentId(file.getId())).toList());
            for (SharedFileVO file : files) {
                file.setDownloadCount(downloadCounts.getOrDefault(parseAttachmentId(file.getId()), 0));
            }
            
            log.info("获取共享文件列表成功 - 文件数量: {}", files.size());
            return files;
            
        } catch (Exception e) {
            log.error("获取共享文件列表失败", e);
            throw new RuntimeException("获取共享文件列表失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadSharedFile(Long fileId, Long studentId, HttpServletResponse response) {
        try {
            log.info("下载共享文件 - 学生 ID: {}, 文件 ID: {}", studentId, fileId);
                
            //复附件下载逻辑
            downloadAttachment(fileId, studentId, response);
            
        } catch (Exception e) {
            log.error("下载共享文件失败 - 文件ID: {}", fileId, e);
            throw new RuntimeException("共享文件下载失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMessagesRead(Long studentId, Long sessionId) {
        requireMyConversation(studentId, sessionId);
        try {
            log.info("标记消息已读 - 学生 ID: {}, 会话 ID: {}", studentId, sessionId);
                    
            // 【重构】sessionId 现在是独立的会话 ID（conversation_id）
            // 不再依赖论文存在性，直接从消息表中查询该会话的消息
            
            // 1. 验证会话是否存在（通过查询该会话的消息）
            LambdaQueryWrapper<InstantMessage> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(InstantMessage::getConversationId, sessionId)
                       .last("LIMIT 1");
            
            InstantMessage existMessage = instantMessageMapper.selectOne(checkWrapper);
            if (existMessage == null) {
                log.warn("会话不存在或没有消息 - 会话 ID: {}", sessionId);
                    
                // 【关键】即使会话不存在，也要尝试标记所有可能的未读消息为已读
                // 这是为了处理"幽灵会话"的情况（前端显示但未实际存在）
                LambdaQueryWrapper<InstantMessage> cleanupWrapper = new LambdaQueryWrapper<>();
                cleanupWrapper
                    .eq(InstantMessage::getReceiverId, studentId)
                    .eq(InstantMessage::getStatus, "SENT") // 只处理未读消息
                    .eq(InstantMessage::getIsDeleted, 0);
                        
                InstantMessage updateMessage = new InstantMessage();
                updateMessage.setStatus("READ");
                updateMessage.setReadTime(LocalDateTime.now());
                updateMessage.setUpdateTime(LocalDateTime.now());
                        
                int cleanedCount = instantMessageMapper.update(updateMessage, cleanupWrapper);
                if (cleanedCount > 0) {
                    log.info("清理幽灵会话的未读消息 - 会话 ID: {}, 清理数量：{}", sessionId, cleanedCount);
                }
                    
                return; // 幂等性处理
            }
            
            // 2. 从历史消息中获取导师 ID（发送者或接收者）
            Long teacherId = null;
            if (existMessage.getSenderId().equals(studentId)) {
                // 学生发送给导师的消息 → 接收者是导师
                teacherId = existMessage.getReceiverId();
            } else {
                // 导师发送给学生的消息 → 发送者是导师
                teacherId = existMessage.getSenderId();
            }
            
            log.info("从历史消息中获取导师 ID: {}", teacherId);
                    
            // 3. 将发送给学生的未读消息标记为已读
            LambdaQueryWrapper<InstantMessage> readWrapper = new LambdaQueryWrapper<>();
            readWrapper
                .eq(InstantMessage::getReceiverId, studentId)
                .eq(InstantMessage::getSenderId, teacherId)  // 导师发送的消息
                .eq(InstantMessage::getStatus, "SENT") // 未读状态
                .eq(InstantMessage::getIsDeleted, 0);
                
            log.info("查询条件 - receiverId: {}, senderId: {}, status: SENT", studentId, teacherId);
                    
            // 更新状态为已读
            InstantMessage updateMessage = new InstantMessage();
            updateMessage.setStatus("READ");
            updateMessage.setReadTime(LocalDateTime.now());
            updateMessage.setUpdateTime(LocalDateTime.now());
                    
            int updatedCount = instantMessageMapper.update(updateMessage, readWrapper);
            log.info("消息标记已读成功 - 更新消息数：{}", updatedCount);
                    
        } catch (Exception e) {
            log.error("标记消息已读失败", e);
            throw new RuntimeException("标记消息已读失败：" + e.getMessage());
        }
    }

    @Override
    public void recallMessage(Long studentId, Long messageId) {
        try {
            log.info("撤回消息 -学生ID: {},消息ID: {}", studentId, messageId);
            
            // 实现消息撤回逻辑
            //检查消息是否可以撤回（必须是发送者且在2分钟内）
            InstantMessage message = instantMessageMapper.selectById(messageId);
            
            if (message == null) {
                throw new RuntimeException("消息不存在");
            }
            
            if (!message.getSenderId().equals(studentId)) {
                throw new RuntimeException("只能撤回自己发送的消息");
            }
            
            //检查时间限制（2分钟内）
            LocalDateTime now = LocalDateTime.now();
            if (message.getSentTime() != null && 
                message.getSentTime().plusMinutes(2).isBefore(now)) {
                throw new RuntimeException("消息发送超过2分钟，无法撤回");
            }
            
            // 更新消息状态为已撤回
            message.setStatus("RECALLED");
            message.setUpdateTime(LocalDateTime.now());
            message.setContent("[此消息已被撤回]");
            
            int result = instantMessageMapper.updateById(message);
            if (result <= 0) {
                throw new RuntimeException("消息撤回失败");
            }
            
            log.info("消息撤回成功 - 消息ID: {}", messageId);
            
        } catch (Exception e) {
            log.error("撤回消息失败", e);
            throw new RuntimeException("消息撤回失败: " + e.getMessage());
        }
    }

    @Override
    public AdvisorInfoVO getAdvisorInfo(Long studentId) {
        try {
            log.info("获取导师信息 - 学生 ID: {}", studentId);
            
            // 获取学生的论文信息
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .eq(PaperInfo::getIsDeleted, 0)
                       .isNotNull(PaperInfo::getTeacherId);
            List<PaperInfo> papers = paperInfoMapper.selectList(paperWrapper);
            
            // 没有导师是正常状态（刚注册、还没被分配），不是故障：返回 null 让调用方走空态。
            // 原来抛异常，会被下面的 catch 再包一层，前端只收到一句红字，连"没导师"都读不出来。
            if (papers.isEmpty()) {
                log.info("学生暂未分配指导老师 - 学生 ID: {}", studentId);
                return null;
            }

            // 获取第一个论文的导师信息
            PaperInfo paper = papers.get(0);
            SysUser teacher = sysUserMapper.selectById(paper.getTeacherId());

            // paper_info 挂着 teacher_id、sys_user 里却没这个人：数据不一致。
            // 对学生来说同样是"没导师可看"，记 warn 便于排查，仍然按空处理。
            if (teacher == null) {
                log.warn("论文 {} 的 teacher_id={} 在 sys_user 中查不到，按未分配导师处理",
                        paper.getId(), paper.getTeacherId());
                return null;
            }
            
            //构建导师信息 VO
            AdvisorInfoVO advisorInfo = new AdvisorInfoVO();
            advisorInfo.setId(teacher.getId().toString());
            advisorInfo.setName(teacher.getRealName());
            advisorInfo.setTitle(teacher.getProfessionalTitle() != null ? teacher.getProfessionalTitle() : "讲师");
            advisorInfo.setResearchField(teacher.getResearchDirection());
            advisorInfo.setEmail(teacher.getEmail());
            advisorInfo.setPhone(teacher.getPhone());
            advisorInfo.setOffice(teacher.getOffice());
            advisorInfo.setOfficeHours(teacher.getOfficeHours());
            // 设置头像，如果数据库中没有则设为 null
            advisorInfo.setAvatar(teacher.getAvatar() != null && !teacher.getAvatar().trim().isEmpty() ? teacher.getAvatar() : null);
            advisorInfo.setBio(teacher.getIntroduce());
            advisorInfo.setCollege(teacher.getCollegeName());
            
            //统计指导学生数
            LambdaQueryWrapper<PaperInfo> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(PaperInfo::getTeacherId, teacher.getId())
                       .eq(PaperInfo::getIsDeleted, 0);
            int studentCount = Math.toIntExact(paperInfoMapper.selectCount(countWrapper));
            advisorInfo.setStudentCount(studentCount);
            
            log.info("获取导师信息成功 -导师 ID: {}", teacher.getId());
            return advisorInfo;
            
        } catch (Exception e) {
            log.error("获取导师信息失败", e);
            throw new RuntimeException("获取导师信息失败：" + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 生成聊天记录导出内容
     */
    private String generateChatExportContent(List<InstantMessage> messages, String format) {
        StringBuilder content = new StringBuilder();
        
        if ("txt".equalsIgnoreCase(format)) {
            // TXT格式
            content.append("聊天记录导出\n");
            content.append("导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
            content.append("========================================\n\n");
            
            for (InstantMessage message : messages) {
                String senderName = message.getSenderId() != null ? 
                    "用户" + message.getSenderId() : "未知用户";
                String time = message.getSentTime() != null ? 
                    message.getSentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
                
                content.append("[").append(time).append("] ").append(senderName).append(":\n");
                content.append(message.getContent()).append("\n\n");
            }
            
        } else {
            // PDF/DOC格式（简化为HTML格式，便于转换）
            content.append("<html><head><meta charset=UTF-8><title>聊天记录</title></head><body>");
            content.append("<h1>聊天记录导出</h1>");
            content.append("<p>导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("</p>");
            content.append("<hr>");
            
            for (InstantMessage message : messages) {
                String senderName = message.getSenderId() != null ? 
                    "用户" + message.getSenderId() : "未知用户";
                String time = message.getSentTime() != null ? 
                    message.getSentTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
                
                content.append("<div style=margin: 10px 0; padding: 10px; border: 1px solid #ccc;>");
                content.append("<strong>").append(time).append(" ").append(senderName).append(":</strong><br>");
                content.append(message.getContent());
                content.append("</div>");
            }
            
            content.append("</body></html>");
        }
        
        return content.toString();
    }
    
    /**
     *将 InstantMessage 转换为 MessageVO
     */
    private MessageVO convertToMessageVO(InstantMessage message, Long studentId) {
        MessageVO messageVO = new MessageVO();
        messageVO.setId(message.getId());
        messageVO.setSenderId(message.getSenderId());
        messageVO.setSenderName(message.getSenderName() != null ? message.getSenderName() : "未知用户");
        // 发送者角色按会话参与者判定：本人=STUDENT，对方教师=TEACHER，系统消息无发送者
        if (message.getSenderId() == null) {
            messageVO.setSenderRole("SYSTEM");
        } else {
            messageVO.setSenderRole(message.getSenderId().equals(studentId) ? "STUDENT" : "TEACHER");
        }
        messageVO.setContent(message.getContent());
            
        if (message.getSentTime() != null) {
            String sendTimeStr = message.getSentTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            messageVO.setSendTime(sendTimeStr);
            messageVO.setFormattedTime(messageVO.getFormattedTime()); // 设置格式化时间
        } else {
            messageVO.setSendTime("");
            messageVO.setFormattedTime("");
        }
            
        messageVO.setStatus(message.getStatus());
        messageVO.setMessageType(message.getMessageType());
        messageVO.setSenderAvatar(message.getSenderAvatar());
        // 不解析 attachments 的话，追加到消息上的附件在列表接口里会整个消失，
        // 页面上的 .message-attachments 区块永远不渲染。
        messageVO.setAttachments(MessageVO.parseAttachments(message.getAttachments()));
        return messageVO;
    }
    
    /**
     * 获取最后一条消息（基于师生关系）
     * 
     * @param studentId 学生 ID
     * @param teacherId 导师 ID
     * @return 最后一条消息
     */
    private InstantMessage getLastMessage(Long studentId, Long teacherId) {
        try {
            LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper
                .and(w -> w
                    .eq(InstantMessage::getSenderId, studentId)
                    .eq(InstantMessage::getReceiverId, teacherId))
                .or(w -> w
                    .eq(InstantMessage::getSenderId, teacherId)
                    .eq(InstantMessage::getReceiverId, studentId))
                .eq(InstantMessage::getIsDeleted, 0)
                .orderByDesc(InstantMessage::getSentTime)
                .last("LIMIT 1");
            
            return instantMessageMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("获取最后消息失败", e);
            return null;
        }
    }
    
    /**
     * 获取未读消息数（基于师生关系）
     * 
     * @param studentId 学生 ID
     * @param teacherId 导师 ID
     * @return 未读消息数量
     */
    private Integer getUnreadMessageCount(Long studentId, Long teacherId) {
        try {
            LambdaQueryWrapper<InstantMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper
                .eq(InstantMessage::getReceiverId, studentId)
                .eq(InstantMessage::getSenderId, teacherId)
                .eq(InstantMessage::getStatus, "SENT") // 未读状态
                .eq(InstantMessage::getIsDeleted, 0);
            
            return Math.toIntExact(instantMessageMapper.selectCount(wrapper));
        } catch (Exception e) {
            log.warn("获取未读消息数失败", e);
            return 0;
        }
    }
        
    /**
     *根据文件名获取内容类型
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
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            return "image/png";
        } else {
            return "application/octet-stream";
        }
    }
}