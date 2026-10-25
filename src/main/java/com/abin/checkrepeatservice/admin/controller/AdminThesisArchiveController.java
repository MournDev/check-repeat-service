package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.ThesisArchiveReq;
import com.abin.checkrepeatservice.admin.service.ThesisArchiveService;
import com.abin.checkrepeatservice.admin.vo.ThesisArchiveVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端论文定稿与归档控制器（P4）：仅管理员角色可访问。
 *
 * <p>归档前必须由指导教师（或管理员兜底）完成定稿确认；归档时冻结学位授予证据快照。</p>
 */
@RestController
@RequestMapping("/api/admin/thesis")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "论文归档", description = "管理员定稿/归档/归档记录查询")
public class AdminThesisArchiveController {

    @Resource
    private ThesisArchiveService thesisArchiveService;

    /**
     * 管理员兜底定稿：defense_passed → finalized（教师未操作时由教务确认）
     */
    @Operation(summary = "管理员兜底定稿", description = "教师未操作时由教务确认，将论文由 defense_passed 推进为 finalized")
    @PostMapping("/{paperId}/finalize")
    @OperationLog(type = "admin_thesis_finalize", description = "管理员定稿论文")
    public Result<String> finalize(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId) {
        return thesisArchiveService.finalizePaper(paperId);
    }

    /**
     * 归档：finalized → archived，落归档证据快照并使论文正式进入比对库
     */
    @Operation(summary = "归档论文", description = "将论文由 finalized 推进为 archived，落归档证据快照并进入比对库")
    @PostMapping("/{paperId}/archive")
    @OperationLog(type = "admin_thesis_archive", description = "管理员归档论文")
    public Result<ThesisArchiveVO> archive(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId,
            @Parameter(description = "归档请求体：remark 归档备注", required = true)
            @Valid @RequestBody ThesisArchiveReq req) {
        return thesisArchiveService.archivePaper(paperId, req == null ? null : req.getRemark());
    }

    /**
     * 归档记录列表（分页）
     */
    @Operation(summary = "归档记录列表", description = "分页查询论文归档记录")
    @GetMapping("/archives")
    public Result<Page<ThesisArchiveVO>> listArchives(
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "学院ID（可选）")
            @RequestParam(value = "collegeId", required = false) Long collegeId,
            @Parameter(description = "专业ID（可选）")
            @RequestParam(value = "majorId", required = false) Long majorId,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return thesisArchiveService.listArchives(keyword, collegeId, majorId, currentPage, pageSize);
    }

    /**
     * 归档记录详情（证据快照）
     */
    @Operation(summary = "归档记录详情", description = "查询指定归档记录的证据快照")
    @GetMapping("/archives/{id}")
    public Result<ThesisArchiveVO> getArchive(
            @Parameter(description = "归档记录ID", required = true)
            @PathVariable Long id) {
        return thesisArchiveService.getArchive(id);
    }
}
