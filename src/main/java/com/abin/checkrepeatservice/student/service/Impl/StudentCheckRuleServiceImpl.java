package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.PaperTypeEnum;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckRule;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.CheckRuleMapper;
import com.abin.checkrepeatservice.student.service.StudentCheckRuleService;
import com.abin.checkrepeatservice.student.vo.StudentCheckRuleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 学生端查重规则查询实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCheckRuleServiceImpl implements StudentCheckRuleService {

    private final CheckRuleMapper checkRuleMapper;

    private final SysUserMapper sysUserMapper;

    private final SimilarityThresholdResolver similarityThresholdResolver;

    @Override
    public Result<StudentCheckRuleVO> getCurrentCheckRule(Long studentId) {
        // 未删除的默认规则。管理端只允许存在一条 is_default=1（saveOrUpdateCheckRule 里有校验），
        // 但历史数据不保证，所以按 id 倒序取第一条把"取哪一行"定死，避免 selectOne 多行报错。
        CheckRule rule = checkRuleMapper.selectOne(new LambdaQueryWrapper<CheckRule>()
                .eq(CheckRule::getIsDefault, 1)
                .eq(CheckRule::getIsDeleted, 0)
                .orderByDesc(CheckRule::getId)
                .last("LIMIT 1"));

        if (rule == null) {
            // 没有默认规则时不编造次数/间隔等数值，直接告知未配置，前端据此隐藏规则卡片
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "未配置默认查重规则");
        }

        StudentCheckRuleVO vo = new StudentCheckRuleVO();
        vo.setRuleName(rule.getRuleName());
        vo.setRuleCode(rule.getRuleCode());
        vo.setDescription(rule.getDescription());
        vo.setMaxCheckCount(rule.getMaxCheckCount());
        vo.setMaxReSubmitCount(rule.getMaxReSubmitCount());
        vo.setCheckInterval(rule.getCheckInterval());

        // 阈值不走 rule.getPassThreshold()：那一列已废弃，编辑规则时根本不写，
        // 用它会显示一个与实际判定无关的值。按学生自己的学院/专业解析才是生效阈值。
        SysUser student = (studentId == null) ? null : sysUserMapper.selectById(studentId);
        Long collegeId = (student == null) ? null : student.getCollegeId();
        Long majorId = (student == null) ? null : student.getMajorId();

        // 不带类型的解析结果，留给灰度期（前端还没拿到 paperTypeThresholds 时兜底显示）
        vo.setPassThreshold(similarityThresholdResolver.resolve(collegeId, majorId));

        // 逐类型解析。这里是学生级卡片、没有单篇论文，所以 subjectId 传 null，
        // 解析链退化成「专业(+类型) > 学院(+类型) > 仅类型 > 全局」，
        // 也就是**不含学科维度**的那部分。
        // 注意这意味着卡片值不保证等于某篇具体论文的判定值：那篇论文的学科若单独配了
        // 更严的阈值，实际判定会比卡片显示的更低。要让卡片也精确，就得把学科带进来，
        // 而学科是论文的属性、不是学生的 —— 卡片上给不出。
        List<StudentCheckRuleVO.PaperTypeThreshold> thresholds = new ArrayList<>(PaperTypeEnum.values().length);
        for (PaperTypeEnum type : PaperTypeEnum.values()) {
            StudentCheckRuleVO.PaperTypeThreshold row = new StudentCheckRuleVO.PaperTypeThreshold();
            row.setPaperType(type.getCode());
            row.setPaperTypeDesc(type.getDescription());
            row.setPassThreshold(similarityThresholdResolver.resolve(null, collegeId, majorId, type.getCode()));
            thresholds.add(row);
        }
        vo.setPaperTypeThresholds(thresholds);

        return Result.success(vo);
    }
}
