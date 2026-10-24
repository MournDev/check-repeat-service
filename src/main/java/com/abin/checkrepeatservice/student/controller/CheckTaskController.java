package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/check")
public class CheckTaskController {
    @Resource
    private CheckTaskService checkTaskService;

    /**
     * 创建查重任务
     */
    @PostMapping("/create")
    public Result<CheckResultVO> createCheckTask(@RequestParam Long paperId) {
        try {
            return checkTaskService.createCheckTask(paperId);
        } catch (BusinessException e) {
            // 业务异常（无权限/额度不足等）必须原样交给全局处理器，不能抹成 500
            throw e;
        } catch (Exception e) {
            return Result.error(ResultCode.SYSTEM_ERROR, "系统异常：");
        }
    }

    /**
     * 查询查重结果
     */
    @GetMapping("/result")
    public Result<CheckResultVO> getCheckResult(@RequestParam Long paperId) {
        try {
            CheckResultVO resultVO = checkTaskService.getCheckResult(paperId);
            return Result.success("查询成功", resultVO);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return Result.error(ResultCode.SYSTEM_ERROR, "系统异常：");
        }
    }
}
