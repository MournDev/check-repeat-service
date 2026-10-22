package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.ThesisArchiveReq;
import com.abin.checkrepeatservice.admin.service.ThesisArchiveService;
import com.abin.checkrepeatservice.admin.vo.ThesisArchiveVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
public class AdminThesisArchiveController {

    @Resource
    private ThesisArchiveService thesisArchiveService;

    /**
     * 管理员兜底定稿：defense_passed → finalized（教师未操作时由教务确认）
     */
    @PostMapping("/{paperId}/finalize")
    @OperationLog(type = "admin_thesis_finalize", description = "管理员定稿论文")
    public Result<String> finalize(@PathVariable Long paperId) {
        return thesisArchiveService.finalizePaper(paperId);
    }

    /**
     * 归档：finalized → archived，落归档证据快照并使论文正式进入比对库
     */
    @PostMapping("/{paperId}/archive")
    @OperationLog(type = "admin_thesis_archive", description = "管理员归档论文")
    public Result<ThesisArchiveVO> archive(@PathVariable Long paperId,
                                           @Valid @RequestBody ThesisArchiveReq req) {
        return thesisArchiveService.archivePaper(paperId, req == null ? null : req.getRemark());
    }

    /**
     * 归档记录列表（分页）
     */
    @GetMapping("/archives")
    public Result<Page<ThesisArchiveVO>> listArchives(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "collegeId", required = false) Long collegeId,
            @RequestParam(value = "majorId", required = false) Long majorId,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return thesisArchiveService.listArchives(keyword, collegeId, majorId, currentPage, pageSize);
    }

    /**
     * 归档记录详情（证据快照）
     */
    @GetMapping("/archives/{id}")
    public Result<ThesisArchiveVO> getArchive(@PathVariable Long id) {
        return thesisArchiveService.getArchive(id);
    }
}
