package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.dto.ChatExportDTO;
import com.abin.checkrepeatservice.student.dto.FileUploadVO;
import com.abin.checkrepeatservice.student.dto.MessageSendDTO;
import com.abin.checkrepeatservice.student.vo.MessageSessionVO;
import com.abin.checkrepeatservice.student.vo.MessageVO;
import com.abin.checkrepeatservice.student.vo.SharedFileVO;
import com.abin.checkrepeatservice.teacher.service.TeacherMessageService;
import com.abin.checkrepeatservice.teacher.vo.StudentInfoVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 教师端消息控制器：师生聊天与共享文件。
 *
 * <p>路径与方法照前端 {@code src/api/teacher.js} 里既有的调用逐字对齐
 * （包括 POST + query param 这种不太 REST 的形状）—— 后端此前从未有过这条路径，
 * 前端是唯一的既有契约，改前端 URL 只会把改动面变大。</p>
 *
 * <p>注意 {@code POST /send} 只负责**聊天消息**。同一个前端文件里的
 * {@code sendMessage} 还被打给 {@code StudentList.vue} 与 {@code ReviewDialog.vue} 用来发
 * 系统消息，那是另一种语义，走的是已有的 {@code POST /api/teacher/students/messages/send}。</p>
 *
 * <p>异常不在这里兜：{@code GlobalExceptionHandler} 会把 BusinessException 映射成
 * 对应的 HTTP 状态码（403/404），这样"不该看的会话"是真的 403，而不是 200 里塞个错误码。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher/message")
@PreAuthorize("hasAuthority('TEACHER')")
@Api(tags = "教师消息接口", description = "教师端师生聊天与共享文件接口")
public class TeacherMessageController {

    @Resource
    private TeacherMessageService teacherMessageService;

    /** 1. 会话列表（按学生去重） */
    @GetMapping("/sessions")
    @ApiOperation("获取消息会话列表")
    public Result<List<MessageSessionVO>> getMessageSessions() {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("获取教师消息会话列表 - 教师ID: {}", teacherId);
        return Result.success("获取会话列表成功", teacherMessageService.getMessageSessions(teacherId));
    }

    /** 2. 某个会话的消息列表 */
    @GetMapping("/list")
    @ApiOperation("获取消息列表")
    public Result<Page<MessageVO>> getMessageList(@RequestParam Long sessionId,
                                                  @RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        return Result.success("获取消息列表成功",
                teacherMessageService.getMessageList(teacherId, sessionId, pageNum, pageSize));
    }

    /** 3. 发送聊天消息 */
    @PostMapping("/send")
    @ApiOperation("发送消息")
    public Result<MessageVO> sendMessage(@Valid @RequestBody MessageSendDTO sendDTO) {
        return Result.success("发送成功", teacherMessageService.sendMessage(sendDTO));
    }

    /** 4. 标记会话已读 */
    @PostMapping("/mark-read")
    @ApiOperation("标记消息已读")
    public Result<String> markMessagesRead(@RequestParam Long sessionId) {
        teacherMessageService.markMessagesRead(UserBusinessInfoUtils.getCurrentUserId(), sessionId);
        return Result.success("标记已读成功");
    }

    /** 5. 上传文件；带 sessionId 时会一并落一条附件消息 */
    @PostMapping("/upload")
    @ApiOperation("上传文件")
    public Result<FileUploadVO> uploadFile(@RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "sessionId", required = false) Long sessionId) {
        return Result.success("上传成功", teacherMessageService.uploadFile(file, sessionId));
    }

    /** 6. 清空会话 */
    @PostMapping("/clear")
    @ApiOperation("清空聊天记录")
    public Result<String> clearMessages(@RequestParam Long sessionId) {
        teacherMessageService.clearMessages(UserBusinessInfoUtils.getCurrentUserId(), sessionId);
        return Result.success("聊天记录已清空");
    }

    /** 7. 学生联系信息（仅限本人指导的学生） */
    @GetMapping("/student-info")
    @ApiOperation("获取学生信息")
    public Result<StudentInfoVO> getStudentInfo(@RequestParam Long studentId) {
        return Result.success("获取学生信息成功", teacherMessageService.getStudentInfo(studentId));
    }

    /** 8. 导出聊天记录；前端以 query param 传 sessionId/format */
    @PostMapping("/export")
    @ApiOperation("导出聊天记录")
    public void exportChatRecords(ChatExportDTO exportDTO, HttpServletResponse response) {
        teacherMessageService.exportChatRecords(
                UserBusinessInfoUtils.getCurrentUserId(), exportDTO, response);
    }

    /** 9. 会话中的共享文件 */
    @GetMapping("/files")
    @ApiOperation("获取共享文件列表")
    public Result<List<SharedFileVO>> getSharedFiles(@RequestParam Long sessionId) {
        return Result.success("获取共享文件列表成功",
                teacherMessageService.getSharedFiles(UserBusinessInfoUtils.getCurrentUserId(), sessionId));
    }

    /** 10. 下载共享文件 */
    @GetMapping("/files/{fileId}")
    @ApiOperation("下载共享文件")
    public void downloadSharedFile(@PathVariable Long fileId, HttpServletResponse response) {
        teacherMessageService.downloadSharedFile(fileId, UserBusinessInfoUtils.getCurrentUserId(), response);
    }

    /** 11. 删除共享文件 */
    @DeleteMapping("/files/{fileId}")
    @ApiOperation("删除共享文件")
    public Result<String> deleteSharedFile(@PathVariable Long fileId) {
        teacherMessageService.deleteSharedFile(fileId, UserBusinessInfoUtils.getCurrentUserId());
        return Result.success("文件删除成功");
    }

}
