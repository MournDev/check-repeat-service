package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.dto.*;
import com.abin.checkrepeatservice.student.service.StudentMessageService;
import com.abin.checkrepeatservice.student.vo.MessageSessionVO;
import com.abin.checkrepeatservice.student.vo.MessageVO;
import com.abin.checkrepeatservice.student.vo.SharedFileVO;
import com.abin.checkrepeatservice.student.vo.AdvisorInfoVO;
import com.abin.checkrepeatservice.user.service.Impl.UserQueryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 *学端消息系统控制器
 * 提供学生与导师的即时通讯功能
 */
@Slf4j
@RestController
@RequestMapping("/api/student/messages")
@Api(tags = "学生消息接口", description = "学生端消息相关接口")
public class StudentMessageController {

    @Resource
    private StudentMessageService studentMessageService;
    
    @Resource
    private UserQueryService userQueryService;

    /**
     * 1. 获取消息会话列表
     * GET /api/student/messages/sessions
     */
    @GetMapping("/sessions")
    @ApiOperation("获取消息会话列表")
    public Result<List<MessageSessionVO>> getMessageSessions() {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("获取消息会话列表 -学ID: {}", studentId);
            
            List<MessageSessionVO> sessions = studentMessageService.getMessageSessions(studentId);
            return Result.success("获取会话列表成功", sessions);
        } catch (Exception e) {
            log.error("获取消息会话列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取会话列表失败: " + e.getMessage());
        }
    }

    /**
     * 2. 获取消息列表
     * GET /api/student/messages/list?sessionId=1&pageNum=1&pageSize=20
     */
    @GetMapping("/list")
    @ApiOperation("获取消息列表")
    public Result<Page<MessageVO>> getMessageList(
            @RequestParam Long sessionId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("获取消息列表 -学ID: {}, 会话ID: {}, 页码: {}, 页大小: {}", 
                    studentId, sessionId, pageNum, pageSize);
            
            Page<MessageVO> messages = studentMessageService.getMessageList(studentId, sessionId, pageNum, pageSize);
            return Result.success("获取消息列表成功", messages);
        } catch (Exception e) {
            log.error("获取消息列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息列表失败: " + e.getMessage());
        }
    }

    /**
     * 3. 发送消息
     * POST /api/student/messages/send
     */
    @PostMapping("/send")
    @ApiOperation("发送消息")
    public Result<MessageVO> sendMessage(@Valid @RequestBody MessageSendDTO sendDTO) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("发送消息 -学ID: {}, 会话ID: {},接收者ID: {}", 
                    studentId, sendDTO.getSessionId(), sendDTO.getReceiverId());
            
            MessageVO message = studentMessageService.sendMessage(studentId, sendDTO);
            return Result.success("消息发送成功", message);
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "消息发送失败: " + e.getMessage());
        }
    }

    /**
     * 4. 上传文件
     * POST /api/student/messages/upload
     */
    @PostMapping("/upload")
    @ApiOperation("上传文件")
    public Result<FileUploadVO> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("上传文件 - 学生ID: {}, 文件名: {}, 文件大小: {}", 
                    studentId, file.getOriginalFilename(), file.getSize());
            
            FileUploadVO fileVO = studentMessageService.uploadFile(file, studentId);
            return Result.success("文件上传成功", fileVO);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 5. 下载附件
     * GET /api/student/messages/attachment/{attachmentId}
     */
    @GetMapping("/attachment/{attachmentId}")
    @ApiOperation("下载附件")
    public void downloadAttachment(@PathVariable Long attachmentId, HttpServletResponse response) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("下载附件 - 学生 ID: {}, 附件 ID: {}", studentId, attachmentId);
                
            studentMessageService.downloadAttachment(attachmentId, studentId, response);
        } catch (Exception e) {
            log.error("下载附件失败 - 附件ID: {}", attachmentId, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 6.清空消息
     * DELETE /api/student/messages/session/{sessionId}/clear
     */
    @DeleteMapping("/session/{sessionId}/clear")
    @ApiOperation("清空消息")
    public Result<String> clearMessages(@PathVariable String sessionId) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            
            // 验证sessionId参数
            if (sessionId == null || "null".equals(sessionId) || sessionId.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "会话ID不能为空");
            }
            
            Long sessionIdLong;
            try {
                sessionIdLong = Long.parseLong(sessionId);
            } catch (NumberFormatException e) {
                return Result.error(ResultCode.PARAM_ERROR, "会话ID格式不正确");
            }
            
            log.info("清空消息 - 学生ID: {}, 会话ID: {}", studentId, sessionIdLong);
            
            studentMessageService.clearMessages(studentId, sessionIdLong);
            return Result.success("清空消息成功");
        } catch (Exception e) {
            log.error("清空消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "清空消息失败: " + e.getMessage());
        }
    }

    /**
     * 7.导出聊天记录
     * POST /api/student/messages/export
     */
    @PostMapping("/export")
    @ApiOperation("导出聊天记录")
    public void exportChatRecords(@Valid @RequestBody ChatExportDTO exportDTO, HttpServletResponse response) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            
            // 额外验证sessionId
            if (exportDTO.getSessionId() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"code\":400,\"message\":\"会话ID不能为空\"}");
                return;
            }
            
            log.info("导出聊天记录 - 学生ID: {}, 会话ID: {},格: {}", 
                    studentId, exportDTO.getSessionId(), exportDTO.getFormat());
            
            studentMessageService.exportChatRecords(studentId, exportDTO, response);
        } catch (Exception e) {
            log.error("导出聊天记录失败", e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败: " + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 8. 获取共享文件列表
     * GET /api/student/messages/shared-files?sessionId=1
     */
    @GetMapping("/shared-files")
    @ApiOperation("获取共享文件列表")
    public Result<List<SharedFileVO>> getSharedFiles(@RequestParam Long sessionId) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("获取共享文件列表 - 学生ID: {}, 会话ID: {}", studentId, sessionId);
            
            List<SharedFileVO> files = studentMessageService.getSharedFiles(studentId, sessionId);
            return Result.success("获取共享文件列表成功", files);
        } catch (Exception e) {
            log.error("获取共享文件列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取共享文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 9. 下载共享文件
     * GET /api/student/messages/shared-file/{fileId}
     */
    @GetMapping("/shared-file/{fileId}")
    @ApiOperation("下载共享文件")
    public void downloadSharedFile(@PathVariable Long fileId, HttpServletResponse response) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("下载共享文件 - 学生 ID: {}, 文件 ID: {}", studentId, fileId);
                
            studentMessageService.downloadSharedFile(fileId, studentId, response);
        } catch (Exception e) {
            log.error("下载共享文件失败 - 文件ID: {}", fileId, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 10.标消息已读
     * PUT /api/student/messages/session/{sessionId}/read
     */
    @PutMapping("/session/{sessionId}/read")
    @ApiOperation("标记消息已读")
    public Result<String> markMessagesRead(@PathVariable String sessionId) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            
            // 验证sessionId参数
            if (sessionId == null || "null".equals(sessionId) || sessionId.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "会话ID不能为空");
            }
            
            Long sessionIdLong;
            try {
                sessionIdLong = Long.parseLong(sessionId);
            } catch (NumberFormatException e) {
                return Result.error(ResultCode.PARAM_ERROR, "会话ID格式不正确");
            }
            
            log.info("标记消息已读 - 学生ID: {}, 会话ID: {}", studentId, sessionIdLong);
            
            studentMessageService.markMessagesRead(studentId, sessionIdLong);
            return Result.success("标记消息已读成功");
        } catch (Exception e) {
            log.error("标记消息已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "标记消息已读失败: " + e.getMessage());
        }
    }

    /**
     * 11.撤消息
     * DELETE /api/student/messages/{messageId}/recall
     */
    @DeleteMapping("/{messageId}/recall")
    @ApiOperation("撤回消息")
    public Result<String> recallMessage(@PathVariable String messageId) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            
            // 验证messageId参数
            if (messageId == null || "null".equals(messageId) || messageId.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "消息ID不能为空");
            }
            
            Long messageIdLong;
            try {
                messageIdLong = Long.parseLong(messageId);
            } catch (NumberFormatException e) {
                return Result.error(ResultCode.PARAM_ERROR, "消息ID格式不正确");
            }
            
            log.info("撤回消息 - 学生ID: {},消息ID: {}", studentId, messageIdLong);
            
            studentMessageService.recallMessage(studentId, messageIdLong);
            return Result.success("消息撤回成功");
        } catch (Exception e) {
            log.error("撤回消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "消息撤回失败: " + e.getMessage());
        }
    }

}