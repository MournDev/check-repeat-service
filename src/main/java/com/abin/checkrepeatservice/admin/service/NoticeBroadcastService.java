package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.NoticeBroadcastReq;
import com.abin.checkrepeatservice.common.Result;

import java.util.Map;

/**
 * 系统公告广播服务
 * 前端契约：POST /api/admin/notices/broadcast（仅超管）
 */
public interface NoticeBroadcastService {

    /**
     * 发布系统公告：按 targetRoles 逐用户落库消息中心（system_message），
     * 并对在线用户实时推送 NOTIFICATION（铃铛角标 +1 + 弹提示）
     *
     * @return data.sentCount = 实际落库的用户数
     */
    Result<Map<String, Object>> broadcast(NoticeBroadcastReq req);
}
