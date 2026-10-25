package com.abin.checkrepeatservice.student.mapper;

import com.abin.checkrepeatservice.admin.vo.CollegePaperStatsVO;
import com.abin.checkrepeatservice.admin.vo.MajorPaperStatsVO;
import com.abin.checkrepeatservice.admin.vo.OverduePaperVO;
import com.abin.checkrepeatservice.common.datascope.DataScopeSqlFilter;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.teacher.vo.CollegeDistributionVO;
import com.abin.checkrepeatservice.teacher.vo.SimilarityDistributionVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PaperInfoMapper extends BaseMapper<PaperInfo> {

    PaperInfo selectLatestPaper(Long studentId);

    /**
     * 对论文行加排他锁（SELECT ... FOR UPDATE），用于串行化同一论文的查重任务创建。
     * 必须在事务内调用；行锁会一直持有到事务提交/回滚。
     */
    @Select("SELECT id FROM paper_info WHERE id = #{paperId} AND is_deleted = 0 FOR UPDATE")
    Long lockPaperForCheck(@Param("paperId") Long paperId);

    /**
     * 把 subject_id 显式清空（置 NULL），仅用于「学科编码解析不出学科」时抹掉旧值。
     *
     * 为什么不能靠 updateById 顺带清：MyBatis-Plus 的更新策略是 NOT_NULL，null 字段会被
     * 直接跳过 —— 于是「先选了 ENG-CS，重提时改成查不到的编码」会留下
     * subject_code='查不到的编码' 而 subject_id 还指着「计算机科学与技术」的矛盾行，
     * 阈值解析会拿这个错位的 id 去匹配学科维度的规则。所以这里用一条定点 UPDATE 显式置 NULL。
     * （同类坑：本仓库全局靠 updateById，清空列一律要单独处理。）
     */
    @Update("UPDATE paper_info SET subject_id = NULL, update_time = NOW() WHERE id = #{paperId}")
    int clearSubjectId(@Param("paperId") Long paperId);

    /**
     * 获取相似度分布数据
     */
    List<SimilarityDistributionVO> selectSimilarityDistribution(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 获取学院分布数据
     */
    List<CollegeDistributionVO> selectCollegeDistribution(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 统计指定教师在时间范围内的论文总数
     */
    Integer countTotalPapers(
            @Param("teacherId") Long teacherId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    Integer countTeacherPapers(Long teacherId);
    
    /**
     * 获取学院论文分布统计（管理员用）
     *
     * @param scope 数据范围；null 表示全量（ALL）
     */
    List<CollegePaperStatsVO> selectCollegePaperStats(@Param("scope") DataScopeSqlFilter scope);
    
    /**
     * 获取专业论文分布统计（管理员用）
     *
     * @param scope 数据范围；null 表示全量（ALL）
     */
    List<MajorPaperStatsVO> selectMajorPaperStats(@Param("scope") DataScopeSqlFilter scope);

    /**
     * S14：论文按状态聚合（单条 GROUP BY），替代管理端论文统计/仪表盘原先十多次 selectCount。
     * <p>每个状态一行：cnt=该状态未删论文总数；todayCnt/weekCnt/monthCnt=对应时间点及以后新增数，
     * 时间入参传 null 时对应列返回 0（论文状态为 NULL 的存量行也会单独成组，计入总数）。</p>
     */
    @Select("""
            <script>
            SELECT paper_status AS status,
                   COUNT(*) AS cnt,
                   SUM(CASE WHEN #{todayStart} IS NOT NULL AND create_time >= #{todayStart}
                            THEN 1 ELSE 0 END) AS todayCnt,
                   SUM(CASE WHEN #{weekStart} IS NOT NULL AND create_time >= #{weekStart}
                            THEN 1 ELSE 0 END) AS weekCnt,
                   SUM(CASE WHEN #{monthStart} IS NOT NULL AND create_time >= #{monthStart}
                            THEN 1 ELSE 0 END) AS monthCnt
            FROM paper_info
            WHERE is_deleted = 0
            <if test="scope != null and scope.denyAll">
                AND 1 = 0
            </if>
            <if test="scope != null and scope.collegeId != null">
                AND college_id = #{scope.collegeId}
            </if>
            <if test="scope != null and scope.majorId != null">
                AND major_id = #{scope.majorId}
            </if>
            <if test="scope != null and scope.teacherId != null">
                AND teacher_id = #{scope.teacherId}
            </if>
            <if test="scope != null and scope.studentId != null">
                AND student_id = #{scope.studentId}
            </if>
            GROUP BY paper_status
            </script>
            """)
    List<Map<String, Object>> selectPaperStatusStats(@Param("todayStart") LocalDateTime todayStart,
                                                     @Param("weekStart") LocalDateTime weekStart,
                                                     @Param("monthStart") LocalDateTime monthStart,
                                                     @Param("scope") DataScopeSqlFilter scope);

    /**
     * Wave 3：管理端超期论文分页查询（超期口径 submit_time + slaHours）。
     */
    Page<OverduePaperVO> selectOverduePapers(Page<OverduePaperVO> page,
                                             @Param("slaHours") int slaHours,
                                             @Param("bucket") String bucket,
                                             @Param("keyword") String keyword);

    /**
     * Wave 3：超期论文全量分桶计数（不受 bucket/keyword 筛选影响）。
     */
    List<Map<String, Object>> countOverdueBuckets(@Param("slaHours") int slaHours);
}
