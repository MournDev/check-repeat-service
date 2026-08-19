package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.MessageTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplate> {

    /**
     * 根据模板代码查询模板
     */
    @Select("SELECT * FROM message_template WHERE template_code = #{templateCode} AND is_active = 1")
    MessageTemplate selectByCode(@Param("templateCode") String templateCode);

    /**
     * 根据模板类型查询模板列表
     */
    List<MessageTemplate> selectByType(@Param("templateType") String templateType);
}
