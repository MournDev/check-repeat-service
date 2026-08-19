package com.abin.checkrepeatservice.student.mapper;

import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CheckTaskMapper extends BaseMapper<CheckTask> {

    CheckTask selectLatestByPaperId(Long id);
}
