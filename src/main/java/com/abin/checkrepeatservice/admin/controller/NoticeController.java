package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.abin.checkrepeatservice.user.service.SysNoticeService;
import com.abin.checkrepeatservice.user.vo.NoticeStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
    @GetMapping("/list")
    public Result<Page<SysNotice>> getNoticeList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) Integer priority) {

        log.info("接收通知列表请求: pageNum={}, pageSize={}, noticeType={}, isRead={}, priority={}",
                pageNum, pageSize, noticeType, isRead, priority);
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            return Result.success(sysNoticeService.getNoticePageForUser(
                    userId, noticeType, isRead, priority, pageNum, pageSize));
        } catch (Exception e) {
            log.error("查询通知列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询通知列表失败: " + e.getMessage());
        }
    }

    /**
     * 通知统计（顶部四张卡片：总数/未读/紧急/已读）
     * 出参是扁平对象，前端用 Object.assign 直接盖到自己的 reactive 上
     */
    @GetMapping("/stats")
    public Result<NoticeStatsVO> getNoticeStats() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            return Result.success(sysNoticeService.getNoticeStats(userId));
        } catch (Exception e) {
            log.error("查询通知统计失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询通知统计失败: " + e.getMessage());
        }
    }

    /**
     * 未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            return Result.success(sysNoticeService.getUnreadNoticeCount(userId));
        } catch (Exception e) {
            log.error("查询未读通知数失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询未读通知数失败: " + e.getMessage());
        }
    }

    /**
     * 按优先级统计未读通知数
     * 出参固定含 0/1/2 三个键：{"0":n,"1":n,"2":n}
     */
    @GetMapping("/unread-count-by-priority")
    public Result<Map<Integer, Integer>> getUnreadCountByPriority() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            return Result.success(sysNoticeService.getUnreadCountByPriority(userId));
        } catch (Exception e) {
            log.error("按优先级查询未读通知数失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "按优先级查询未读通知数失败: " + e.getMessage());
        }
    }

    /**
     * 最新通知（默认 5 条，最多 50 条）
     */
    @GetMapping("/latest")
    public Result<List<SysNotice>> getLatestNotices(@RequestParam(defaultValue = "5") Integer limit) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            return Result.success(sysNoticeService.getLatestNotices(userId, limit));
        } catch (Exception e) {
            log.error("查询最新通知失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询最新通知失败: " + e.getMessage());
        }
    }

    // ==================== 标记已读 ====================

    /**
     * 标记单条通知为已读
     * 通知不存在 / 不属于当前用户 → 404 业务码，前端会弹「操作失败」
     */
    @PutMapping("/read/{id}")
    @OperationLog(type = "notice_read", description = "标记通知已读")
    public Result<String> markAsRead(@PathVariable("id") Long noticeId) {
        log.info("接收标记通知已读请求: noticeId={}", noticeId);
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            if (!sysNoticeService.markNoticeAsRead(noticeId, userId)) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "通知不存在或无权操作");
            }
            return Result.success("已标记为已读", null);
        } catch (Exception e) {
            log.error("标记通知已读失败: noticeId={}, {}", noticeId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "标记通知已读失败: " + e.getMessage());
        }
    }

    /**
     * 批量标记已读
     * 前端发的是【裸 JSON 数组】如 [1,2]，不是对象包装
     */
    @PutMapping("/batch-read")
    @OperationLog(type = "notice_batch_read", description = "批量标记通知已读")
    public Result<Integer> batchMarkAsRead(@RequestBody(required = false) List<Long> noticeIds) {
        log.info("接收批量标记已读请求: count={}", noticeIds == null ? 0 : noticeIds.size());
        Result<Integer> invalid = validateBatchIds(noticeIds);
        if (invalid != null) {
            return invalid;
        }
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            int rows = sysNoticeService.batchMarkAsRead(noticeIds, userId);
            return Result.success("已标记 " + rows + " 条通知为已读", rows);
        } catch (Exception e) {
            log.error("批量标记通知已读失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量标记通知已读失败: " + e.getMessage());
        }
    }

    /**
     * 全部标记为已读（仅当前用户自己的未读通知）
     */
    @PutMapping("/read-all")
    @OperationLog(type = "notice_read_all", description = "全部标记通知已读")
    public Result<Integer> markAllAsRead() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            int rows = sysNoticeService.markAllUnreadAsRead(userId);
            return Result.success("已将 " + rows + " 条通知标记为已读", rows);
        } catch (Exception e) {
            log.error("全部标记通知已读失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "全部标记通知已读失败: " + e.getMessage());
        }
    }

    // ==================== 删除（逻辑删除） ====================

    /**
     * 删除单条通知
     */
    @DeleteMapping("/delete/{id}")
    @OperationLog(type = "notice_delete", description = "删除通知")
    public Result<String> deleteNotice(@PathVariable("id") Long noticeId) {
        log.info("接收删除通知请求: noticeId={}", noticeId);
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            int rows = sysNoticeService.deleteNotice(noticeId, userId);
            if (rows <= 0) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "通知不存在或无权操作");
            }
            return Result.success("已删除该通知", null);
        } catch (Exception e) {
            log.error("删除通知失败: noticeId={}, {}", noticeId, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除通知失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除
     * 前端发的是【裸 JSON 数组】
     */
    @DeleteMapping("/batch-delete")
    @OperationLog(type = "notice_batch_delete", description = "批量删除通知")
    public Result<Integer> batchDeleteNotices(@RequestBody(required = false) List<Long> noticeIds) {
        log.info("接收批量删除通知请求: count={}", noticeIds == null ? 0 : noticeIds.size());
        Result<Integer> invalid = validateBatchIds(noticeIds);
        if (invalid != null) {
            return invalid;
        }
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            int rows = sysNoticeService.batchDeleteNotices(noticeIds, userId);
            return Result.success("已删除 " + rows + " 条通知", rows);
        } catch (Exception e) {
            log.error("批量删除通知失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量删除通知失败: " + e.getMessage());
        }
    }

    /**
     * 清空全部通知（仅当前用户自己的）
     * 前端按钮上写的是「此操作不可恢复」，实际是逻辑删除（is_deleted = 1），行还在
     */
    @DeleteMapping("/clear-all")
    @OperationLog(type = "notice_clear_all", description = "清空通知")
    public Result<Integer> clearAllNotices() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            int rows = sysNoticeService.clearAllNotices(userId);
            return Result.success("已清空 " + rows + " 条通知", rows);
        } catch (Exception e) {
            log.error("清空通知失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "清空通知失败: " + e.getMessage());
        }
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
