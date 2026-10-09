package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.StudentGroup;
import com.abin.checkrepeatservice.pojo.entity.StudentGroupMember;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.StudentGroupReq;
import com.abin.checkrepeatservice.teacher.mapper.StudentGroupMapper;
import com.abin.checkrepeatservice.teacher.mapper.StudentGroupMemberMapper;
import com.abin.checkrepeatservice.teacher.service.StudentGroupService;
import com.abin.checkrepeatservice.teacher.vo.GroupStudentVO;
import com.abin.checkrepeatservice.teacher.vo.StudentGroupVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StudentGroupServiceImpl implements StudentGroupService {

    @Resource
    private StudentGroupMapper studentGroupMapper;
    @Resource
    private StudentGroupMemberMapper studentGroupMemberMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Override
    public Result<List<StudentGroupVO>> listGroups() {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        List<StudentGroup> groups = studentGroupMapper.selectList(
                new LambdaQueryWrapper<StudentGroup>()
                        .eq(StudentGroup::getTeacherId, teacherId)
                        .orderByAsc(StudentGroup::getCreateTime));
        if (groups.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<Long> groupIds = groups.stream().map(StudentGroup::getId).collect(Collectors.toList());
        // 一次查出全部分组的成员，再按组聚合，避免逐组查库
        List<StudentGroupMember> members = studentGroupMemberMapper.selectList(
                new LambdaQueryWrapper<StudentGroupMember>().in(StudentGroupMember::getGroupId, groupIds));
        Map<Long, List<GroupStudentVO>> studentsByGroup = assembleGroupStudents(members);

        List<StudentGroupVO> result = groups.stream().map(group -> {
            List<GroupStudentVO> students = studentsByGroup.getOrDefault(group.getId(), Collections.emptyList());
            return toVO(group, students);
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @Override
    public Result<StudentGroupVO> createGroup(StudentGroupReq req) {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        StudentGroup group = new StudentGroup();
        group.setTeacherId(teacherId);
        group.setName(req.getName());
        group.setDescription(req.getDescription());
        studentGroupMapper.insert(group);
        log.info("创建学生分组成功：teacherId={}, groupId={}, name={}", teacherId, group.getId(), group.getName());
        // 新建分组必然无成员，直接给空列表，前端会把返回整体放进分组卡片
        return Result.success("创建分组成功", toVO(group, Collections.emptyList()));
    }

    @Override
    public Result<StudentGroupVO> updateGroup(Long groupId, StudentGroupReq req) {
        StudentGroup group = checkGroupOwnership(groupId);
        group.setName(req.getName());
        group.setDescription(req.getDescription());
        studentGroupMapper.updateById(group);
        log.info("更新学生分组成功：groupId={}, name={}", groupId, group.getName());

        List<StudentGroupMember> members = studentGroupMemberMapper.selectList(
                new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId, groupId));
        List<GroupStudentVO> students = assembleGroupStudents(members)
                .getOrDefault(groupId, Collections.emptyList());
        return Result.success("更新分组成功", toVO(group, students));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteGroup(Long groupId) {
        checkGroupOwnership(groupId);
        // 成员关系物理删除；分组本身软删
        studentGroupMemberMapper.delete(
                new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId, groupId));
        studentGroupMapper.deleteById(groupId);
        log.info("删除学生分组成功：groupId={}", groupId);
        return Result.success("删除分组成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> addStudents(Long groupId, List<Long> studentIds) {
        checkGroupOwnership(groupId);
        if (CollectionUtils.isEmpty(studentIds)) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "请选择要添加的学生");
        }
        // 过滤掉已在组内的，避免撞 (group_id, student_id) 唯一键
        Set<Long> existingIds = studentGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<StudentGroupMember>()
                                .eq(StudentGroupMember::getGroupId, groupId)
                                .in(StudentGroupMember::getStudentId, studentIds))
                .stream().map(StudentGroupMember::getStudentId).collect(Collectors.toSet());

        List<Long> toAdd = studentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toList());
        for (Long studentId : toAdd) {
            StudentGroupMember member = new StudentGroupMember();
            member.setGroupId(groupId);
            member.setStudentId(studentId);
            studentGroupMemberMapper.insert(member);
        }
        log.info("分组添加学生成功：groupId={}, 新增{}人，跳过已在组内{}人", groupId, toAdd.size(), existingIds.size());
        return Result.success("添加学生成功");
    }

    @Override
    public Result<String> removeStudent(Long groupId, Long studentId) {
        checkGroupOwnership(groupId);
        int rows = studentGroupMemberMapper.delete(
                new LambdaQueryWrapper<StudentGroupMember>()
                        .eq(StudentGroupMember::getGroupId, groupId)
                        .eq(StudentGroupMember::getStudentId, studentId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "该学生不在此分组中");
        }
        log.info("分组移除学生成功：groupId={}, studentId={}", groupId, studentId);
        return Result.success("移除学生成功");
    }

    @Override
    public Result<List<GroupStudentVO>> listStudentsNotInGroup(Long groupId) {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        checkGroupOwnership(groupId);

        // 教师名下学生 = 该教师指导的论文的去重学生集合（与教师端学生管理同一口径）
        List<PaperInfo> papers = paperInfoMapper.selectList(
                new QueryWrapper<PaperInfo>()
                        .select("DISTINCT student_id")
                        .eq("teacher_id", teacherId));
        List<Long> teacherStudentIds = papers.stream()
                .map(PaperInfo::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (teacherStudentIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Set<Long> inGroupIds = studentGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<StudentGroupMember>().eq(StudentGroupMember::getGroupId, groupId))
                .stream().map(StudentGroupMember::getStudentId).collect(Collectors.toSet());

        List<Long> candidateIds = teacherStudentIds.stream()
                .filter(id -> !inGroupIds.contains(id))
                .collect(Collectors.toList());
        if (candidateIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<GroupStudentVO> result = sysUserMapper.selectBatchIds(candidateIds).stream()
                .map(this::toGroupStudentVO)
                .sorted(Comparator.comparing(GroupStudentVO::getStudentId, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 校验分组存在且属于当前教师
     */
    private StudentGroup checkGroupOwnership(Long groupId) {
        StudentGroup group = studentGroupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "分组不存在");
        }
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        if (!teacherId.equals(group.getTeacherId())) {
            throw new BusinessException(ResultCode.PERMISSION_TEACHER_OWNER);
        }
        return group;
    }

    /**
     * 把成员记录批量关联 sys_user，按 groupId 聚合成学生视图
     */
    private Map<Long, List<GroupStudentVO>> assembleGroupStudents(List<StudentGroupMember> members) {
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyMap();
        }
        List<Long> studentIds = members.stream()
                .map(StudentGroupMember::getStudentId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        return members.stream()
                .filter(m -> userMap.containsKey(m.getStudentId()))
                .collect(Collectors.groupingBy(
                        StudentGroupMember::getGroupId,
                        Collectors.mapping(m -> toGroupStudentVO(userMap.get(m.getStudentId())), Collectors.toList())));
    }

    private GroupStudentVO toGroupStudentVO(SysUser user) {
        return new GroupStudentVO(user.getId(), user.getRealName(), user.getUsername(),
                user.getCollegeName(), user.getMajor());
    }

    private StudentGroupVO toVO(StudentGroup group, List<GroupStudentVO> students) {
        StudentGroupVO vo = new StudentGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setCreateTime(group.getCreateTime());
        vo.setStudents(new ArrayList<>(students));
        vo.setStudentCount(students.size());
        return vo;
    }
}
