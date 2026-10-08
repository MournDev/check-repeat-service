package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 专业↔学科领域映射实体类（对应 major_subject 表）
 *
 * 多对多：一个专业可对应多个学科（如 计算机科学与技术 → ENG-CS / ENG-SE / ENG-AI），
 * 一个学科也可被多个专业共用。论文提交页就是靠它把学科候选从 100 个收窄到本专业相关的几个。
 *
 * ⚠️ 这张表用**物理删除**，不要用 BaseEntity 的 deleteById（@TableLogic 会改写软删）。
 *    uk_major_subject(major_id, subject_id) 不含 is_deleted，软删后重新加回同一对
 *    会直接撞唯一键。删除走 MajorSubjectMapper 的 @Delete 方法。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("major_subject")
public class MajorSubject extends BaseEntity {

    /**
     * 专业ID（关联 major.id）
     */
    @TableField("major_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long majorId;

    /**
     * 学科领域ID（关联 subject.id）
     */
    @TableField("subject_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long subjectId;

}
