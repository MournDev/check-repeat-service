package com.abin.checkrepeatservice.common.maintenance;

import com.abin.checkrepeatservice.admin.mapper.SystemParamMapper;
import com.abin.checkrepeatservice.pojo.entity.SystemParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 维护模式内存状态持有者 —— 拦截器读这里，绝不每请求查库
 *
 * <p>写路径只有一条：MaintenanceServiceImpl.updateMaintenance 落库 system_param
 * 后调 {@link #update} 刷新内存。启动时从 system_param 表加载一次
 * （读失败按「非维护」处理，绝不阻断启动——与 AsyncConfig 读性能配置同一套兜底约定）。</p>
 *
 * <p>放在 common 而不是 admin.service：MaintenanceInterceptor（common）与
 * MaintenanceServiceImpl（admin）共用，放 admin 会让 common 依赖 admin 包。</p>
 *
 * <p>注：直接注入 SystemParamMapper（无下游依赖），不走 MaintenanceService，
 * 否则 Holder → Service → Holder 成环。</p>
 */
@Component
@Slf4j
public class MaintenanceStateHolder {

    @Resource
    private SystemParamMapper systemParamMapper;

    // ==================== volatile 状态（读多写少） ====================

    private volatile boolean maintenanceStatus = false;
    private volatile String maintenanceNotice;
    private volatile LocalDateTime maintenanceStartTime;
    private volatile LocalDateTime maintenanceEndTime;

    /**
     * 启动时从 system_param 表（单例表）加载维护状态
     */
    @PostConstruct
    public void loadFromDatabase() {
        try {
            SystemParam param = systemParamMapper.selectOne(new LambdaQueryWrapper<>());
            if (param != null) {
                this.maintenanceStatus = param.getMaintenanceStatus() != null
                        && param.getMaintenanceStatus() == 1;
                this.maintenanceNotice = param.getMaintenanceNotice();
                this.maintenanceStartTime = param.getMaintenanceStartTime();
                this.maintenanceEndTime = param.getMaintenanceEndTime();
            }
            log.info("维护模式状态已加载: maintenanceStatus={}, notice={}",
                    maintenanceStatus, maintenanceNotice);
        } catch (Exception e) {
            // 兜底：启动期读库失败按「非维护」处理，绝不阻断应用启动
            log.warn("启动时读取维护状态失败，按非维护处理: {}", e.getMessage());
        }
    }

    /**
     * 落库成功后刷新内存（唯一写入口）
     */
    public synchronized void update(boolean status, String notice,
                                    LocalDateTime startTime, LocalDateTime endTime) {
        this.maintenanceStatus = status;
        this.maintenanceNotice = notice;
        this.maintenanceStartTime = startTime;
        this.maintenanceEndTime = endTime;
    }

    public boolean isMaintenance() {
        return maintenanceStatus;
    }

    public String getMaintenanceNotice() {
        return maintenanceNotice;
    }

    public LocalDateTime getMaintenanceStartTime() {
        return maintenanceStartTime;
    }

    public LocalDateTime getMaintenanceEndTime() {
        return maintenanceEndTime;
    }
}
