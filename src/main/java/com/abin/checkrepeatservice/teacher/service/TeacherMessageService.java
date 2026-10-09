package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.student.dto.ChatExportDTO;
import com.abin.checkrepeatservice.student.dto.FileUploadVO;
import com.abin.checkrepeatservice.student.dto.MessageSendDTO;
import com.abin.checkrepeatservice.student.vo.MessageSessionVO;
import com.abin.checkrepeatservice.student.vo.MessageVO;
import com.abin.checkrepeatservice.student.vo.SharedFileVO;
import com.abin.checkrepeatservice.teacher.vo.StudentInfoVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 教师端消息服务：教师视角下的师生聊天与共享文件。
 *
 * <p>与学生端 {@code StudentMessageService} 共用同一张 {@code messages} 表和同一套会话号规则
 * （{@link com.abin.checkrepeatservice.common.utils.ConversationIdUtils}），
 * 因此师生两端看到的是同一批消息。</p>
 *
 * <p>会话号的产生方式不同：学生端按 {@code paper_info.student_id} 反查导师，
 * 教师端按 {@code paper_info.teacher_id} 反查学生。除此之外的语义完全一致。</p>
 *
 * <p><b>可见范围</b>：任何会话号都必须先能被证明属于当前教师（存在一条
 * {@code teacher_id = 我 AND student_id = 对方} 的论文），否则拒绝。会话号是哈希值，
 * 但不能因此就默认"猜不到就等于安全"。</p>
 */
public interface TeacherMessageService {

    /** 当前教师的所有会话（按学生去重） */
    List<MessageSessionVO> getMessageSessions(Long teacherId);

    /** 某个会话的消息列表 */
    Page<MessageVO> getMessageList(Long teacherId, Long sessionId, Integer pageNum, Integer pageSize);

    /** 发送一条聊天消息，发送者取自当前登录用户 */
    MessageVO sendMessage(MessageSendDTO sendDTO);

    /**
     * 上传文件。
     *
     * @param sessionId 所属会话；非空时会额外落一条带附件的消息，否则该文件不会出现在共享文件列表里
     */
    FileUploadVO uploadFile(MultipartFile file, Long sessionId);

    /** 清空某个会话的消息（软删） */
    void clearMessages(Long teacherId, Long sessionId);

    /** 把该会话中发给我的未读消息标记为已读 */
    void markMessagesRead(Long teacherId, Long sessionId);

    /** 导出某个会话的聊天记录 */
    void exportChatRecords(Long teacherId, ChatExportDTO exportDTO, HttpServletResponse response);

    /** 某个会话中的共享文件（来自带附件的消息） */
    List<SharedFileVO> getSharedFiles(Long teacherId, Long sessionId);

    /** 下载共享文件 */
    void downloadSharedFile(Long fileId, Long teacherId, HttpServletResponse response);

    /** 删除共享文件 */
    void deleteSharedFile(Long fileId, Long teacherId);

    /** 学生联系信息；仅限本人指导的学生 */
    StudentInfoVO getStudentInfo(Long studentId);

}
