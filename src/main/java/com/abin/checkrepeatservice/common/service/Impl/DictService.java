package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.mapper.SysDictDataMapper;
import com.abin.checkrepeatservice.pojo.entity.SysDictData;
import com.abin.checkrepeatservice.student.vo.DictTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DictService {

    @Autowired
    private SysDictDataMapper sysDictDataMapper;

    @Autowired
    private SubjectService subjectService;

    /**
     * 根据字典类型获取字典数据列表
     */
    public List<SysDictData> getDictDataByType(String dictType) {
        return sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, "0")
                .orderByAsc(SysDictData::getDictSort));
    }

    /**
     * 根据字典类型获取字典值映射
     */
    public Map<String, String> getDictValueMap(String dictType) {
        List<SysDictData> dictDataList = getDictDataByType(dictType);
        return dictDataList.stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel));
    }

    /**
     * 根据字典类型和字典值获取字典标签
     */
    public String getDictLabel(String dictType, String dictValue) {
        SysDictData dictData = sysDictDataMapper.selectOne(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getDictValue, dictValue)
                .eq(SysDictData::getStatus, "0"));
        return dictData != null ? dictData.getDictLabel() : dictValue;
    }

    /**
     * 获取学科领域树形结构
     *
     * 数据源已从 sys_dict_data 的字符串树换成 subject 表（含 parent_id 的真实层级）。
     *
     * ⚠️ 旧的字典行**一行都没删**，仍然留在库里 —— DEPLOY.md 第八节写明 deploy.sh
     * 健康检查失败只回滚应用镜像、DDL 不回滚，于是旧代码（正是上面那段读字典的实现）
     * 会跑在新结构上；字典还在，回滚后学科选择器才不会空白。
     * 所以这段被替换的实现是**故意不要了**，不是遗漏；反向恢复见 存量修复.sql 附录 B。
     */
    public List<DictTreeVO> getSubjectFieldTree() {
        return subjectService.getTree(null);
    }

    /**
     * 获取论文相关的所有字典数据
     */
    public Map<String, List<SysDictData>> getPaperDictData() {
        return Map.of(
                "paperStatus", getDictDataByType("paper_status"),
                "submitStatus", getDictDataByType("submit_status"),
                "checkStatus", getDictDataByType("check_status"),
                "paperType", getDictDataByType("paper_type")
        );
    }
    /**
     * 获取学科领域树形结构数据
     *
     * @param majorId 可选。传了就只返回该专业映射到的学科分支（论文提交页选完专业后重拉用），
     *                不传 = 整棵树（教师研究方向选择、管理端用）
     */
    public List<DictTreeVO> getSubjectFieldTreeData(Long majorId) {
        return subjectService.getTree(majorId);
    }
}
