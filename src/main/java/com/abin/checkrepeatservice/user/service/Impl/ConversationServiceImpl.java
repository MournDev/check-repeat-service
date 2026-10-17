package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.Conversation;
import com.abin.checkrepeatservice.pojo.entity.ConversationMember;
import com.abin.checkrepeatservice.user.mapper.ConversationMapper;
import com.abin.checkrepeatservice.user.mapper.ConversationMemberMapper;
import com.abin.checkrepeatservice.user.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话服务实现类
 */
@Service
@Slf4j
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    @Autowired
    private ConversationMemberMapper conversationMemberMapper;

    @Override
    public IPage<Conversation> getUserConversations(Long userId, Integer pageNum, Integer pageSize) {
        Page<Conversation> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getIsDeleted, 0)
                .orderByDesc(Conversation::getUpdateTime);

        // 只查询用户参与的会话
        // S11 安全加固：inSql 字符串拼接改为 apply {0} 参数绑定
        wrapper.apply("id IN (SELECT conversation_id FROM conversation_members WHERE user_id = {0} AND is_deleted = 0)", userId);

        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation createConversation(String name, String type, Long creatorId, List<Long> memberIds) {
        // 创建会话
        Conversation conversation = new Conversation();
        conversation.setName(name);
        conversation.setType(type);
        conversation.setCreatorId(creatorId);
        conversation.setLastMessageTime(LocalDateTime.now());
        UserBusinessInfoUtils.setAuditField(conversation, true);
        save(conversation);

        // 添加会话成员
        for (Long memberId : memberIds) {
            ConversationMember member = new ConversationMember();
            member.setConversationId(conversation.getId());
            member.setUserId(memberId);
            member.setJoinedAt(LocalDateTime.now());
            UserBusinessInfoUtils.setAuditField(member, true);
            conversationMemberMapper.insert(member);
        }

        log.info("创建会话成功 - 会话ID: {}, 创建者: {}, 成员数: {}",
                conversation.getId(), creatorId, memberIds.size());

        return conversation;
    }

    @Override
    public boolean updateConversation(Long conversationId, String name, String avatar) {
        Conversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "会话不存在");
        }

        if (name != null) {
            conversation.setName(name);
        }
        if (avatar != null) {
            conversation.setAvatar(avatar);
        }

        return updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean leaveConversation(Long conversationId, Long userId) {
        // 删除会话成员关系
        LambdaQueryWrapper<ConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getUserId, userId);

        boolean removed = conversationMemberMapper.delete(wrapper) > 0;

        if (removed) {
            log.info("用户退出会话 - 会话ID: {}, 用户ID: {}", conversationId, userId);
        }

        return removed;
    }

    @Override
    public IPage<ConversationMember> getConversationMembers(Long conversationId, Integer pageNum, Integer pageSize) {
        Page<ConversationMember> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<ConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMember::getConversationId, conversationId)
                .eq(ConversationMember::getIsDeleted, 0)
                .orderByAsc(ConversationMember::getJoinedAt);

        return conversationMemberMapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<Conversation> searchConversations(Long userId, String keyword, Integer pageNum, Integer pageSize) {
        Page<Conversation> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getIsDeleted, 0)
                .and(w -> w.like(Conversation::getName, keyword)
                        .or()
                        .like(Conversation::getDescription, keyword))
                .orderByDesc(Conversation::getUpdateTime);

        // 只搜索用户参与的会话
        // S11 安全加固：inSql 字符串拼接改为 apply {0} 参数绑定
        wrapper.apply("id IN (SELECT conversation_id FROM conversation_members WHERE user_id = {0} AND is_deleted = 0)", userId);

        return page(page, wrapper);
    }
}

