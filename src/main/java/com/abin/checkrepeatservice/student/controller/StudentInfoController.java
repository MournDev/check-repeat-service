package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.dto.UpdatePasswordReq;
import com.abin.checkrepeatservice.student.dto.UpdateEmailReq;
import com.abin.checkrepeatservice.student.service.InfoService;
import com.abin.checkrepeatservice.student.vo.LoginHistoryVO;
import com.abin.checkrepeatservice.student.vo.LoginLogQueryReq;
import com.abin.checkrepeatservice.user.dto.UpdateUserInfoReq;
import com.abin.checkrepeatservice.user.vo.LoginVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequestMapping("/api/user/")
@Tag(name = "用户信息模块", description = "当前用户信息查询、资料与账号安全维护接口")
public class StudentInfoController {

    @Resource
    private InfoService infoService;

    /**
     * 查询当前登录用户信息接口。
     *
     * <p>注意不要和 {@code UserController}（同样是 /api/user）的 /messages 搞混：
     * 本类路径带尾斜杠，Spring 会拼成 /api/user/info。</p>
     *
     * @return 当前登录用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "查询当前登录用户信息")
    public Result<LoginVO> getCurrentUserInfo() {
        log.info("接收当前用户信息查询请求");
        return infoService.getCurrentUserInfo();
    }

    /**
     * 更新用户信息接口
     * @param updateReq 用户信息更新请求
     * @return 更新后的用户信息
     */
    @PostMapping("/update-info")
    @Operation(summary = "更新用户信息")
    public Result<LoginVO> updateUserInfo(
            @Parameter(description = "用户信息更新请求", required = true) @Valid @RequestBody UpdateUserInfoReq updateReq) {
        log.info("接收用户信息更新请求：用户ID={}", updateReq.getUserId());
        return infoService.updateUserInfo(updateReq);
    }
    /**
     * 上传用户头像接口
     * @param file 头像文件
     * @return 头像访问URL
     */
    @PostMapping("/upload-avatar")
    @Operation(summary = "上传用户头像", description = "返回头像访问URL")
    public Result<String> uploadAvatar(
            @Parameter(description = "头像文件", required = true) @RequestParam("file") MultipartFile file) {
        log.info("接收用户头像上传请求：");
        // 服务层已经把成功和失败都封在 Result 里了，这里直接返回。
        // 曾经这里写的是 Result.success("头像上传成功", avatar.getData())，
        // 等于把服务层的失败整个吞掉：MinIO 报「bucket 不存在」时，
        // 前端收到的仍是 HTTP 200 + code 200 + 「头像上传成功」，只是 data 为 null，
        // 表现成「点了没反应，但也没报错」。
        return infoService.uploadAvatar(file);
    }
    /**
     * 查询用户登录历史接口
     * @param queryReq 查询条件
     * @return 登录历史列表
     */
    @PostMapping("/login-history")
    @Operation(summary = "查询用户登录历史")
    public Result<Page<LoginHistoryVO>> getLoginHistory(
            @Parameter(description = "登录历史查询条件", required = true) @RequestBody LoginLogQueryReq queryReq) {
        log.info("接收查询登录历史请求：{}", queryReq);
        return infoService.getLoginHistory(queryReq);
    }

    /**
     * 修改用户密码接口
     * @param updatePasswordReq 密码修改请求
     * @return 修改结果
     */
    @PutMapping("/update-password")
    @Operation(summary = "修改用户密码")
    public Result<String> updatePassword(
            @Parameter(description = "密码修改请求", required = true) @Valid @RequestBody UpdatePasswordReq updatePasswordReq) {
        log.info("接收用户密码修改请求");
        return infoService.updatePassword(updatePasswordReq);
    }

    /**
     * 发送邮箱验证邮件接口
     * @param email 目标邮箱地址
     * @return 发送结果
     */
    @PostMapping("/send-verify-email")
    @Operation(summary = "发送邮箱验证邮件")
    public Result<String> sendVerifyEmail(
            @Parameter(description = "目标邮箱地址", required = true) @RequestParam String email) {
        log.info("接收发送邮箱验证邮件请求：邮箱={}", email);
        return infoService.sendVerifyEmail(email);
    }

    /**
     * 验证邮箱接口
     * @param token 验证令牌
     */
    @GetMapping("/verify-email")
    @Operation(summary = "验证邮箱")
    public Result<String> verifyEmail(
            @Parameter(description = "验证令牌", required = true) @RequestParam String token) {
        infoService.verifyEmail(token);
        return Result.success("邮箱验证成功");
    }

    /**
     * 发送邮箱验证码接口
     * @param email 目标邮箱地址
     * @return 发送结果
     */
    @PostMapping("/send-email-code")
    @Operation(summary = "发送邮箱验证码")
    public Result<String> sendEmailCode(
            @Parameter(description = "目标邮箱地址", required = true) @RequestParam String email) {
        log.info("接收发送邮箱验证码请求：邮箱={}", email);
        return infoService.sendEmailCode(email);
    }

    /**
     * 更新用户邮箱接口
     * @param updateReq 邮箱更新请求
     * @return 更新结果
     */
    @PostMapping("/update-email")
    @Operation(summary = "更新用户邮箱")
    public Result<String> updateEmail(
            @Parameter(description = "邮箱更新请求", required = true) @Valid @RequestBody UpdateEmailReq updateReq) {
        log.info("接收用户邮箱更新请求");
        return infoService.updateEmail(updateReq);
    }

}
