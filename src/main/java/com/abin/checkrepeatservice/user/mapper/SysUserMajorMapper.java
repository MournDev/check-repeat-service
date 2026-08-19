package com.abin.checkrepeatservice.user.mapper;


import com.abin.checkrepeatservice.pojo.entity.Major;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SysUserMajorMapper extends BaseMapper<Major> {

    /**
     * 1. 查询某教师有审核权限的所有专业ID（默认专业+未过期的临时协助专业）
     * @param userId 教师ID
     * @return 专业ID列表（如[1,3,5]）
     */
    List<Long> selectAuthorizedMajorIds(@Param("userId") Long userId);

    /**
     * 2. 查询某专业的所有审核教师（默认教师+有临时权限的教师）
     * @param majorId 专业ID
     * @return 用户ID列表（教师ID）
     */
    List<Long> selectAuditTeachersByMajorId(@Param("majorId") Long majorId);

    /**
     * 3. 给教师添加跨专业临时协助权限（批量或单条）
     * @param list SysUserMajor列表（含userId、majorId、tempAuditAuth等）
     * @return 成功添加的条数
     */
    int batchAddTempAuth(@Param("list") List<Major> list);

    /**
     * 4. 获取某学生可咨询的AdvisorID列表
     * @param studentMajorId 学生专业ID
     * @return 可咨询的AdvisorID列表
     */
    List<Long> selectAvailableAdvisorIds(Long studentMajorId);

}
