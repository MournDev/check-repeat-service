package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.student.dto.DeadlinesDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 时间节点配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Api(tags = "时间节点配置管理")
public class DeadlinesConfigController {
    
    private final SystemConfigService systemConfigService;
    
    /**
     * 获取时间节点配置
     */
    @GetMapping("/deadlines")
    @ApiOperation("获取时间节点配置")
    public Result<DeadlinesDTO> getDeadlinesConfig() {
        DeadlinesDTO deadlines = systemConfigService.getDeadlines();
        return Result.success(deadlines);
    }
    
    /**
     * 更新时间节点配置
     */
    @PutMapping("/deadlines")
    @ApiOperation("更新时间节点配置")
    public Result<Void> updateDeadlinesConfig(@RequestBody DeadlinesDTO deadlines) {
        log.info("更新时间节点配置：{}", deadlines);
        systemConfigService.updateDeadlines(deadlines);
        return Result.success("时间节点配置更新成功");
    }
}
