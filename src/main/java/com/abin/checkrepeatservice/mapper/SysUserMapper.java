package com.abin.checkrepeatservice.mapper;

import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public  interface SysUserMapper extends BaseMapper<SysUser> {
    /**
     * 按专业或教师ID筛选教师列表
     * @param majorId 专业ID（可选，null则不按专业筛选）
     * @param teacherId 教师ID（可选，null则不按教师ID筛选；非null时优先筛选该教师）
     * @return 符合条件的教师列表（含姓名、ID等核心信息）
     */
    List<SysUser> selectTeacherList(
            @Param("majorId") Long majorId,  // 专业ID（用于筛选“负责该专业的教师”）
            @Param("teacherId") Long teacherId  // 教师ID（用于单独查询某教师）
    );
    /**
     * 按专业和年级筛选学生总数（需提交论文的学生）
     * @param majorId 专业ID（可选：null 则不按专业筛选）
     * @param grade 年级（可选：null 则不按年级筛选，如2021、2022）
     * @return 符合条件的学生总数（仅统计未删除的有效学生）
     */
    int countStudentByCondition(
            @Param("majorId") Long majorId,  // 专业ID（如“计算机科学与技术”的ID=3）
            @Param("grade") Integer grade    // 年级（如2021级，存储为整数2021）
    );
    /**
     * 按邮箱查询用户
     * @param email 邮箱
     * @return 邮箱对应的用户
     */
    SysUser selectByEmail(String email);
}
