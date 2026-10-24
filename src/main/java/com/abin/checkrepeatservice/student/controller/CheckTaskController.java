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
        return checkTaskService.createCheckTask(paperId);
    }

    /**
     * 查询查重结果
     */
    @GetMapping("/result")
    public Result<CheckResultVO> getCheckResult(@RequestParam Long paperId) {
        CheckResultVO resultVO = checkTaskService.getCheckResult(paperId);
        return Result.success("查询成功", resultVO);
    }
}
