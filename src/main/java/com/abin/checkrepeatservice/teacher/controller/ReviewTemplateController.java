package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.ReviewTemplateSaveReq;
import com.abin.checkrepeatservice.teacher.service.ReviewTemplateService;
import com.abin.checkrepeatservice.teacher.vo.ReviewTemplateVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核意见模板控制器：仅教师角色可访问。
 *
 * <p>路径与方法照前端 src/api/teacher.js 里既有的调用逐字对齐
 * （getTemplatesList / createTemplate 这类命名并不 REST，但改前端 URL
 * 只是把改动面变大，没有实际收益）。</p>
 */
@RestController
@RequestMapping("/api/teacher/review-templates")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "审核意见模板", description = "审核意见模板的查询、创建、更新与使用")
public class ReviewTemplateController {

    @Resource
    private ReviewTemplateService reviewTemplateService;

    /** 当前教师可见的模板列表（自己的 + 公开的）；scenario 用于按通过/退回场景过滤 */
    @GetMapping("/getTemplatesList")
    @Operation(summary = "获取审核意见模板列表", description = "查询当前教师可见的模板（本人创建及公开模板），可按场景过滤")
    public Result<List<ReviewTemplateVO>> getTemplatesList(
            @Parameter(description = "场景过滤：通过 / 退回")
            @RequestParam(value = "scenario", required = false) String scenario) {
        return Result.success(reviewTemplateService.list(scenario));
    }

    /** 新建模板 */
    @PostMapping("/createTemplate")
    @Operation(summary = "新建审核意见模板")
    public Result<ReviewTemplateVO> createTemplate(
            @Parameter(description = "模板保存请求体", required = true)
            @Valid @RequestBody ReviewTemplateSaveReq req) {
        return Result.success(reviewTemplateService.create(req));
    }

    /** 更新模板（仅限本人创建） */
    @PutMapping("/{templateId}")
    @Operation(summary = "更新审核意见模板", description = "仅限创建人操作")
    public Result<ReviewTemplateVO> updateTemplate(
            @Parameter(description = "模板ID", required = true)
            @PathVariable Long templateId,
            @Parameter(description = "模板保存请求体", required = true)
            @Valid @RequestBody ReviewTemplateSaveReq req) {
        return Result.success(reviewTemplateService.update(templateId, req));
    }

    /** 删除模板（软删，仅限本人创建） */
    @DeleteMapping("/{templateId}")
    @Operation(summary = "删除审核意见模板", description = "软删除，仅限创建人操作")
    public Result<Void> deleteTemplate(
            @Parameter(description = "模板ID", required = true)
            @PathVariable Long templateId) {
        reviewTemplateService.delete(templateId);
        // 与项目里其它 Result<Void> 的写法一致（Result.success(null) 会在
        // success(T) 与 success(String) 之间产生重载歧义，根本编不过）
        return Result.success("模板删除成功");
    }

    /** 使用一次模板：使用次数 +1、记录最后使用时间 */
    @PostMapping("/{templateId}/use")
    @Operation(summary = "使用审核意见模板", description = "使用次数加一并记录最后使用时间")
    public Result<ReviewTemplateVO> useTemplate(
            @Parameter(description = "模板ID", required = true)
            @PathVariable Long templateId) {
        return Result.success(reviewTemplateService.use(templateId));
    }

}
