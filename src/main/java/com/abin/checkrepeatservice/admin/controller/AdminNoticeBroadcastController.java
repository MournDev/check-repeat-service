package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.NoticeBroadcastReq;
import com.abin.checkrepeatservice.admin.service.NoticeBroadcastService;
import com.abin.checkrepeatservice.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端「系统公告广播」控制器
 *
 * <p>路径挂在 /api/admin/notices（不是 /api/notices）：SecurityConfig 对
 * /api/admin/** 是 hasAuthority("ADMIN")，本类再收紧为 SUPER_ADMIN——
 * 与「性能配置写」「备份触发」同一档职责分离（P1 约定：广播属全站触达动作）。</p>
 *
 * <p>前端契约：SystemNoticeManagement.vue「系统公告」页签 →
 * POST /api/admin/notices/broadcast。</p>
 */
@RestController
@RequestMapping("/api/admin/notices")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
@Slf4j
public class AdminNoticeBroadcastController {

    @Resource
    private NoticeBroadcastService noticeBroadcastService;

    /**
     * 发布系统公告
     * 按 targetRoles 逐用户落库消息中心并对在线用户实时推送
     * 出参：data.sentCount = 实际落库的用户数
     */
    @PostMapping("/broadcast")
    public Result<java.util.Map<String, Object>> broadcast(@Valid @RequestBody NoticeBroadcastReq req) {
        log.info("接收系统公告发布请求: title={}, noticeType={}, targetRoles={}",
                req.getTitle(), req.getNoticeType(), req.getTargetRoles());
        return noticeBroadcastService.broadcast(req);
    }
}
