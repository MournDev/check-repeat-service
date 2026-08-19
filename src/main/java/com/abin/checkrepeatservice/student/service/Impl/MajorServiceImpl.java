package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.student.mapper.MajorMapper;
import com.abin.checkrepeatservice.student.service.MajorService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@Slf4j
public class MajorServiceImpl extends ServiceImpl<MajorMapper, Major> implements MajorService {

    @Override
    public List<Major> getMajorsByCollegeId(Long collegeId) {
        // 参数校验
        if (collegeId == null || collegeId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "学院ID参数不合法");
        }

        try {
            LambdaQueryWrapper<Major> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Major::getCollegeId, collegeId)
                    .eq(Major::getIsDeleted, 0)
                    .orderByAsc(Major::getMajorName);

            List<Major> list = list(queryWrapper);
            return list;
        } catch (Exception e) {
            log.error("专业列表查询失败，学院ID：{}", collegeId, e);
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "专业信息获取失败");
        }
    }
}
