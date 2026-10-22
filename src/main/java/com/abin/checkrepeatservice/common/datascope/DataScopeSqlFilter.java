package com.abin.checkrepeatservice.common.datascope;

import lombok.Getter;

/**
 * 数据范围过滤参数（供 XML/自定义 SQL 复用）。
 *
 * <p>LambdaQueryWrapper 场景直接用 {@link DataScopeResolver#applyPaperScope}；
 * 报告列表、评阅任务等自定义 SQL 把本对象传给 Mapper，在 XML 里按
 * {@code scope.denyAll / scope.collegeId / ...} 追加条件。</p>
 */
@Getter
public class DataScopeSqlFilter {

    /** 无范围限制（ALL） */
    public static final DataScopeSqlFilter NONE =
            new DataScopeSqlFilter(false, null, null, null, null);

    /** 范围不足以定位（如学院范围但用户无学院）：直接拒绝 */
    public static final DataScopeSqlFilter DENY_ALL =
            new DataScopeSqlFilter(true, null, null, null, null);

    private final boolean denyAll;
    private final Long collegeId;
    private final Long majorId;
    private final Long teacherId;
    private final Long studentId;

    private DataScopeSqlFilter(boolean denyAll, Long collegeId, Long majorId,
                               Long teacherId, Long studentId) {
        this.denyAll = denyAll;
        this.collegeId = collegeId;
        this.majorId = majorId;
        this.teacherId = teacherId;
        this.studentId = studentId;
    }

    public static DataScopeSqlFilter college(Long collegeId) {
        return new DataScopeSqlFilter(false, collegeId, null, null, null);
    }

    public static DataScopeSqlFilter major(Long majorId) {
        return new DataScopeSqlFilter(false, null, majorId, null, null);
    }

    public static DataScopeSqlFilter teacher(Long teacherId) {
        return new DataScopeSqlFilter(false, null, null, teacherId, null);
    }

    public static DataScopeSqlFilter self(Long studentId) {
        return new DataScopeSqlFilter(false, null, null, null, studentId);
    }
}
