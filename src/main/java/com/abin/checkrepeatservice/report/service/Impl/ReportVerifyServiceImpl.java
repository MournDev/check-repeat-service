package com.abin.checkrepeatservice.report.service.Impl;

import com.abin.checkrepeatservice.common.utils.ReportHashUtils;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.report.dto.ReportVerifyVO;
import com.abin.checkrepeatservice.report.service.ReportVerifyService;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 报告验真服务实现（论文全生命周期 P5）
 *
 * 验真逻辑：报告编号定位报告 → 取 check_task 中与写哈希时相同的关键要素
 * （taskId/reportNo/checkRate/maxPerSource/citationRate/paperId + 盐）重算 SHA-256
 * → 与 check_report.content_hash 比对。
 *
 * 注意：历史报告（P5 之前生成）没有 content_hash，返回 valid=false 并说明，
 * 而不是误判为被篡改。
 */
@Service
@Slf4j
public class ReportVerifyServiceImpl implements ReportVerifyService {

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Value("${REPORT_HASH_SALT:check-repeat-report-salt}")
    private String reportHashSalt;

    @Override
    public ReportVerifyVO verify(String reportNo) {
        ReportVerifyVO vo = new ReportVerifyVO();
        vo.setReportNo(reportNo);
        vo.setValid(false);

        CheckReport report = checkReportMapper.selectOne(new LambdaQueryWrapper<CheckReport>()
                .eq(CheckReport::getReportNo, reportNo)
                .last("LIMIT 1"));
        if (report == null) {
            vo.setMessage("报告编号不存在，请核对后重试");
            return vo;
        }

        CheckTask task = checkTaskMapper.selectById(report.getTaskId());
        if (task == null) {
            vo.setMessage("报告对应的查重任务不存在");
            return vo;
        }

        if (report.getContentHash() == null || report.getContentHash().isBlank()) {
            fillMetadata(vo, task);
            vo.setMessage("该报告生成于验真码功能上线前（历史报告），无验真码");
            return vo;
        }

        // 用 DB 中同样的要素重算哈希（check_task.check_rate 与写哈希时为同一规范值）
        String recomputed = ReportHashUtils.sha256Hex(ReportHashUtils.buildHashSource(
                task.getId(), report.getReportNo(), task.getCheckRate(),
                task.getMaxPerSource(), task.getCitationRate(), task.getPaperId(), reportHashSalt));
        boolean valid = report.getContentHash().equalsIgnoreCase(recomputed);

        fillMetadata(vo, task);
        vo.setValid(valid);
        vo.setMessage(valid ? "验真通过：报告真实有效" : "验真不通过：报告内容可能被篡改或要素已变更");
        return vo;
    }

    private void fillMetadata(ReportVerifyVO vo, CheckTask task) {
        vo.setCheckRate(task.getCheckRate());
        vo.setMaxPerSource(task.getMaxPerSource());
        vo.setCitationRate(task.getCitationRate());
        vo.setCheckTime(task.getEndTime());
        PaperInfo paper = paperInfoMapper.selectById(task.getPaperId());
        vo.setPaperTitle(paper != null ? maskTitle(paper.getPaperTitle()) : null);
    }

    /**
     * 标题打码：保留首尾各 2 字符，中间以 **** 代替；过短标题仅保留首字符。
     * 公开接口不暴露完整标题，防止凭标题遍历枚举报告编号。
     */
    private String maskTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String t = title.trim();
        if (t.length() <= 4) {
            return t.charAt(0) + "****";
        }
        return t.substring(0, 2) + "****" + t.substring(t.length() - 2);
    }
}
