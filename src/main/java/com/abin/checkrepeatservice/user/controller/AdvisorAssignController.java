package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.user.service.AdvisorAssignService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;


/**
 * 指导老师分配控制器（对外提供分配、任务查询接口）
 */
@RestController
@RequestMapping("/api/advisor/assign")
@Api(tags = "指导老师分配管理")
public class AdvisorAssignController {

    @Resource
    private AdvisorAssignService advisorAssignService;

    /**
     * 接口1：自动分配指导老师（学生提交论文后调用，或管理员触发）
     */
    @PostMapping("/auto")
    @ApiOperation(value = "自动分配指导老师", notes = "仅“待分配”状态的论文可调用")
    @OperationLog(type = "admin_auto_assign", description = "管理员自动分配指导老师")
    public Result<Boolean> autoAssign(
            @RequestParam @NotNull Long paperSubmitId
    ) {
        return advisorAssignService.autoAssignAdvisor(paperSubmitId);
    }

    /**
     * 接口2：手动分配指导老师（仅管理员可调用）
     */
    @PostMapping("/manual")
    @ApiOperation(value = "手动分配指导老师", notes = "需管理员权限，指定论文ID和目标教师ID")
   @OperationLog(type = "admin_manual_assign", description = "管理员手动分配指导老师")
   public Result< ?> manualAssign(@RequestParam @NotNull Long paperId,
                                  @RequestParam @NotNull Long teacherId,
                                  @RequestParam String reason
    ) {
       return advisorAssignService.manualAssignAdvisor(paperId,teacherId,reason);
   }

    /**
     * 接口3：查询当前教师的指导任务列表（教师登录后调用）
     */
//    @GetMapping("/task/list")
//    @ApiOperation(value = "查询指导任务列表", notes = "教师登录后查询自己的任务，支持按状态筛选")
//    public Result<List<PaperAdvisorTaskVO>> getTaskList(
//            @ApiParam(value = "任务状态（1=待指导，2=指导中，3=指导完成，null=所有）")
//            @RequestParam(required = false) Integer status,
//            // 当前登录教师ID（从Token解析）
//            @RequestAttribute("loginUserId") Long advisorId
//    ) {
//        List<PaperAdvisorTaskVO> taskList = advisorAssignService.getAdvisorTaskList(advisorId, status);
//        return Result.success("任务列表查询成功", taskList);
//    }
//    // 接口4：指导任务取消
//    @PostMapping("/cancel")
//    @ApiOperation(value = "指导任务取消", notes = "学生仅能取消自己的“待指导”任务，管理员无限制")
//    public Result<?> cancelTask(
//            @RequestParam @NotNull Long paperId,
//            @RequestAttribute("loginUserId") Long operatorId
//    ) {
//        return advisorAssignService.cancelAdvisorTask(paperId, operatorId);
//    }
//
//    // 接口5：指导老师更换（管理员）
//    @PostMapping("/change")
//    @ApiOperation(value = "指导老师更换", notes = "仅管理员可调用，需指定论文ID和新指导老师ID")
//    public Result<?> changeAdvisor(
//            @RequestParam @NotNull Long paperId,
//            @RequestParam @NotNull Long newAdvisorId,
//            @RequestAttribute("loginUserId") Long operatorId
//    ) {
//        return advisorAssignService.changeAdvisor(paperId, newAdvisorId, operatorId);
//    }
//    /**
//     * 接口3：查询论文多轮指导历史（修复版）
//     * 权限规则：
//     * 1. 管理员：可查所有论文
//     * 2. 学生：仅可查自己提交的论文
//     * 3. 教师：可查自己参与过的所有轮次（含历史轮次）
//     */
//    @GetMapping("/round/history/{paperId}")
//    @ApiOperation(value = "查询多轮指导历史", notes = "管理员可查所有；学生查自己的；老师查自己参与的")
//    public Result<List<PaperAdvisorRoundVO>> getRoundHistory(
//            @PathVariable @NotNull(message = "论文ID不能为空") Long paperId,
//            @RequestAttribute("loginUserId") Long operatorId
//    ) {
//        // 修复问题1：处理论文不存在的场景（避免空指针）
//        PaperSubmit paper = paperSubmitService.getById(paperId);
//        if (paper == null || paper.getIsDeleted() == 1) {
//            throw new ResourceNotFoundException(ResultCode.PARAM_ERROR,null,paperId,"论文不存在或已删除（ID：" + paperId + "）");
//        }
//
//        // 修复问题2：优化权限校验（支持历史指导老师查询）
//        boolean isAdmin = sysUserService.isAdmin(operatorId);
//        boolean isStudentOwner = paper.getUserId().equals(operatorId); // 学生：自己的论文
//        // 教师：查询该论文所有轮次中，是否有自己作为指导老师的记录（含历史轮次）
//        boolean isTeacherInvolved = paperAdvisorRelService.count(
//                new QueryWrapper<PaperAdvisorRel>()
//                        .eq("paper_id", paperId)
//                        .eq("advisor_id", operatorId)
//                        .eq("is_deleted", 0)
//        ) > 0;
//
//        // 权限判定：仅管理员/学生本人/参与过的老师可查询
//        if (!isAdmin && !isStudentOwner && !isTeacherInvolved) {
//            throw new PermissionDeniedException(ResultCode.PERMISSION_NO_ACCESS,operatorId,null,"无权限查看该论文的指导历史（仅管理员、学生本人、参与指导的老师可查）");
//        }
//
//        // 修复问题3：返回适配前端的 VO 列表（含关联详情）
//        List<PaperAdvisorRoundVO> historyVOList = paperAdvisorRelService.listRoundHistoryWithDetail(paperId);
//        return Result.success("多轮指导历史查询成功", historyVOList);
//    }
}
