package com.abin.checkrepeatservice.pojo.dto;

import lombok.Data;

/**
 * 学科引用次数（SubjectMapper.countReferencesAll 的行结果）。
 *
 * 放在 pojo/dto 而不是 admin/vo：消费它的是 common/mapper/SubjectMapper，
 * 若把它挂在 admin 包下，common 就反向依赖了 admin，抽模块时第一个卡住。
 */
@Data
public class SubjectRefCountDTO {

    /** 学科ID */
    private Long subjectId;

    /** 引用总次数（论文 + 阈值配置 + 教师研究方向 + 专业映射） */
    private Integer refCount;

}
