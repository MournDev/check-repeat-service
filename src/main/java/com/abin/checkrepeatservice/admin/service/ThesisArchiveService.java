package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.vo.ThesisArchiveVO;
import com.abin.checkrepeatservice.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 论文定稿与归档服务（P4）。
 *
 * <p>生命周期收口：终期答辩通过（defense_passed）→ 指导教师定稿（finalized）
 * → 管理员归档（archived）。归档时冻结论文、学生、导师、学院专业与查重报告快照，
 * 并把论文标记为比对库准入（library_admitted=1）。</p>
 */
public interface ThesisArchiveService {

    /**
     * 指导教师定稿确认：defense_passed → finalized。
     * 仅允许该论文的指导教师（或管理员）操作。
     */
    Result<String> finalizePaper(Long paperId);

    /**
     * 管理员归档：finalized → archived，落 thesis_archive 证据快照。
     * 归档是比对库正式准入节点（要求正文已入库）。
     */
    Result<ThesisArchiveVO> archivePaper(Long paperId, String remark);

    /**
     * 归档记录分页列表（可按关键词、学院、专业过滤）
     */
    Result<Page<ThesisArchiveVO>> listArchives(String keyword, Long collegeId, Long majorId,
                                               Integer currentPage, Integer pageSize);

    /**
     * 归档记录详情（证据快照）
     */
    Result<ThesisArchiveVO> getArchive(Long id);
}
