package com.abin.checkrepeatservice.admin.vo;

import lombok.Data;

@Data
public class MajorPaperStatsVO {
    //专业id
    private Long majorId;
    //专业名称
    private String majorName;
    //论文数量
    private Integer paperCount;
}
