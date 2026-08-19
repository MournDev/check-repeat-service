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
     */
    public List<DictTreeVO> getSubjectFieldTree() {
        // 1. 先查询所有学科领域字典数据（状态正常）
        List<SysDictData> allSubjectData = getDictDataByType("subject_field");

        // 2. 分离一级学科和二级学科
        // 一级学科：编码无“-”（如 ENG、MED）
        List<SysDictData> firstLevelData = allSubjectData.stream()
                .filter(data -> !data.getDictValue().contains("-"))
                .collect(Collectors.toList());

        // 二级学科：编码含“-”（如 ENG-AI、MED-CLIN）
        List<SysDictData> secondLevelData = allSubjectData.stream()
                .filter(data -> data.getDictValue().contains("-"))
                .collect(Collectors.toList());

        // 3. 组装树形结构：给每个一级学科分配对应的二级学科
        return firstLevelData.stream().map(firstLevel -> {
            DictTreeVO firstLevelVO = new DictTreeVO();
            firstLevelVO.setLabel(firstLevel.getDictLabel()); // 一级名称（如“工学”）
            firstLevelVO.setValue(firstLevel.getDictValue()); // 一级编码（如“ENG”）

            // 筛选当前一级学科对应的二级学科（二级编码前缀=一级编码）
            List<DictTreeVO> children = secondLevelData.stream()
                    .filter(secondLevel -> secondLevel.getDictValue().startsWith(firstLevel.getDictValue() + "-"))
                    .map(secondLevel -> {
                        DictTreeVO secondLevelVO = new DictTreeVO();
                        secondLevelVO.setLabel(secondLevel.getDictLabel()); // 二级名称（如“人工智能”）
                        secondLevelVO.setValue(secondLevel.getDictValue()); // 二级编码（如“ENG-AI”）
                        secondLevelVO.setChildren(null); // 学科最多二级，子节点为null
                        return secondLevelVO;
                    })
                    .collect(Collectors.toList());

            firstLevelVO.setChildren(children);
            return firstLevelVO;
        }).collect(Collectors.toList());
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
     */
    public List<DictTreeVO> getSubjectFieldTreeData() {
        return getSubjectFieldTree();
    }
}
