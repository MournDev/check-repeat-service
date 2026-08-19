package com.abin.checkrepeatservice.teacher.mapper;

import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待审核论文Mapper接口
 */
@Mapper
public interface PendingReviewMapper extends BaseMapper<PaperInfo> {
    
}