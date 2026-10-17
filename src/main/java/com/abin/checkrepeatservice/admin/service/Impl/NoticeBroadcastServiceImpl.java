package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.NoticeBroadcastReq;
import com.abin.checkrepeatservice.admin.service.NoticeBroadcastService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.common.websocket.WebSocketMessage;
import com.abin.checkrepeatservice.common.websocket.WebSocketSender;
import com.abin.checkrepeatservice.mapper.SysRoleMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.user.mapper.SystemMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统公告广播服务实现
 *
 * <p><b>数据落点（契约第四节）</b>：写消息中心表 system_message（前端铃铛
 * getMessageList 的数据源），messageType=ANNOUNCEMENT——在线弹窗 + 铃铛角标 +
 * 消息中心可见，触达面最大。不写 sys_notice（那是「通知管理」页自己的存储）。</p>
 *
 * <p><b>推送通道（契约二选一）</b>：选「按人推送」——对每个在线用户发
 * /queue/messages 的 NOTIFICATION 帧（MainLayout 已有处理：弹提示+未读+1）。
 * 不再同时发 /topic/announcements 主题广播，避免前端双重弹窗；
 * 主题通道保留给维护状态变更（MAINTENANCE_STATUS_CHANGED）专用。</p>
 *
 * <p><b>失效兜底</b>：消息中心列表过滤 expireTime &gt; now（见 MessageServiceImpl），
 * 公告默认 30 天后过期，过期后自动从铃铛/列表消失。</p>
 */
@Service
@Slf4j
public class NoticeBroadcastServiceImpl implements NoticeBroadcastService {

    /** 合法公告类型 */
    private static final Set<String> ALLOWED_NOTICE_TYPES =
            new HashSet<>(Arrays.asList("MAINTENANCE", "VERSION", "GENERAL"));

    /** 合法目标角色（契约固定；SUPER_ADMIN 不在其中——公告不主动打扰超管） */
    private static final Set<String> ALLOWED_TARGET_ROLES =
            new HashSet<>(Arrays.asList("STUDENT", "TEACHER", "ADMIN"));

    /** 缺省目标角色 = 全部（契约：targetRoles 缺省或空数组 = 全部角色） */
    private static final List<String> DEFAULT_TARGET_ROLES =
            Arrays.asList("STUDENT", "TEACHER", "ADMIN");

    /** 公告有效期：30 天后过期 */
    private static final int EXPIRE_DAYS = 30;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SystemMessageMapper systemMessageMapper;

    @Resource
    private WebSocketSender webSocketSender;

    @Override
    @Transactional
    public Result<Map<String, Object>> broadcast(NoticeBroadcastReq req) {
        try {
            // ===== 1. 业务校验（jakarta 注解之外的自定义规则） =====
            if (!ALLOWED_NOTICE_TYPES.contains(req.getNoticeType())) {
                return Result.error(ResultCode.PARAM_ERROR, "公告类型只能是 MAINTENANCE、VERSION 或 GENERAL");
            }
            List<String> targetRoles = (req.getTargetRoles() == null || req.getTargetRoles().isEmpty())
                    ? DEFAULT_TARGET_ROLES : req.getTargetRoles();
            for (String role : targetRoles) {
                if (!ALLOWED_TARGET_ROLES.contains(role)) {
                    return Result.error(ResultCode.PARAM_ERROR,
                            "目标角色只能包含 STUDENT、TEACHER、ADMIN，非法值: " + role);
                }
            }

            // ===== 2. 解析目标用户：角色编码 → role_id → 启用状态用户 =====
            List<Long> roleIds = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                            .in(SysRole::getRoleCode, targetRoles))
                    .stream().map(SysRole::getId).toList();
            if (roleIds.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "目标角色不存在: " + targetRoles);
            }
            // status: 0-禁用 1-正常；禁用账号不发（发了也登录不了）
            List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .in(SysUser::getRoleId, roleIds)
                    .eq(SysUser::getStatus, 1));
            if (users.isEmpty()) {
                return Result.success("公告发布成功", Map.of("sentCount", 0));
            }

            // ===== 3. 逐用户落库消息中心（sender = 当前超管） =====
            Long senderId = UserBusinessInfoUtils.getCurrentUserId();
            LocalDateTime now = LocalDateTime.now();
            // 优先级映射：契约 0/1/2（sys_notice 语义）→ system_message 1/2/3
            int messagePriority = req.getPriority() + 1;
            for (SysUser user : users) {
                SystemMessage message = new SystemMessage();
                message.setTitle(req.getTitle());
                message.setContent(req.getContent());
                message.setMessageType("ANNOUNCEMENT");
                message.setSenderId(senderId);
                message.setReceiverId(user.getId());
                message.setRelatedType("notice");
                message.setIsRead(0);
                message.setPriority(messagePriority);
                message.setExpireTime(now.plusDays(EXPIRE_DAYS));
                systemMessageMapper.insert(message);
            }

            // ===== 4. 在线用户实时推送（离线用户登录后从消息中心可见） =====
            String createTimeText = TIME_FORMATTER.format(now);
            for (SysUser user : users) {
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("title", req.getTitle());
                content.put("content", req.getContent());
                content.put("noticeType", req.getNoticeType());
                content.put("priority", req.getPriority());
                content.put("createTime", createTimeText);
                webSocketSender.sendToUser(user.getId(), "/queue/messages",
                        new WebSocketMessage("NOTIFICATION", content));
            }

            log.info("系统公告发布成功: title={}, noticeType={}, targetRoles={}, 落库用户数={}",
                    req.getTitle(), req.getNoticeType(), targetRoles, users.size());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sentCount", users.size());
            return Result.success("公告发布成功", data);

        } catch (Exception e) {
            log.error("系统公告发布失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "公告发布失败: " + "系统异常，请稍后重试");
        }
    }
}
