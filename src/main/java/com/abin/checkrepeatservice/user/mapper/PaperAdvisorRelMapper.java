package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.PaperAdvisorRel;
import com.abin.checkrepeatservice.user.vo.PaperAdvisorRoundVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaperAdvisorRelMapper extends BaseMapper<PaperAdvisorRel> {


    List<PaperAdvisorRoundVO> selectRoundHistoryWithDetail(@Param("paperId") Long paperId);
}
