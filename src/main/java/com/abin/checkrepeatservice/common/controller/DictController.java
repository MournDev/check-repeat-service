package com.abin.checkrepeatservice.common.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.service.Impl.DictService;
import com.abin.checkrepeatservice.pojo.entity.SysDictData;
import com.abin.checkrepeatservice.student.vo.DictTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
@Slf4j
public class DictController {

    @Autowired
    private DictService dictService;

    /**
     * 根据字典类型获取字典数据
     */
    @GetMapping("/data/type/{dictType}")
    public Result<List<SysDictData>> getDictDataByType(@PathVariable String dictType) {
        List<SysDictData> dictDataList = dictService.getDictDataByType(dictType);
        return Result.success(dictDataList);
    }

    /**
     * 获取学科领域树形结构
     *
     * @param majorId 可选。论文提交页选完专业后带上来，只返回该专业映射到的学科分支；
     *                不传 = 整棵树。不传时的行为与本次改造前完全一致（旧前端零影响）。
     */
    @GetMapping("/subject/tree")
    public Result<List<DictTreeVO>> getSubjectFieldTree(
            @RequestParam(value = "majorId", required = false) Long majorId) {
        List<DictTreeVO> subjectFieldTreeData = dictService.getSubjectFieldTreeData(majorId);
        return Result.success(subjectFieldTreeData);
    }

    /**
     * 获取论文相关的所有字典数据
     */
    @GetMapping("/paper/all")
    public Result<Map<String, List<SysDictData>>> getPaperDictData() {
        Map<String, List<SysDictData>> dictData = dictService.getPaperDictData();
        return Result.success(dictData);
    }

    /**
     * 根据字典类型和值获取标签
     */
    @GetMapping("/label")
    public Result<String> getDictLabel(@RequestParam String dictType, @RequestParam String dictValue) {
        String label = dictService.getDictLabel(dictType, dictValue);
        return Result.success(label);
    }
}
