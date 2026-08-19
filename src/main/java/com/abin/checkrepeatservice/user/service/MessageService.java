package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.user.dto.MessageSendDTO;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 消息服务接口
 * 统一处理系统消息和用户消息
 */
public interface MessageService {

    /**
     * 获取消息列表（通用查询接口）
     * @param userId 用户ID
     * @param messageType 消息类型
     * @param isRead 是否已读
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param sortBy 排序字段
     * @param sortOrder 排序方式
     * @return 消息分页列表
     */
    Result<PageResultVO<SystemMessage>> getMessageList(Long userId, String messageType,
                                                       Integer isRead, Integer pageNum, Integer pageSize,
                                                       String sortBy, String sortOrder);

    /**
     * 发送站内信
     * @param message 系统消息对象
     * @return 发送结果
     */
    Result<Boolean> sendMessage(SystemMessage message);

    /**
     * 发送业务通知
     * @param messageType 消息类型
     * @param receiverId 接收者ID
     * @param relatedId 关联ID
     * @param relatedType 关联类型
     * @param title 消息标题
     * @param content 消息内容
     * @return 发送结果
     */
    Result<Boolean> sendBusinessMessage(String messageType, Long receiverId,
                                        Long relatedId, String relatedType,
                                        String title, String content);

    /**
     * 使用模板发送消息
     * @param templateCode 模板编码
     * @param receiverId 接收者ID
     * @param relatedId 关联ID
     * @param relatedType 关联类型
     * @param params 模板参数
     * @return 发送结果
     */
    Result<Boolean> sendMessageByTemplate(String templateCode, Long receiverId,
                                          Long relatedId, String relatedType,
                                          Map<String, Object> params);

    /**
     * 获取用户消息列表
     * @param userId 用户ID
     * @param messageType 消息类型
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 消息分页列表
     */
    Result<Page<SystemMessage>> getUserMessages(Long userId, String messageType,
                                                Integer pageNum, Integer pageSize);

    /**
     * 获取用户未读消息
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 未读消息分页列表
     */
    Result<Page<SystemMessage>> getUnreadMessages(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 根据关联业务查询消息
     * @param userId 用户ID
     * @param relatedType 关联类型
     * @param relatedId 关联ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 关联消息分页列表
     */
    Result<Page<SystemMessage>> getMessagesByRelated(Long userId, String relatedType,
                                                     Long relatedId, Integer pageNum, Integer pageSize);

    /**
     * 标记消息为已读
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 操作结果
     */
    Result<Boolean> markAsRead(Long messageId, Long userId);

    /**
     * 批量标记为已读
     * @param messageIds 消息ID列表
     * @param userId 用户ID
     * @return 操作结果
     */
    Result<Boolean> batchMarkAsRead(List<Long> messageIds, Long userId);

    /**
     * 删除消息
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 操作结果
     */
    Result<Boolean> deleteMessage(Long messageId, Long userId);

    /**
     * 批量删除消息
     * @param messageIds 消息ID列表
     * @param userId 用户ID
     * @return 操作结果
     */
    Result<Boolean> deleteAllMessages(List<Long> messageIds, Long userId);

    /**
     * 清理过期消息
     * @return 清理的数量
     */
    Result<Integer> cleanExpiredMessages();

    /**
     * 获取消息详情
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 消息详情
     */
    Result<SystemMessage> getMessageDetail(Long messageId, Long userId);

    /**
     * 获取未读消息数量
     * @param userId 用户ID
     * @return 未读消息数量
     */
    Result<Integer> getUnreadCount(Long userId);
}