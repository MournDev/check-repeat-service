package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.ReviewTemplateSaveReq;
import com.abin.checkrepeatservice.teacher.service.ReviewTemplateService;
import com.abin.checkrepeatservice.teacher.vo.ReviewTemplateVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
public class ReviewTemplateController {

    @Resource
    private ReviewTemplateService reviewTemplateService;

    /** 当前教师可见的模板列表（自己的 + 公开的）；scenario 用于按通过/退回场景过滤 */
    @GetMapping("/getTemplatesList")
    public Result<List<ReviewTemplateVO>> getTemplatesList(
            @RequestParam(value = "scenario", required = false) String scenario) {
        return Result.success(reviewTemplateService.list(scenario));
    }

    /** 新建模板 */
    @PostMapping("/createTemplate")
    public Result<ReviewTemplateVO> createTemplate(@Valid @RequestBody ReviewTemplateSaveReq req) {
        return Result.success(reviewTemplateService.create(req));
    }

    /** 更新模板（仅限本人创建） */
    @PutMapping("/{templateId}")
    public Result<ReviewTemplateVO> updateTemplate(@PathVariable Long templateId,
                                                   @Valid @RequestBody ReviewTemplateSaveReq req) {
        return Result.success(reviewTemplateService.update(templateId, req));
    }

    /** 删除模板（软删，仅限本人创建） */
    @DeleteMapping("/{templateId}")
    public Result<Void> deleteTemplate(@PathVariable Long templateId) {
        reviewTemplateService.delete(templateId);
        // 与项目里其它 Result<Void> 的写法一致（Result.success(null) 会在
        // success(T) 与 success(String) 之间产生重载歧义，根本编不过）
        return Result.success("模板删除成功");
    }

    /** 使用一次模板：使用次数 +1、记录最后使用时间 */
    @PostMapping("/{templateId}/use")
    public Result<ReviewTemplateVO> useTemplate(@PathVariable Long templateId) {
        return Result.success(reviewTemplateService.use(templateId));
    }

}
