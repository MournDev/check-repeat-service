package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超期论文一键催办结果（Wave 3）：逐篇结果 + 留痕ID。
 */
@Data
public class OverdueRemindResultVO {

    private Integer total;

    private Integer success;

    private Integer failed;

    private List<String> failReasons;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime remindedAt;

    private List<RemindRecord> records;

    @Data
    public static class RemindRecord {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long paperId;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long teacherId;

        private Boolean success;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long reminderLogId;
    }
}
