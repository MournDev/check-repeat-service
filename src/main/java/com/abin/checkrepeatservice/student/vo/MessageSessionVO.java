package com.abin.checkrepeatservice.student.vo;

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
        @ApiModelProperty("用户ID")
        private Long userId;

        @ApiModelProperty("用户姓名")
        private String userName;

        @ApiModelProperty("用户角色")
        private String userRole;

        @ApiModelProperty("用户头像")
        private String avatar;
    }
}