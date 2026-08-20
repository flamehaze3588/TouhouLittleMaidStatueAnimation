package com.tlmstatueanimation.client.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个 YSM 模型的轮盘动作数据（D2 classify 子菜单）：
 * 根表 + extra_animation_classify 子菜单表（子菜单 id → 该层动作列表）。
 * key 以 '#' 开头的条目为子菜单入口（'#return' 为返回上一级），保留在列表中；
 * value 以 '#' 开头的条目是 YSM molang 配置按钮（本 mod 不支持），扫描期已整条过滤。
 * 显示名本地化与根表同规则：lang 表 "properties.extra_animation.&lt;key&gt;" 覆盖，缺失回退默认名。
 */
public record ModelAnimations(List<YsmModelScanner.AnimEntry> root,
                              Map<String, List<YsmModelScanner.AnimEntry>> submenus) {

    /** 空实例：YsmExtraAnimationIndex.lookup 未命中时返回 */
    public static final ModelAnimations EMPTY = new ModelAnimations(List.of(), Map.of());

    public ModelAnimations {
        root = List.copyOf(root);
        // Map.copyOf 不保证迭代顺序，子菜单表保持声明顺序（LinkedHashMap）便于调试与测试断言
        submenus = Collections.unmodifiableMap(new LinkedHashMap<>(submenus));
    }

    /** 根表与子菜单都空才算空（调用方据此判空） */
    public boolean isEmpty() {
        return root.isEmpty() && submenus.isEmpty();
    }
}
