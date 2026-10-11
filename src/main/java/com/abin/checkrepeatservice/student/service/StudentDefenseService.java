package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.vo.StudentDefenseVO;

import java.util.List;

/**
 * 学生端答辩服务接口（论文全生命周期 P3）。
 *
 * 学生只看到本人被安排的答辩批次与个人记录，含批次会议信息（时间/地点/方式/组长/秘书）
 * 与本人结论（如已出）。
 */
public interface StudentDefenseService {

    /**
     * 我的答辩安排（按答辩时间倒序，含已出结论）
     *
     * @param batchType 批次类型过滤（可空：mid_term / final）
     */
    Result<List<StudentDefenseVO>> listMyDefense(String batchType);
}
