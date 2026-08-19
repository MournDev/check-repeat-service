package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.student.dto.ChatExportDTO;
import com.abin.checkrepeatservice.student.dto.MessageSendDTO;
import com.abin.checkrepeatservice.student.vo.AdvisorInfoVO;
import com.abin.checkrepeatservice.student.vo.MessageSessionVO;
import com.abin.checkrepeatservice.student.vo.MessageVO;
import com.abin.checkrepeatservice.student.vo.SharedFileVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 *学端消息服务接口
 */
public interface StudentMessageService {

    /**
     * 获取消息会话列表
     */
    List<MessageSessionVO> getMessageSessions(Long studentId);

    /**
     * 获取消息列表
     */
    Page<MessageVO> getMessageList(Long studentId, Long sessionId, Integer pageNum, Integer pageSize);

    /**
     * 发送消息
     */
    MessageVO sendMessage(Long studentId, MessageSendDTO sendDTO);

    /**
     * 上传文件
     */
    com.abin.checkrepeatservice.student.dto.FileUploadVO uploadFile(MultipartFile file, Long studentId);

    /**
     * 下载附件
     */
    void downloadAttachment(Long attachmentId, Long studentId, HttpServletResponse response);

    /**
     *清空消息
     */
    void clearMessages(Long studentId, Long sessionId);

    /**
     *导出聊天记录
     */
    void exportChatRecords(Long studentId, ChatExportDTO exportDTO, HttpServletResponse response);

    /**
     * 获取共享文件列表
     */
    List<SharedFileVO> getSharedFiles(Long studentId, Long sessionId);

    /**
     * 下载共享文件
     */
    void downloadSharedFile(Long fileId, Long studentId, HttpServletResponse response);

    /**
     *标消息已读
     */
    void markMessagesRead(Long studentId, Long sessionId);

    /**
     *撤消息
     */
    void recallMessage(Long studentId, Long messageId);

    /**
     * 获取导师信息
     */
    AdvisorInfoVO getAdvisorInfo(Long studentId);
}