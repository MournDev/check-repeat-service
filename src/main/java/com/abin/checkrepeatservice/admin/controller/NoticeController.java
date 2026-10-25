package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.abin.checkrepeatservice.user.service.SysNoticeService;
import com.abin.checkrepeatservice.user.vo.NoticeStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端「通知管理」控制器
 *
 * 对应前端 src/api/admin/notices.js（页面 views/admin/SystemNoticeManagement.vue）。
 * 放在 admin 包下而不是 notification 包下：notification/** 目前专指 /api/notification
 * + SystemMessage 那一套（字段模型不同：title/noticeTitle、布尔/数字 isRead），
 * 两套混在一个包里下一个人一定会接错线。
 *
 * 数据范围：所有接口都只作用于「当前登录管理员自己的通知」(sys_notice.user_id)，
 * 即这是一个「我的通知」管理页；不存在跨用户查看/删除他人通知的入口。
 *
 * ⚠️ 权限：真正的门禁在 SecurityConfig 的 URL 规则
 * `.requestMatchers("/api/notices/**").hasAuthority("ADMIN")`。类上的 @PreAuthorize
 * 是纵深防御，但【不能只有它】—— GlobalExceptionHandler 只有一个
 * @ExceptionHandler(Exception.class) 兜底、没有 AccessDeniedException 处理器，
 * 方法级安全拒绝会被兜底成 HTTP 500，而过滤器链上的规则才能给出正确的 403。
 *
 * ⚠️ 参数名：pageNum / pageSize 是前端契约，与 admin 包其他接口惯用的 page / size
 * 不同，不要「顺手修正」。
 */
@RestController
@RequestMapping("/api/notices")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "通知管理", description = "当前管理员的通知查询、标记已读与删除")
public class NoticeController {

    /** 批量操作单次上限，防止一个请求带进来几万个 id */
    private static final int MAX_BATCH_SIZE = 500;

    @Resource
    private SysNoticeService sysNoticeService;

    // ==================== 查询 ====================

