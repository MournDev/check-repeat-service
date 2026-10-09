package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.PaperTypeEnum;
import com.abin.checkrepeatservice.mapper.SysDictDataMapper;
import com.abin.checkrepeatservice.pojo.entity.SysDictData;
import com.abin.checkrepeatservice.student.vo.DictTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
     *
     * paper_type 是个例外：它的真相源是 PaperTypeEnum，不是 sys_dict_data。
     * 那批字典行本仓库从来没有种子数据（init.sql 只有建表、种子数据.sql 无一条 INSERT），
     * 线上能用只是因为线上库被手工插过行 —— 空库跑起来这里返回空列表，
     * 提交页下拉为空而 paperType 又必填，学生端论文提交流程直接走不下去。
     * 改成由枚举合成后，空库开箱可用，且库里残留的旧行再也不会被读到（不会与枚举漂移）。
     *
     * 返回结构保持 SysDictData（dictValue=code、dictLabel=desc），
     * 所以前端那条 /api/dict/data/type/paper_type 的 URL 与解析逻辑一行都不用改。
     */
    public List<SysDictData> getDictDataByType(String dictType) {
        if (DictConstants.DICT_PAPER_TYPE.equals(dictType)) {
            return paperTypesAsDictData();
        }
        return sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, "0")
                .orderByAsc(SysDictData::getDictSort));
    }

    /**
     * 把 PaperTypeEnum 投影成字典行的形状。
     *
     * 只填前端真正会读的几列（dictValue / dictLabel / dictSort / dictType / status）。
     * id 留空：这些行不存在于数据库，给它们编 id 只会让人以为可以按 id 回查。
     * 合成逻辑留在本类而不挂到枚举上，是为了让枚举不依赖 entity —— 它要被
     * 校验注解、阈值解析、管理端共用，不该被 POJO 的字段结构绑架。
     */
    private List<SysDictData> paperTypesAsDictData() {
        PaperTypeEnum[] types = PaperTypeEnum.values();
        List<SysDictData> list = new ArrayList<>(types.length);
        for (int i = 0; i < types.length; i++) {
            PaperTypeEnum type = types[i];
            SysDictData row = new SysDictData();
            row.setDictType(DictConstants.DICT_PAPER_TYPE);
            row.setDictValue(type.getCode());
            row.setDictLabel(type.getDescription());
            // 顺序即枚举声明顺序：前端下拉的次序由此决定，不再受 dict_sort 影响
            row.setDictSort(i);
            row.setStatus("0");
            list.add(row);
        }
        return list;
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
     * 根据字典类型和字典值获取字典标签。未命中时返回原值（调用方据此原样展示裸码）。
     *
     * paper_type 同样走枚举，理由见 getDictDataByType：库里那批行不是真相源。
     */
    public String getDictLabel(String dictType, String dictValue) {
        if (DictConstants.DICT_PAPER_TYPE.equals(dictType)) {
            String desc = PaperTypeEnum.descOf(dictValue);
            return desc != null ? desc : dictValue;
        }
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
                // 由枚举合成，不查库 —— 见 getDictDataByType 的说明
                "paperType", getDictDataByType(DictConstants.DICT_PAPER_TYPE)
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
