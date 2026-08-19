package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.StudentListDTO;
import com.abin.checkrepeatservice.teacher.service.TeacherStudentListService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TeacherStudentListServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements TeacherStudentListService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PaperInfoMapper paperInfoMapper;

    @Override
    public PageResultVO<StudentListDTO> getStudentsByTeacherId(Long teacherId, Integer current, Integer pageSize) {
        // 直接从PaperInfo表查询，因为teacher_id在该表中
        QueryWrapper<PaperInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("teacher_id", teacherId);

        // 创建分页对象
        Page<PaperInfo> page = new Page<>(current, pageSize);

        // 执行分页查询 - 获取论文信息
        Page<PaperInfo> paperPage = paperInfoMapper.selectPage(page, queryWrapper);

        // 转换为DTO列表，整合论文信息和学生信息
        List<StudentListDTO> dtoList = paperPage.getRecords().stream()
                .map(this::convertPaperToDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 封装分页结果
        PageResultVO<StudentListDTO> result = new PageResultVO<>();
        result.setList(dtoList);
        result.setTotalCount((int) paperPage.getTotal());
        result.setPageSize((int) paperPage.getSize());
        result.setPageNum((int) paperPage.getCurrent());

        return result;
    }

    @Override
    @Transactional
    public boolean deleteStudent(Long studentId) {
        // 检查用户是否存在（未被软删除）
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("id", studentId)
                .eq("is_deleted", 0);
        SysUser existingUser = sysUserMapper.selectOne(wrapper);

        if (existingUser == null) {
            return false;
        }
        // 直接调用删除方法，MyBatis-Plus 会自动处理软删除
        return this.removeById(studentId);
    }

    private StudentListDTO convertPaperToDto(PaperInfo paperInfo) {
        // 通过paperInfo中的student_id查询学生信息
        SysUser student = sysUserMapper.selectById(paperInfo.getStudentId());
        if (student == null) {
            return null;
        }
        StudentListDTO dto = new StudentListDTO();

        // 设置学生基础信息
        dto.setStudentId(student.getId());
        dto.setUsername(student.getUsername());
        dto.setStudentName(student.getRealName());
        dto.setCollegeName(student.getCollegeName());
        dto.setMajor(student.getMajor());
        dto.setGrade(student.getGrade());
        dto.setClassName(student.getClassName());
        dto.setPhone(student.getPhone());
        dto.setEmail(student.getEmail());
        
        // 设置论文信息
        dto.setPaperId(paperInfo.getId());
        dto.setPaperTitle(paperInfo.getPaperTitle());
        dto.setPaperStatus(paperInfo.getPaperStatus());
        dto.setSubmitTime(paperInfo.getSubmitTime());
        dto.setSimilarity(paperInfo.getSimilarityRate());
        dto.setAdvisorName(paperInfo.getTeacherName());
        dto.setPaperType(paperInfo.getPaperType());
        dto.setWordCount(paperInfo.getWordCount());
        dto.setFileId(paperInfo.getFileId());

        return dto;
    }
}
