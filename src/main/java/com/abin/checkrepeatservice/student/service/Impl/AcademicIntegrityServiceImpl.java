package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.pojo.entity.AcademicChecklist;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.dto.AcademicResourceDTO;
import com.abin.checkrepeatservice.student.dto.ChecklistItemDTO;
import com.abin.checkrepeatservice.student.dto.PersonalAdviceDTO;
import com.abin.checkrepeatservice.student.mapper.AcademicChecklistMapper;
import com.abin.checkrepeatservice.student.service.AcademicIntegrityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 学术诚信服务实现类
 */
@Slf4j
@Service
public class AcademicIntegrityServiceImpl implements AcademicIntegrityService {
    
    @Resource
    private AcademicChecklistMapper academicChecklistMapper;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private CheckTaskMapper checkTaskMapper;
    
    @Override
    public PersonalAdviceDTO getPersonalAdvice(Long studentId) {
        try {
            log.info("获取学生个性化学术建议 - 学生ID: {}", studentId);
            
            // 1. 获取学生的最新论文
            PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
            if (latestPaper == null) {
                // 如果没有论文，返回默认建议
                return createDefaultPersonalAdvice();
            }
            
            // 2. 获取最新的查重任务
            CheckTask latestTask = checkTaskMapper.selectLatestByPaperId(latestPaper.getId());
            if (latestTask == null || latestTask.getCheckRate() == null) {
                return createDefaultPersonalAdvice();
            }
            
            // 3. 基于查重结果生成个性化建议
            PersonalAdviceDTO advice = new PersonalAdviceDTO();
            advice.setVersion(1); // 使用固定版本号
            
            // 4. 分析高风险区域（模拟数据）
            List<PersonalAdviceDTO.HighRiskAreaDTO> highRiskAreas = analyzeHighRiskAreas(latestTask.getCheckRate().doubleValue());
            advice.setHighRiskAreas(highRiskAreas);
            
            // 5. 识别表现良好的方面
            List<PersonalAdviceDTO.GoodAspectDTO> goodAspects = identifyGoodAspects(latestTask.getCheckRate().doubleValue());
            advice.setGoodAspects(goodAspects);
            
            // 6. 生成通用建议
            List<String> generalTips = generateGeneralTips(latestTask.getCheckRate().doubleValue());
            advice.setGeneralTips(generalTips);
            
            log.info("个性化学术建议生成成功 - 学生ID: {}, 相似度: {}", studentId, latestTask.getCheckRate());
            return advice;
            
        } catch (Exception e) {
            log.error("获取个性化学术建议失败 - 学生ID: {}", studentId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取个性化学术建议失败");
        }
    }
    
    @Override
    public List<AcademicResourceDTO> getRecommendedResources(Long studentId, String resourceType) {
        try {
            log.info("获取推荐学习资源 - 学生ID: {}, 资源类型: {}", studentId, resourceType);
            
            // 1. 获取学生最新论文信息，用于个性化推荐
            PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
            String subjectArea = latestPaper != null ? latestPaper.getSubjectCode() : "general";
            
            // 2. 根据学科领域和资源类型筛选资源
            List<AcademicResourceDTO> allResources = getAllAcademicResources();
            List<AcademicResourceDTO> filteredResources = allResources.stream()
                    .filter(resource -> resourceType == null || resource.getType().equals(resourceType))
                    .filter(resource -> "general".equals(subjectArea) || resource.getCategory().contains(subjectArea.toLowerCase()))
                    .limit(10)
                    .collect(Collectors.toList());
            
            log.info("推荐学习资源获取成功 - 学生ID: {}, 返回资源数: {}", studentId, filteredResources.size());
            return filteredResources;
            
        } catch (Exception e) {
            log.error("获取推荐学习资源失败 - 学生ID: {}", studentId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取推荐学习资源失败");
        }
    }
    
    @Override
    public List<ChecklistItemDTO> getChecklist(Long studentId) {
        try {
            log.info("获取用户检查清单 - 学生ID: {}", studentId);
            
            // 1. 检查用户是否已有检查清单
            LambdaQueryWrapper<AcademicChecklist> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(AcademicChecklist::getStudentId, studentId);
            List<AcademicChecklist> checklistItems = academicChecklistMapper.selectList(queryWrapper);
            
            // 2. 如果没有，初始化默认检查清单
            if (checklistItems.isEmpty()) {
                initializeChecklist(studentId);
                checklistItems = academicChecklistMapper.selectList(queryWrapper);
            }
            
            // 3. 转换为DTO
            List<ChecklistItemDTO> result = checklistItems.stream()
                    .map(item -> {
                        ChecklistItemDTO dto = new ChecklistItemDTO();
                        dto.setItemId(item.getId());
                        dto.setText(item.getText());
                        dto.setChecked(item.getChecked());
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            log.info("用户检查清单获取成功 - 学生ID: {}, 项目数: {}", studentId, result.size());
            return result;
            
        } catch (Exception e) {
            log.error("获取用户检查清单失败 - 学生ID: {}", studentId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "获取用户检查清单失败");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateChecklistItem(Long studentId, Long itemId, Boolean checked) {
        try {
            log.info("更新检查项状态 - 学生ID: {}, 项目ID: {}, 状态: {}", studentId, itemId, checked);
            
            // 1. 验证权限
            AcademicChecklist item = academicChecklistMapper.selectById(itemId);
            if (item == null || !item.getStudentId().equals(studentId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限操作此检查项");
            }
            
            // 2. 更新状态
            item.setChecked(checked);
            item.setUpdateTime(LocalDateTime.now());
            int result = academicChecklistMapper.updateById(item);
            
            log.info("检查项状态更新成功 - 项目ID: {}, 结果: {}", itemId, result > 0);
            return result > 0;
            
        } catch (Exception e) {
            log.error("更新检查项状态失败 - 学生ID: {}, 项目ID: {}", studentId, itemId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "更新检查项状态失败");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initializeChecklist(Long studentId) {
        try {
            log.info("初始化用户检查清单 - 学生ID: {}", studentId);
            
            // 1. 检查是否已存在
            LambdaQueryWrapper<AcademicChecklist> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(AcademicChecklist::getStudentId, studentId);
            if (academicChecklistMapper.selectCount(queryWrapper) > 0) {
                log.info("用户检查清单已存在，无需初始化 - 学生ID: {}", studentId);
                return;
            }
            
            // 2. 创建默认检查清单项
            List<String> defaultItems = Arrays.asList(
                "引用时标注作者和年份",
                "使用引号标识直接引用",
                "提供完整的参考文献",
                "避免过度依赖单一来源",
                "区分自己的观点和引用内容",
                "使用文献管理工具",
                "检查引用格式是否规范",
                "确认所有引用都有对应文献"
            );
            
            for (int i = 0; i < defaultItems.size(); i++) {
                AcademicChecklist item = new AcademicChecklist();
                item.setStudentId(studentId);
                item.setText(defaultItems.get(i));
                item.setChecked(false);
                item.setSort(i + 1);
                UserBusinessInfoUtils.setAuditField(item, true);
                academicChecklistMapper.insert(item);
            }
            
            log.info("用户检查清单初始化成功 - 学生ID: {}, 项目数: {}", studentId, defaultItems.size());
            
        } catch (Exception e) {
            log.error("初始化用户检查清单失败 - 学生ID: {}", studentId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "初始化用户检查清单失败");
        }
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 创建默认个性化建议
     */
    private PersonalAdviceDTO createDefaultPersonalAdvice() {
        PersonalAdviceDTO advice = new PersonalAdviceDTO();
        advice.setVersion(1);
        
        // 默认高风险区域
        PersonalAdviceDTO.HighRiskAreaDTO defaultArea = new PersonalAdviceDTO.HighRiskAreaDTO();
        defaultArea.setSection("文献综述");
        defaultArea.setSimilarity(25.0);
        defaultArea.setIssue("相似度偏高");
        defaultArea.setSuggestion("建议重新梳理文献脉络，增加个人观点表达，减少直接引用");
        
        advice.setHighRiskAreas(Arrays.asList(defaultArea));
        
        // 默认良好方面
        PersonalAdviceDTO.GoodAspectDTO defaultGood = new PersonalAdviceDTO.GoodAspectDTO();
        defaultGood.setSection("研究方法");
        defaultGood.setSimilarity(5.0);
        defaultGood.setStrength("原创性较好");
        defaultGood.setEncouragement("继续保持独立思考和创新精神");
        
        advice.setGoodAspects(Arrays.asList(defaultGood));
        
        // 默认通用建议
        advice.setGeneralTips(Arrays.asList(
            "养成及时记录参考文献的习惯",
            "学会用自己的语言表述他人观点",
            "定期使用查重工具自查",
            "参加学术写作培训课程"
        ));
        
        return advice;
    }
    
    /**
     * 分析高风险区域
     */
    private List<PersonalAdviceDTO.HighRiskAreaDTO> analyzeHighRiskAreas(double similarity) {
        List<PersonalAdviceDTO.HighRiskAreaDTO> areas = new ArrayList<>();
        Random random = new Random();
        
        // 根据相似度生成不同的风险区域
        if (similarity > 30) {
            // 高相似度时，多个高风险区域
            String[] sections = {"文献综述", "理论框架", "研究现状"};
            for (String section : sections) {
                PersonalAdviceDTO.HighRiskAreaDTO area = new PersonalAdviceDTO.HighRiskAreaDTO();
                area.setSection(section);
                area.setSimilarity(25.0 + random.nextDouble() * 15); // 25-40%
                area.setIssue("相似度过高");
                area.setSuggestion("建议重新组织语言表达，增加原创性内容");
                areas.add(area);
            }
        } else if (similarity > 20) {
            // 中等相似度时，1-2个风险区域
            PersonalAdviceDTO.HighRiskAreaDTO area = new PersonalAdviceDTO.HighRiskAreaDTO();
            area.setSection("文献综述");
            area.setSimilarity(20.0 + random.nextDouble() * 15); // 20-35%
            area.setIssue("相似度偏高");
            area.setSuggestion("建议完善引用标注，优化段落表达");
            areas.add(area);
        }
        
        return areas;
    }
    
    /**
     * 识别表现良好的方面
     */
    private List<PersonalAdviceDTO.GoodAspectDTO> identifyGoodAspects(double similarity) {
        List<PersonalAdviceDTO.GoodAspectDTO> aspects = new ArrayList<>();
        Random random = new Random();
        
        String[] goodSections = {"研究方法", "数据分析", "结论"};
        for (String section : goodSections) {
            PersonalAdviceDTO.GoodAspectDTO aspect = new PersonalAdviceDTO.GoodAspectDTO();
            aspect.setSection(section);
            aspect.setSimilarity(5.0 + random.nextDouble() * 10); // 5-15%
            aspect.setStrength("原创性较好");
            aspect.setEncouragement("继续保持独立思考和严谨态度");
            aspects.add(aspect);
        }
        
        return aspects;
    }
    
    /**
     * 生成通用改进建议
     */
    private List<String> generateGeneralTips(double similarity) {
        List<String> tips = new ArrayList<>();
        
        tips.add("养成及时记录参考文献的习惯");
        tips.add("学会用自己的语言表述他人观点");
        
        if (similarity > 25) {
            tips.add("重点修改高相似度章节，重新组织语言");
            tips.add("增加原创性分析和批判性思考");
        } else {
            tips.add("继续保持良好的学术写作习惯");
            tips.add("定期使用查重工具进行自查");
        }
        
        tips.add("参加学术写作培训课程");
        tips.add("向导师请教具体的修改建议");
        
        return tips;
    }
    
    /**
     * 获取所有学术资源（模拟数据）
     */
    private List<AcademicResourceDTO> getAllAcademicResources() {
        List<AcademicResourceDTO> resources = new ArrayList<>();
        
        // 图书资源
        resources.add(createResource(1L, "《学术写作规范手册》", "book", 
            "详细介绍学术写作的各项规范和标准", "/resources/handbook.pdf", "writing"));
        resources.add(createResource(2L, "《文献检索与利用》", "book", 
            "系统介绍文献检索方法和技巧", "/resources/literature-search.pdf", "research"));
        
        // 在线资源
        resources.add(createResource(3L, "学校图书馆引用格式指南", "online", 
            "官方发布的各类引用格式标准", "https://library.university.edu/citation", "general"));
        resources.add(createResource(4L, "学术诚信政策解读", "online", 
            "详细解读学校的学术诚信相关政策", "https://academic-integrity.university.edu", "policy"));
        
        // 视频教程
        resources.add(createResource(5L, "EndNote文献管理软件教程", "video", 
            "从入门到精通的文献管理软件使用教程", "/videos/endnote-tutorial.mp4", "tools"));
        resources.add(createResource(6L, "学术写作技巧系列课程", "video", 
            "涵盖选题、文献综述、论文结构等完整写作指导", "/videos/academic-writing-series.mp4", "writing"));
        
        // 文档资料
        resources.add(createResource(7L, "查重系统使用说明", "document", 
            "详细的操作指南和注意事项", "/documents/plagiarism-check-guide.docx", "tools"));
        resources.add(createResource(8L, "常见引用格式模板", "document", 
            "APA、MLA、Chicago等主流格式的模板文件", "/documents/citation-templates.zip", "writing"));
        
        // 课程资源
        resources.add(createResource(9L, "学术道德与规范课程", "course", 
            "系统的学术诚信教育在线课程", "https://mooc.university.edu/academic-integrity", "ethics"));
        resources.add(createResource(10L, "研究生学术写作工作坊", "course", 
            "面向研究生的学术写作实践课程", "https://workshop.university.edu/grad-writing", "advanced"));
        
        return resources;
    }
    
    /**
     * 创建资源对象
     */
    private AcademicResourceDTO createResource(Long id, String title, String type, 
                                             String description, String url, String category) {
        AcademicResourceDTO resource = new AcademicResourceDTO();
        resource.setResourceId(id);
        resource.setTitle(title);
        resource.setType(type);
        resource.setDescription(description);
        resource.setUrl(url);
        resource.setCategory(category);
        return resource;
    }
}