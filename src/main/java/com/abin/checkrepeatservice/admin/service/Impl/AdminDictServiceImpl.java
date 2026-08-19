package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AdminDictService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.mapper.CollegeMapper;
import com.abin.checkrepeatservice.pojo.entity.College;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.MajorMapper;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员公共字典服务实现类
 */
@Slf4j
@Service
public class AdminDictServiceImpl implements AdminDictService {

    @Resource
    private SysUserService sysUserService;

    @Resource
    private CollegeMapper collegeMapper;

    @Resource
    private MajorMapper majorMapper;

    @Override
    public Result<List<Map<String, Object>>> getMajors() {
        // 从 major 表查询所有专业信息
        LambdaQueryWrapper<Major> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Major::getIsDeleted, 0)
               .orderByAsc(Major::getMajorName);
        
        List<Major> majorList = majorMapper.selectList(wrapper);
        
        List<Map<String, Object>> majors = majorList.stream()
                .map(major -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", major.getId());
                    map.put("label", major.getMajorName());
                    map.put("code", major.getMajorCode());
                    map.put("collegeId", major.getCollegeId());
                    return map;
                })
                .collect(Collectors.toList());
        
        log.debug("获取专业列表成功: count={}", majors.size());
        return Result.success("专业列表获取成功", majors);
    }

    @Override
    public Result<List<Map<String, Object>>> getGrades() {
        // 从用户表中提取所有年级信息
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysUser::getGrade)
               .isNotNull(SysUser::getGrade)
               .ne(SysUser::getGrade, "")
               .eq(SysUser::getIsDeleted, 0)
               .groupBy(SysUser::getGrade);
        
        List<SysUser> users = sysUserService.list(wrapper);
        
        List<Map<String, Object>> grades = users.stream()
                .map(user -> {
                    Map<String, Object> grade = new HashMap<>();
                    grade.put("value", user.getGrade());
                    grade.put("label", user.getGrade() + "级");
                    return grade;
                })
                .distinct()
                .sorted((a, b) -> {
                    String gradeA = (String) a.get("value");
                    String gradeB = (String) b.get("value");
                    return gradeB.compareTo(gradeA); // 按年级倒序排列
                })
                .collect(Collectors.toList());
        
        log.debug("获取年级列表成功: count={}", grades.size());
        return Result.success("年级列表获取成功", grades);
    }

    @Override
    public Result<List<Map<String, Object>>> getColleges() {
        // 从 college 表查询所有学院信息
        LambdaQueryWrapper<College> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(College::getIsDeleted, 0)
               .orderByAsc(College::getCollegeName);
        
        List<College> collegeList = collegeMapper.selectList(wrapper);
        
        List<Map<String, Object>> colleges = collegeList.stream()
                .map(college -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("value", college.getId());
                    map.put("label", college.getCollegeName());
                    map.put("code", college.getCollegeCode());
                    return map;
                })
                .collect(Collectors.toList());
        
        log.debug("获取学院列表成功: count={}", colleges.size());
        return Result.success("学院列表获取成功", colleges);
    }
    
    @Override
    public Result<Map<String, String>> getMajorNameMap() {
        try {
            // 从 major 表查询专业ID和名称的映射关系
            LambdaQueryWrapper<Major> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Major::getIsDeleted, 0);
            
            List<Major> majors = majorMapper.selectList(wrapper);
            
            Map<String, String> majorMap = majors.stream()
                .collect(Collectors.toMap(
                    major -> String.valueOf(major.getId()),
                    Major::getMajorName,
                    (existing, replacement) -> existing
                ));
            
            log.debug("获取专业名称映射成功: count={}", majorMap.size());
            return Result.success("专业名称映射获取成功", majorMap);
        } catch (Exception e) {
            log.error("获取专业名称映射失败: {}", e.getMessage(), e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "获取专业名称映射失败: " + e.getMessage());
        }
    }
}