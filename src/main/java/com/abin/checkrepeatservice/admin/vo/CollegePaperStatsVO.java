package com.abin.checkrepeatservice.admin.vo;

import lombok.Data;

@Data
public class CollegePaperStatsVO {
    // 学院ID
    private Long collegeId;
    // 学院名称
    private String collegeName;
    // 论文数量
    private Integer paperCount;

}
