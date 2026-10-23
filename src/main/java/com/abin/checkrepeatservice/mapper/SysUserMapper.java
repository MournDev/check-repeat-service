package com.abin.checkrepeatservice.mapper;

import com.abin.checkrepeatservice.common.datascope.DataScopeSqlFilter;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public  interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 把 research_direction 显式置 NULL（教师清空研究方向时用）。
     *
     * 这里**必须是 NULL，不能写成空串**，两个理由：
     *   1. updateById 是 NOT_NULL 策略，null 会被跳过 —— 不清就留着旧的中文名缓存；
     *   2. AutoAssignmentServiceImpl.isResearchInterestMatch 用的是
     *      paperTitle.contains(keyword.trim())，空串 split 出 [""]、而 contains("")
     *      恒为 true —— 一旦写成 ''，**所有**教师都会被判定研究兴趣命中、白拿权重分。
     */
    @Update("UPDATE sys_user SET research_direction = NULL, update_time = NOW() WHERE id = #{userId}")
    int clearResearchDirection(@Param("userId") Long userId);

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

    /**
     * S14：用户按类型聚合（单条 GROUP BY），替代仪表盘原先总数+三类共 4 次 selectCount。
     * user_type 为 NULL 的账号单独成组（其 cnt 仍计入总用户数，由调用方对所有行求和）。
     *
     * <p>数据范围：scope 为 null（ALL/超管）时不加条件，SQL 与接入范围前一致；
     * 学院/专业/教师/本人范围按对应列过滤。</p>
     */
    @Select("<script>" +
            "SELECT user_type AS userType, COUNT(*) AS cnt FROM sys_user WHERE is_deleted = 0 " +
            "<if test='scope != null'>" +
            "<choose>" +
            "<when test='scope.denyAll'> AND 1 = 0 </when>" +
            "<when test='scope.collegeId != null'> AND college_id = #{scope.collegeId} </when>" +
            "<when test='scope.majorId != null'> AND major_id = #{scope.majorId} </when>" +
            "<when test='scope.teacherId != null'>" +
            " AND id IN (SELECT student_id FROM paper_info WHERE is_deleted = 0 AND teacher_id = #{scope.teacherId})" +
            "</when>" +
            "<when test='scope.studentId != null'> AND id = #{scope.studentId} </when>" +
            "</choose>" +
            "</if>" +
            " GROUP BY user_type" +
            "</script>")
    List<Map<String, Object>> selectUserTypeStats(@Param("scope") DataScopeSqlFilter scope);
}