    /**
     * 分页查询通知列表
     * 出参：data.records / data.total（MyBatis-Plus Page 的序列化结构）
     */
    @Operation(summary = "分页查询通知列表", description = "查询当前登录管理员自己的通知")
    @GetMapping("/list")
    public Result<Page<SysNotice>> getNoticeList(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "通知类型（可选）")
            @RequestParam(required = false) Integer noticeType,
            @Parameter(description = "是否已读：0-未读，1-已读（可选）")
            @RequestParam(required = false) Integer isRead,
            @Parameter(description = "优先级（可选）")
            @RequestParam(required = false) Integer priority) {

        log.info("接收通知列表请求: pageNum={}, pageSize={}, noticeType={}, isRead={}, priority={}",
                pageNum, pageSize, noticeType, isRead, priority);
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        return Result.success(sysNoticeService.getNoticePageForUser(
                userId, noticeType, isRead, priority, pageNum, pageSize));
    }

    /**
     * 通知统计（顶部四张卡片：总数/未读/紧急/已读）
     * 出参是扁平对象，前端用 Object.assign 直接盖到自己的 reactive 上
     */
    @Operation(summary = "通知统计", description = "返回总数/未读/紧急/已读统计")
    @GetMapping("/stats")
    public Result<NoticeStatsVO> getNoticeStats() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        return Result.success(sysNoticeService.getNoticeStats(userId));
    }

    /**
     * 未读通知数量
     */
    @Operation(summary = "未读通知数量")
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        return Result.success(sysNoticeService.getUnreadNoticeCount(userId));
    }

    /**
     * 按优先级统计未读通知数
     * 出参固定含 0/1/2 三个键：{"0":n,"1":n,"2":n}
     */
    @Operation(summary = "按优先级统计未读通知数")
    @GetMapping("/unread-count-by-priority")
    public Result<Map<Integer, Integer>> getUnreadCountByPriority() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        return Result.success(sysNoticeService.getUnreadCountByPriority(userId));
    }

    /**
     * 最新通知（默认 5 条，最多 50 条）
     */
    @Operation(summary = "最新通知", description = "默认 5 条，最多 50 条")
    @GetMapping("/latest")
    public Result<List<SysNotice>> getLatestNotices(@Parameter(description = "返回条数，默认 5") @RequestParam(defaultValue = "5") Integer limit) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        return Result.success(sysNoticeService.getLatestNotices(userId, limit));
    }

    // ==================== 标记已读 ====================

    /**
     * 标记单条通知为已读
     * 通知不存在 / 不属于当前用户 → 404 业务码，前端会弹「操作失败」
     */
    @Operation(summary = "标记单条通知为已读")
    @PutMapping("/read/{id}")
    @OperationLog(type = "notice_read", description = "标记通知已读")
    public Result<String> markAsRead(@Parameter(description = "通知ID", required = true) @PathVariable("id") Long noticeId) {
        log.info("接收标记通知已读请求: noticeId={}", noticeId);
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        if (!sysNoticeService.markNoticeAsRead(noticeId, userId)) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "通知不存在或无权操作");
        }
        return Result.success("已标记为已读", null);
    }

    /**
     * 批量标记已读
     * 前端发的是【裸 JSON 数组】如 [1,2]，不是对象包装
     */
    @Operation(summary = "批量标记已读")
    @PutMapping("/batch-read")
    @OperationLog(type = "notice_batch_read", description = "批量标记通知已读")
    public Result<Integer> batchMarkAsRead(@Parameter(description = "通知ID列表（可选）") @RequestBody(required = false) List<Long> noticeIds) {
        log.info("接收批量标记已读请求: count={}", noticeIds == null ? 0 : noticeIds.size());
        Result<Integer> invalid = validateBatchIds(noticeIds);
        if (invalid != null) {
            return invalid;
        }
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        int rows = sysNoticeService.batchMarkAsRead(noticeIds, userId);
        return Result.success("已标记 " + rows + " 条通知为已读", rows);
    }

    /**
     * 全部标记为已读（仅当前用户自己的未读通知）
     */
    @Operation(summary = "全部标记为已读", description = "仅当前用户自己的未读通知")
    @PutMapping("/read-all")
    @OperationLog(type = "notice_read_all", description = "全部标记通知已读")
    public Result<Integer> markAllAsRead() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        int rows = sysNoticeService.markAllUnreadAsRead(userId);
        return Result.success("已将 " + rows + " 条通知标记为已读", rows);
    }

    // ==================== 删除（逻辑删除） ====================

    /**
     * 删除单条通知
     */
    @Operation(summary = "删除单条通知")
    @DeleteMapping("/delete/{id}")
    @OperationLog(type = "notice_delete", description = "删除通知")
    public Result<String> deleteNotice(@Parameter(description = "通知ID", required = true) @PathVariable("id") Long noticeId) {
        log.info("接收删除通知请求: noticeId={}", noticeId);
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        int rows = sysNoticeService.deleteNotice(noticeId, userId);
        if (rows <= 0) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "通知不存在或无权操作");
        }
        return Result.success("已删除该通知", null);
    }

    /**
     * 批量删除
     * 前端发的是【裸 JSON 数组】
     */
    @Operation(summary = "批量删除")
    @DeleteMapping("/batch-delete")
    @OperationLog(type = "notice_batch_delete", description = "批量删除通知")
    public Result<Integer> batchDeleteNotices(@Parameter(description = "通知ID列表（可选）") @RequestBody(required = false) List<Long> noticeIds) {
        log.info("接收批量删除通知请求: count={}", noticeIds == null ? 0 : noticeIds.size());
        Result<Integer> invalid = validateBatchIds(noticeIds);
        if (invalid != null) {
            return invalid;
        }
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        int rows = sysNoticeService.batchDeleteNotices(noticeIds, userId);
        return Result.success("已删除 " + rows + " 条通知", rows);
    }

    /**
     * 清空全部通知（仅当前用户自己的）
     * 前端按钮上写的是「此操作不可恢复」，实际是逻辑删除（is_deleted = 1），行还在
     */
    @Operation(summary = "清空全部通知", description = "仅当前用户自己的通知（逻辑删除）")
    @DeleteMapping("/clear-all")
    @OperationLog(type = "notice_clear_all", description = "清空通知")
    public Result<Integer> clearAllNotices() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        int rows = sysNoticeService.clearAllNotices(userId);
        return Result.success("已清空 " + rows + " 条通知", rows);
    }

    // ==================== 内部工具 ====================

    /**
     * 校验批量操作的 id 集合。
     * 用 @RequestBody(required = false) + 这里显式判空，是为了让「没带 body」返回
     * 明确的参数错误码；required 保持默认 true 时抛的 HttpMessageNotReadableException
     * 会被 GlobalExceptionHandler 兜底成 500，把「用户没勾选」伪装成服务端故障。
     *
     * @return null 表示校验通过，否则返回应当直接下发的错误结果
     */
    private Result<Integer> validateBatchIds(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "请先选择要操作的通知");
        }
        if (noticeIds.size() > MAX_BATCH_SIZE) {
            return Result.error(ResultCode.PARAM_ERROR, "单次批量操作不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        return null;
    }
}
