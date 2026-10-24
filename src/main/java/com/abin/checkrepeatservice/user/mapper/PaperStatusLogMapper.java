package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.PaperStatusLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface PaperStatusLogMapper extends BaseMapper<PaperStatusLog> {

    /**
     * Wave 3 生命周期漏斗：统计学期内提交、且到达过指定状态的论文数（去重）。
     */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT l.paper_id)
            FROM paper_status_log l
            JOIN paper_info p ON p.id = l.paper_id AND p.is_deleted = 0
            WHERE l.new_status = #{status}
            <if test="start != null">
                AND p.submit_time &gt;= #{start}
            </if>
            <if test="end != null">
                AND p.submit_time &lt; #{end}
            </if>
            </script>
            """)
    Long countPapersReachedStatus(@Param("status") String status,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);
}

