package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.MaintenanceUpdateReq;
import com.abin.checkrepeatservice.admin.mapper.SystemParamMapper;
import com.abin.checkrepeatservice.admin.service.MaintenanceService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.maintenance.MaintenanceStateHolder;
import com.abin.checkrepeatservice.common.websocket.WebSocketSender;
import com.abin.checkrepeatservice.pojo.entity.SystemParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 维护模式服务实现
 *
 * <p>真值源是 system_param 表（契约第三节：废弃 system_config.system_basic 里的
 * maintenanceMode/maintenanceNotice 死数据）。内存缓存由
 * {@link MaintenanceStateHolder} 承担——拦截器只读内存，这里落库后调用
 * holder.update 刷新，保证「改完立即生效」且「每请求零查库」。</p>
 */
@Service
@Slf4j
public class MaintenanceServiceImpl implements MaintenanceService {

    /** 对前端契约的时间输出格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SystemParamMapper systemParamMapper;

    @Resource
    private MaintenanceStateHolder maintenanceStateHolder;

    @Resource
    private WebSocketSender webSocketSender;

    @Override
    public Result<Map<String, Object>> getSystemStatus() {
        return Result.success("获取系统状态成功", buildStatusMap());
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> updateMaintenance(MaintenanceUpdateReq req) {
        try {
            boolean turningOn = Boolean.TRUE.equals(req.getMaintenanceStatus());

            // 校验：开启维护时公告必填（关闭时可以只给开关）
            if (turningOn && (req.getMaintenanceNotice() == null || req.getMaintenanceNotice().isBlank())) {
                return Result.error(ResultCode.PARAM_ERROR, "开启维护模式时必须填写维护公告");
            }

            // 1. 落库 system_param（单例表）
            SystemParam param = systemParamMapper.selectOne(new LambdaQueryWrapper<>());
            if (param == null) {
                return Result.error(ResultCode.SYSTEM_ERROR, "系统参数记录不存在（system_param 表为空），无法更新维护模式");
            }
            param.setMaintenanceStatus(turningOn ? 1 : 0);
            param.setMaintenanceNotice(req.getMaintenanceNotice());
            param.setMaintenanceStartTime(req.getMaintenanceStartTime());
            param.setMaintenanceEndTime(req.getMaintenanceEndTime());
            param.setUpdateTime(LocalDateTime.now());
            systemParamMapper.updateById(param);

            // 2. 刷新内存缓存（拦截器下一请求即生效，无需重启）
            maintenanceStateHolder.update(turningOn, req.getMaintenanceNotice(),
                    req.getMaintenanceStartTime(), req.getMaintenanceEndTime());

            // 3. 广播状态变更：在线用户立即看到横幅出现/消失（前端订阅 /topic/announcements）
            Map<String, Object> broadcast = new LinkedHashMap<>();
            broadcast.put("type", "MAINTENANCE_STATUS_CHANGED");
            broadcast.put("maintenanceStatus", turningOn);
            broadcast.put("maintenanceNotice", req.getMaintenanceNotice());
            broadcast.put("maintenanceStartTime", formatTime(req.getMaintenanceStartTime()));
            broadcast.put("maintenanceEndTime", formatTime(req.getMaintenanceEndTime()));
            webSocketSender.sendToTopic("announcements", broadcast);

            log.info("维护模式已更新: status={}, notice={}", turningOn, req.getMaintenanceNotice());
            Map<String, Object> data = buildStatusMap();
            return Result.success(turningOn ? "维护模式已开启" : "维护模式已关闭", data);

        } catch (Exception e) {
            log.error("更新维护模式失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新维护模式失败: " + e.getMessage());
        }
    }

    /**
     * 组装前端契约的响应结构（时间转 yyyy-MM-dd HH:mm:ss 字符串，无值为 null）
     */
    private Map<String, Object> buildStatusMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("maintenanceStatus", maintenanceStateHolder.isMaintenance());
        data.put("maintenanceNotice", maintenanceStateHolder.getMaintenanceNotice());
        data.put("maintenanceStartTime", formatTime(maintenanceStateHolder.getMaintenanceStartTime()));
        data.put("maintenanceEndTime", formatTime(maintenanceStateHolder.getMaintenanceEndTime()));
        return data;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
