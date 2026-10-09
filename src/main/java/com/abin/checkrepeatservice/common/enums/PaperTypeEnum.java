package com.abin.checkrepeatservice.common.enums;

import lombok.Getter;

/**
 * 论文类型枚举 —— 论文类型取值域的**唯一真相源**。
 *
 * 取代了两处旧的、互相独立的值域：
 *   · DictConstants 里那个没人引用的嵌套类 PaperType（已删除）；
 *   · sys_dict_data 里 dict_type='paper_type' 的字典行 —— 那批行**本仓库从来没有种子数据**，
 *     空库跑起来接口返回空数组，提交页下拉是空的而 paperType 又必填，学生端直接卡死。
 *     现在 DictService 遇到 paper_type 一律由本枚举合成，不再查库，空库开箱可用。
 *
 * 前端不复制本枚举的文案：提交页/详情页/列表页共用一个前端模块，选项与展示文案都来自
 * /api/dict/data/type/paper_type（其数据源就是本枚举），因此前后端不可能再各持一套值域。
 *
 * 持久化形态是 String（code），不是本枚举 —— 项目没有 TypeHandler 基础设施
 * （无 @EnumValue / IEnum / type-handlers-package），实体字段一律是 String。
 * 这样存量库里若残留历史非法值（例如 THESIS/JOURNAL/CONFERENCE 那套幽灵值），
 * 读取时也不会抛异常，只是匹配不到本枚举、走不到类型维度的阈值而已。
 */
@Getter
public enum PaperTypeEnum {

    /**
     * 毕业论文
     */
    GRADUATION("graduation", "毕业论文"),

    /**
     * 课程论文
     */
    COURSE("course", "课程论文"),

    /**
     * 其他
     */
    OTHER("other", "其他");

    private final String code;
    private final String description;

    PaperTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * code → 枚举。未命中抛异常，供"调用方已经确信值合法"的场景使用。
     * 校验场景请用 isValidCode / descOf，那两个是不抛的。
     */
    public static PaperTypeEnum fromCode(String code) {
        for (PaperTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的论文类型: " + code);
    }

    /**
     * 验证论文类型是否在取值域内。null / 空串 / 纯空白都返回 false
     * —— 这几种情况由 DTO 上的 @NotBlank 负责报"不能为空"，本方法不与之抢文案。
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (PaperTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * code → 展示文案，未命中返回 null（调用方自行决定回退成裸值还是"已失效"）。
     * 返回值不做 trim：脏值应当被看见，而不是被悄悄规整成一个"看起来正常"的文案。
     */
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (PaperTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type.getDescription();
            }
        }
        return null;
    }
}
