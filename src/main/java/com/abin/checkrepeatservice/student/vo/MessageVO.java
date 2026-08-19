package com.abin.checkrepeatservice.student.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 *消息VO
 */
@Data
@ApiModel("消息")
public class MessageVO {

    @ApiModelProperty("消息ID")
    private Long id;

    @ApiModelProperty("发送者ID")
    private Long senderId;

    @ApiModelProperty("发送者姓名")
    private String senderName;

    @ApiModelProperty("发送者角色")
    private String senderRole;
    
    @ApiModelProperty("发送者标识 (student/teacher/advisor)")
    private String sender;

    @ApiModelProperty("消息内容")
    private String content;

    @ApiModelProperty("发送时间")
    private String sendTime;

    @ApiModelProperty("消息状态")
    private String status;

    @ApiModelProperty("消息类型")
    private String messageType;

    @ApiModelProperty("附件列表")
    private List<MessageAttachmentVO> attachments;

    @ApiModelProperty("发送者头像")
    private String senderAvatar;

    @ApiModelProperty("格式化后的时间（相对时间）")
    private String formattedTime;

    /**
     * 获取格式化的时间显示
     */
    public String getFormattedTime() {
        if (sendTime == null || sendTime.isEmpty()) {
            return "";
        }
        
        try {
            LocalDateTime sentTime = LocalDateTime.parse(sendTime);
            LocalDateTime now = LocalDateTime.now();
            long minutesBetween = java.time.Duration.between(sentTime, now).toMinutes();
            
            if (minutesBetween < 1) {
                return "刚刚";
            } else if (minutesBetween < 60) {
                return minutesBetween + "分钟前";
            } else if (minutesBetween < 1440) { // 24 小时内
                long hours = minutesBetween / 60;
                return hours + "小时前";
            } else if (sentTime.toLocalDate().equals(now.toLocalDate())) {
                return "今天 " + sentTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            } else if (sentTime.toLocalDate().equals(now.minusDays(1).toLocalDate())) {
                return "昨天 " + sentTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            } else {
                return sentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
        } catch (Exception e) {
            return sendTime; // 如果解析失败，返回原始时间
        }
    }

    /**
     *消息附件VO
     */
    @Data
    @ApiModel("消息附件")
    public static class MessageAttachmentVO {
        @ApiModelProperty("附件ID")
        private String id;

        @ApiModelProperty("附件名称")
        private String name;

        @ApiModelProperty("附件大小")
        private Long size;

        @ApiModelProperty("附件类型")
        private String type;

        @ApiModelProperty("上传时间")
        private String uploadTime;
    }
}