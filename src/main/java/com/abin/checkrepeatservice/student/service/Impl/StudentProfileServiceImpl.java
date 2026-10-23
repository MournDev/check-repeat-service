package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.student.dto.StudentProfileDTO;
import com.abin.checkrepeatservice.student.dto.UpdateProfileReq;
import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.StudentProfileService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.mapper.SystemMessageMapper;
import com.abin.checkrepeatservice.user.mapper.InstantMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 学生个人信息管理服务实现类
 */
@Slf4j
@Service
public class StudentProfileServiceImpl implements StudentProfileService {

    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private FileInfoMapper fileInfoMapper;
    
    @Resource
    private MessageService messageService;

    @Resource
    private SystemMessageMapper systemMessageMapper;

    @Resource
    private InstantMessageMapper instantMessageMapper;

    @Override
    public Result<StudentProfileDTO> getStudentProfile(Long studentId) {
        try {
            SysUser student = sysUserMapper.selectById(studentId);
            if (student == null || student.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "学生不存在");
            }

            StudentProfileDTO profile = new StudentProfileDTO();
            profile.setId(student.getId());
            profile.setStudentNo(student.getUsername());
            profile.setName(student.getRealName());
            profile.setEmail(student.getEmail());
            profile.setPhone(student.getPhone());
            profile.setCollege(student.getCollegeName());
            profile.setMajor(student.getMajor());
            profile.setGrade(student.getGrade());
            profile.setClassName(student.getClassName());
            profile.setAvatar(student.getAvatar());
            profile.setResearchInterest(student.getResearchDirection());

            // 获取指导关系
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .isNotNull(PaperInfo::getTeacherId)
                       .eq(PaperInfo::getIsDeleted, 0)
                       .orderByDesc(PaperInfo::getSubmitTime)
                       .last("LIMIT 1");
            
            PaperInfo latestPaper = paperInfoMapper.selectOne(paperWrapper);
            if (latestPaper != null && latestPaper.getTeacherId() != null) {
                profile.setAdvisorId(latestPaper.getTeacherId());
                
                // 获取导师信息
                StudentProfileDTO.AdvisorInfo advisorInfo = getAdvisorInfoFromPaper(latestPaper);
                profile.setAdvisorInfo(advisorInfo);
            }
            
            // 获取学术统计
            StudentProfileDTO.AcademicStats academicStats = getAcademicStats(studentId);
            profile.setAcademicStats(academicStats);

            log.info("获取学生个人资料成功: studentId={}", studentId);
            return Result.success("获取个人资料成功", profile);
        } catch (Exception e) {
            log.error("获取学生个人资料失败: studentId={}", studentId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取个人资料失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateProfile(Long studentId, UpdateProfileReq updateReq) {
        try {
            SysUser student = sysUserMapper.selectById(studentId);
            if (student == null || student.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "学生不存在");
            }

            // 参数验证
            if (updateReq.getEmail() != null && !isValidEmail(updateReq.getEmail())) {
                return Result.error(ResultCode.PARAM_ERROR, "邮箱格式不正确");
            }
            
            if (updateReq.getPhone() != null && !isValidPhone(updateReq.getPhone())) {
                return Result.error(ResultCode.PARAM_ERROR, "手机号格式不正确");
            }

            // 更新允许修改的字段
            if (updateReq.getPhone() != null) {
                student.setPhone(updateReq.getPhone());
            }
            if (updateReq.getEmail() != null) {
                student.setEmail(updateReq.getEmail());
            }
            if (updateReq.getAvatar() != null) {
                student.setAvatar(updateReq.getAvatar());
            }
            if (updateReq.getResearchInterest() != null) {
                student.setResearchDirection(updateReq.getResearchInterest());
            }
            if (updateReq.getIntroduce() != null) {
                student.setIntroduce(updateReq.getIntroduce());
            }
            if (updateReq.getGrade() != null) {
                student.setGrade(updateReq.getGrade());
            }
            if (updateReq.getMajor() != null) {
                student.setMajor(updateReq.getMajor());
            }
            if (updateReq.getClassName() != null) {
                student.setClassName(updateReq.getClassName());
            }
            
            student.setUpdateTime(LocalDateTime.now());
            
            sysUserMapper.updateById(student);

            log.info("更新学生个人资料成功: studentId={}", studentId);
            return Result.success("个人资料更新成功");
        } catch (Exception e) {
            log.error("更新学生个人资料失败: studentId={}", studentId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "个人资料更新失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<StudentProfileDTO.AdvisorInfo> getAdvisorInfo(Long studentId) {
        try {
            // 获取最新的指导关系
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .isNotNull(PaperInfo::getTeacherId)
                       .eq(PaperInfo::getIsDeleted, 0)
                       .orderByDesc(PaperInfo::getSubmitTime)
                       .last("LIMIT 1");
            
            PaperInfo latestPaper = paperInfoMapper.selectOne(paperWrapper);
            if (latestPaper == null || latestPaper.getTeacherId() == null) {
                return Result.error(ResultCode.PARAM_ERROR, "暂无指导教师");
            }

            StudentProfileDTO.AdvisorInfo advisorInfo = getAdvisorInfoFromPaper(latestPaper);

            log.info("获取导师信息成功: studentId={}, advisorId={}", studentId, advisorInfo.getId());
            return Result.success("获取导师信息成功", advisorInfo);
        } catch (Exception e) {
            log.error("获取导师信息失败: studentId={}", studentId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取导师信息失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> sendMessageToAdvisor(Long studentId, Long advisorId, String messageContent) {
        try {
            // 验证学生和导师关系
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .eq(PaperInfo::getTeacherId, advisorId)
                       .eq(PaperInfo::getIsDeleted, 0);
            
            long relationCount = paperInfoMapper.selectCount(paperWrapper);
            if (relationCount == 0) {
                return Result.error(ResultCode.PARAM_ERROR, "您与该导师无指导关系，无法发送消息");
            }

            // 构建消息对象
            SystemMessage message = new SystemMessage();
            // 使用当前登录用户ID
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            message.setSenderId(currentUserId);
            message.setReceiverId(advisorId);
            message.setMessageType("PRIVATE");
            message.setContentType("TEXT");
            message.setTitle("学生咨询消息");
            message.setContent(messageContent);
            message.setRelatedType("consultation");
            message.setCreateTime(LocalDateTime.now());

            // 发送消息
            Result<Boolean> sendResult = messageService.sendMessage(message);
            if (sendResult.isSuccess() && Boolean.TRUE.equals(sendResult.getData())) {
                log.info("学生向导师发送消息成功: studentId={}, advisorId={}", studentId, advisorId);
                return Result.success("消息发送成功");
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "消息发送失败: " + sendResult.getMessage());
            }
        } catch (Exception e) {
            log.error("发送消息给导师失败: studentId={}, advisorId={}", studentId, advisorId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "消息发送失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Object> getCommunicationHistory(Long studentId, Long advisorId, Integer pageNum, Integer pageSize) {
        try {
            // 验证指导关系
            LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
            paperWrapper.eq(PaperInfo::getStudentId, studentId)
                       .eq(PaperInfo::getTeacherId, advisorId)
                       .eq(PaperInfo::getIsDeleted, 0);
            
            long relationCount = paperInfoMapper.selectCount(paperWrapper);
            if (relationCount == 0) {
                return Result.error(ResultCode.PARAM_ERROR, "您与该导师无指导关系");
            }

            // 分页参数兜底
            int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
            int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

            // 真实沟通记录：合并「私信 system_message」与「即时消息 instant_message」两个通道
            List<Map<String, Object>> combined = new ArrayList<>();

            List<SystemMessage> privateMessages = systemMessageMapper.selectList(
                    new LambdaQueryWrapper<SystemMessage>()
                            .eq(SystemMessage::getIsDeleted, 0)
                            .and(w -> w.and(a -> a.eq(SystemMessage::getSenderId, studentId)
                                                     .eq(SystemMessage::getReceiverId, advisorId))
                                        .or(b -> b.eq(SystemMessage::getSenderId, advisorId)
                                                     .eq(SystemMessage::getReceiverId, studentId)))
                            .orderByDesc(SystemMessage::getCreateTime)
                            .last("LIMIT 200"));
            for (SystemMessage message : privateMessages) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", message.getId() == null ? null : "sys_" + message.getId());
                item.put("senderId", message.getSenderId() == null ? null : message.getSenderId().toString());
                item.put("receiverId", message.getReceiverId() == null ? null : message.getReceiverId().toString());
                item.put("direction", Objects.equals(message.getSenderId(), studentId) ? "student" : "advisor");
                item.put("title", message.getTitle());
                item.put("content", message.getContent());
                item.put("messageType", message.getMessageType());
                item.put("isRead", message.getIsRead());
                item.put("createTime", message.getCreateTime());
                combined.add(item);
            }

            List<InstantMessage> instantMessages = instantMessageMapper.selectList(
                    new LambdaQueryWrapper<InstantMessage>()
                            .eq(InstantMessage::getIsDeleted, 0)
                            .and(w -> w.and(a -> a.eq(InstantMessage::getSenderId, studentId)
                                                     .eq(InstantMessage::getReceiverId, advisorId))
                                        .or(b -> b.eq(InstantMessage::getSenderId, advisorId)
                                                     .eq(InstantMessage::getReceiverId, studentId)))
                            .orderByDesc(InstantMessage::getSentTime)
                            .last("LIMIT 200"));
            for (InstantMessage message : instantMessages) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", message.getId() == null ? null : "im_" + message.getId());
                item.put("senderId", message.getSenderId() == null ? null : message.getSenderId().toString());
                item.put("receiverId", message.getReceiverId() == null ? null : message.getReceiverId().toString());
                item.put("direction", Objects.equals(message.getSenderId(), studentId) ? "student" : "advisor");
                item.put("title", "即时消息");
                item.put("content", message.getContent());
                item.put("messageType", message.getMessageType());
                item.put("isRead", null);
                item.put("createTime", message.getSentTime());
                combined.add(item);
            }

            // 按时间倒序合并后内存分页
            combined.sort(Comparator.comparing(
                    (Map<String, Object> item) -> (LocalDateTime) item.get("createTime"),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            int from = (current - 1) * size;
            List<Map<String, Object>> history = from >= combined.size()
                    ? new ArrayList<>()
                    : new ArrayList<>(combined.subList(from, Math.min(from + size, combined.size())));
            
            log.info("获取师生沟通记录成功: studentId={}, advisorId={}, count={}",
                    studentId, advisorId, history.size());
            return Result.success("获取沟通记录成功", history);
        } catch (Exception e) {
            log.error("获取沟通记录失败: studentId={}, advisorId={}", studentId, advisorId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取沟通记录失败: " + "系统异常，请稍后重试");
        }
    }
    
    /**
     * 从论文信息中获取导师信息
     */
    private StudentProfileDTO.AdvisorInfo getAdvisorInfoFromPaper(PaperInfo paper) {
        SysUser advisor = sysUserMapper.selectById(paper.getTeacherId());
        if (advisor == null || advisor.getIsDeleted() == 1) {
            return null;
        }

        StudentProfileDTO.AdvisorInfo advisorInfo = new StudentProfileDTO.AdvisorInfo();
        advisorInfo.setId(advisor.getId());
        advisorInfo.setName(advisor.getRealName());
        advisorInfo.setTitle(advisor.getProfessionalTitle() != null ? advisor.getProfessionalTitle() : "教师");
        advisorInfo.setPhone(advisor.getPhone());
        advisorInfo.setEmail(advisor.getEmail());
        advisorInfo.setOffice(advisor.getOffice() != null ? advisor.getOffice() : "未设置");
        advisorInfo.setAvatar(advisor.getAvatar());
        advisorInfo.setResearchField(advisor.getResearchDirection());
        
        // 添加专长领域（假设用逗号分隔）
        if (advisor.getResearchDirection() != null) {
            String[] fields = advisor.getResearchDirection().split(",");
            List<String> expertiseList = new ArrayList<>();
            for (String field : fields) {
                if (!field.trim().isEmpty()) {
                    expertiseList.add(field.trim());
                }
            }
            advisorInfo.setExpertise(expertiseList);
        }
        
        return advisorInfo;
    }
    
    /**
     * 获取学术统计信息
     */
    private StudentProfileDTO.AcademicStats getAcademicStats(Long studentId) {
        StudentProfileDTO.AcademicStats stats = new StudentProfileDTO.AcademicStats();
        
        // 获取所有论文
        LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
        paperWrapper.eq(PaperInfo::getStudentId, studentId)
                   .eq(PaperInfo::getIsDeleted, 0);
        
        List<PaperInfo> papers = paperInfoMapper.selectList(paperWrapper);
        
        if (papers == null || papers.isEmpty()) {
            stats.setTotalPapers(0);
            stats.setCompletedPapers(0);
            stats.setAvgSimilarity(BigDecimal.ZERO);
            stats.setHighestScore(BigDecimal.ZERO);
            stats.setTotalWords(0);
            return stats;
        }
        
        // 统计论文数量
        stats.setTotalPapers(papers.size());
        
        // 统计已完成论文数
        long completedCount = papers.stream()
                .filter(paper -> "completed".equals(paper.getPaperStatus()))
                .count();
        stats.setCompletedPapers((int) completedCount);
        
        // 计算平均相似度
        BigDecimal totalSimilarity = papers.stream()
                .filter(paper -> paper.getSimilarityRate() != null)
                .map(PaperInfo::getSimilarityRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long validSimilarityCount = papers.stream()
                .map(PaperInfo::getSimilarityRate)
                .filter(rate -> rate != null)
                .count();
        
        if (validSimilarityCount > 0) {
            stats.setAvgSimilarity(totalSimilarity.divide(
                    new BigDecimal(validSimilarityCount), 2, BigDecimal.ROUND_HALF_UP));
        } else {
            stats.setAvgSimilarity(BigDecimal.ZERO);
        }
        
        // 获取最高成绩
        BigDecimal highestScore = papers.stream()
                .filter(paper -> paper.getFinalScore() != null)
                .map(PaperInfo::getFinalScore)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        stats.setHighestScore(highestScore);
        
        // 计算累计字数
        int totalWords = papers.stream()
                .mapToInt(paper -> {
                    if (paper.getFileId() != null) {
                        com.abin.checkrepeatservice.pojo.entity.FileInfo fileInfo = 
                                fileInfoMapper.selectById(paper.getFileId());
                        return fileInfo != null ? fileInfo.getWordCount() : 0;
                    }
                    return 0;
                })
                .sum();
        stats.setTotalWords(totalWords);
        
        return stats;
    }
    
    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    
    /**
     * 验证手机号格式
     */
    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        return phone.matches("^1[3-9]\\d{9}$");
    }
}