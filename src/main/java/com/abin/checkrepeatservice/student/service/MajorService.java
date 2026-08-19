package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.pojo.entity.Major;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface MajorService extends IService<Major> {

    /**
     * 根据院系ID查询专业接口
     * @param collegeId 院系ID
     * @return 专业列表
     */
    List<Major> getMajorsByCollegeId(Long collegeId);
}
