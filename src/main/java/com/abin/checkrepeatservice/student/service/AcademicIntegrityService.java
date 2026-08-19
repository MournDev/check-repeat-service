package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.student.dto.AcademicResourceDTO;
import com.abin.checkrepeatservice.student.dto.ChecklistItemDTO;
import com.abin.checkrepeatservice.student.dto.PersonalAdviceDTO;

import java.util.List;

/**
 * 学术诚信服务接口
 */
public interface AcademicIntegrityService {
    
    /**
     * 获取个性化学术建议
     * @param studentId 学生ID
     * @return 个性化学术建议
     */
    PersonalAdviceDTO getPersonalAdvice(Long studentId);
    
    /**
     * 获取推荐学习资源
     * @param studentId 学生ID
     * @param resourceType 资源类型（可选）
     * @return 学习资源列表
     */
    List<AcademicResourceDTO> getRecommendedResources(Long studentId, String resourceType);
    
    /**
     * 获取用户检查清单状态
     * @param studentId 学生ID
     * @return 检查清单项列表
     */
    List<ChecklistItemDTO> getChecklist(Long studentId);
    
    /**
     * 更新检查项状态
     * @param studentId 学生ID
     * @param itemId 检查项ID
     * @param checked 是否已检查
     * @return 更新结果
     */
    boolean updateChecklistItem(Long studentId, Long itemId, Boolean checked);
    
    /**
     * 初始化用户检查清单
     * @param studentId 学生ID
     */
    void initializeChecklist(Long studentId);
}