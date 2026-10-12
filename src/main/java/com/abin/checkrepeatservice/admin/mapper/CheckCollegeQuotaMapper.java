package com.abin.checkrepeatservice.admin.mapper;

import com.abin.checkrepeatservice.pojo.entity.CheckCollegeQuota;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CheckCollegeQuotaMapper extends BaseMapper<CheckCollegeQuota> {

    /**
     * 原子扣减额度：仅当 used_quota < total_quota 时 +1，依赖 InnoDB 行锁保证并发安全。
     * 注解 SQL 不走 @TableLogic，需手动带 is_deleted = 0；
     * update_time 不走 FieldFill，需手动 NOW()。
     * 返回影响行数：1=扣减成功，0=已超限或记录已删（由调用方重查判定）。
     */
    @Update("UPDATE check_college_quota SET used_quota = used_quota + 1, update_time = NOW() " +
            "WHERE id = #{id} AND is_deleted = 0 AND used_quota < total_quota")
    int incrementUsedQuota(@Param("id") Long id);
}
