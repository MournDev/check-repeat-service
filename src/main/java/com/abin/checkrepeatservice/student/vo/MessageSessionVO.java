package com.abin.checkrepeatservice.student.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 *消息会话VO
 */
@Data
@ApiModel("消息会话")
public class MessageSessionVO {

    // 会话 ID 是「两个用户 ID 排序后拼接再取哈希」，上限 2^31，在 JS 的安全整数范围内，
    // 因此不必按字符串下发（与下面的雪花 userId 不同）。
    @ApiModelProperty("会话ID")
    private Long id;

    @ApiModelProperty("会话名称")
    private String name;

    @ApiModelProperty("会话类型")
    private String type;

    @ApiModelProperty("参与用户列表")
    private List<SessionMemberVO> members;

    @ApiModelProperty("最后一条消息内容")
    private String lastMessage;

    @ApiModelProperty("最后消息时间")
    private String lastTime;

    @ApiModelProperty("未读消息数")
    private Integer unreadCount;

    @ApiModelProperty("会话头像")
    private String avatar;

    /**
     *会话成员VO
     */
    @Data
    @ApiModel("会话成员")
    public static class SessionMemberVO {
        // 雪花 ID，必须按字符串下发：前端会把它当作 receiverId 原样发回（发消息给谁），
        // 截断末位就等于发给另一个人。见 LoginVO.userId 的同款注释。
        @ApiModelProperty("用户ID")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long userId;

        @ApiModelProperty("用户姓名")
        private String userName;

        @ApiModelProperty("用户角色")
        private String userRole;

        @ApiModelProperty("用户头像")
        private String avatar;
    }
}