package com.abin.checkrepeatservice.student.vo;

import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 *消息VO
 */
@Data
@ApiModel("消息")
public class MessageVO {

    // id / senderId 都是雪花 ID（19 位），必须以**字符串**下发。
    // 以数字下发时 JS 会截掉末位，而前端判断"这条是不是我发的"用的正是
    //   String(message.senderId) === String(userStore.userInfo?.userId)
    // （userId 来自 LoginVO，本来就带 @JsonFormat(STRING)）——
    // 一边字符串一边被截断的数字，比较恒不成立，自己的消息会全部渲染成对方发的。
    // 见 LoginVO.userId 上的同款注释。
    @ApiModelProperty("消息ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @ApiModelProperty("发送者ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
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

    // ---------------------------------------------------------------- 附件读写
    //
    // 附件的"形状"以前在师生两侧各写了一份：写入端各自拼 Map（键名靠自觉一致），
    // 读取端各自 new ObjectMapper 反序列化。这正是本项目反复中招的"两套约定"，
    // 所以收口到这里——写入只有 toAttachmentsJson，读取只有 parseAttachments，
    // 键名由下面的字段名决定，没有第二处可以漂。

    private static final ObjectMapper ATTACHMENT_MAPPER = new ObjectMapper();

    /** 构造单个附件。size 为 null 时按 0 处理（读取端会直接当数字用）。 */
    public static MessageAttachmentVO attachment(String id, String name, Long size, String type) {
        MessageAttachmentVO attachment = new MessageAttachmentVO();
        attachment.setId(id);
        attachment.setName(name);
        attachment.setSize(size == null ? 0L : size);
        attachment.setType(type);
        return attachment;
    }

    /**
     * 前端传来的附件 ID 列表 → 附件清单。
     *
     * <p>查不到的 ID 直接跳过：文件可能已被清理，不该因此让整条消息发不出去。
     * 前端传的是字符串（雪花 ID 过 JS 会掉末位，只能以字符串传），所以这里逐个转回 Long。</p>
     *
     * @param lookup 按 ID 取文件信息，传 {@code fileService::getById} 即可
     */
    public static List<MessageAttachmentVO> fromFileIds(List<String> fileIds, Function<Long, FileInfo> lookup) {
        List<MessageAttachmentVO> attachments = new ArrayList<>();
        if (fileIds == null || fileIds.isEmpty()) {
            return attachments;
        }
        for (String rawId : fileIds) {
            Long fileId = parseFileId(rawId);
            FileInfo info = fileId == null ? null : lookup.apply(fileId);
            if (info == null) {
                continue;
            }
            String name = info.getOriginalFilename() == null || info.getOriginalFilename().isEmpty()
                    ? "附件_" + fileId : info.getOriginalFilename();
            attachments.add(attachment(String.valueOf(fileId), name, info.getFileSize(), extensionOf(name)));
        }
        return attachments;
    }

    /**
     * 附件清单 → 入库 JSON。空清单返回 null，便于用"attachments 是否为空"判断有无附件。
     */
    public static String toAttachmentsJson(List<MessageAttachmentVO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return ATTACHMENT_MAPPER.writeValueAsString(attachments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化消息附件失败", e);
        }
    }

    /**
     * 入库 JSON → 附件清单。
     *
     * <p>与应用层其它地方不同，这里**不抛异常**：单条脏数据不该让整页消息列表
     * 或共享文件列表打不开，解析失败按"没有附件"处理。</p>
     */
    public static List<MessageAttachmentVO> parseAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<MessageAttachmentVO> parsed = ATTACHMENT_MAPPER.readValue(
                    attachmentsJson, new TypeReference<List<MessageAttachmentVO>>() {});
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Long parseFileId(String rawId) {
        if (rawId == null || rawId.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(rawId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 与两端的 getFileExtension 保持同一套兜底值（"unknown"），避免同一文件两处算出不同类型 */
    private static String extensionOf(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
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