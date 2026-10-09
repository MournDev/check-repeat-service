package com.abin.checkrepeatservice.teacher.service;

import com.abin.checkrepeatservice.teacher.dto.ReviewTemplateSaveReq;
import com.abin.checkrepeatservice.teacher.vo.ReviewTemplateVO;

import java.util.List;

/**
 * 审核意见模板服务。
 *
 * <p>可见性规则（整个服务一贯）：
 * <ul>
 *   <li>列表 = 自己创建的 + 别人公开的；</li>
 *   <li>「使用」同样按这个可见范围 —— 公开模板的意义就在于别人能用；</li>
 *   <li>「修改」「删除」只限自己创建的 —— 公开不等于可编辑，
 *       否则任何人都能改掉别人的模板内容，而原作者下次使用时才发现内容变了。</li>
 * </ul>
 */
public interface ReviewTemplateService {

    /** 当前教师可见的模板列表（自己的 + 公开的），按最后使用时间倒序 */
    List<ReviewTemplateVO> list();

    /** 新建模板，创建人由审计字段自动填充 */
    ReviewTemplateVO create(ReviewTemplateSaveReq req);

    /** 更新模板，仅限本人创建 */
    ReviewTemplateVO update(Long id, ReviewTemplateSaveReq req);

    /** 删除模板（软删），仅限本人创建 */
    void delete(Long id);

    /** 使用一次模板：使用次数 +1、记录最后使用时间 */
    ReviewTemplateVO use(Long id);

}
