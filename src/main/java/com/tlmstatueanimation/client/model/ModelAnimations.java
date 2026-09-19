package com.tlmstatueanimation.client.model;

import com.tlmstatueanimation.client.model.ysmfile.YsmBinaryModelWalker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个 YSM 模型的轮盘数据（D2 classify 子菜单 + §8.22 配置按钮）：
 * 根表 + extra_animation_classify 子菜单表（子菜单 id → 该层动作列表）。
 * key 以 '#' 开头的条目为子菜单入口（'#return' 为返回上一级），保留在列表中；
 * key 以 '#config:' 开头的条目为 molang 配置按钮入口（引用 extra_animation_buttons 的 id）。
 * 显示名本地化与根表同规则：lang 表 "properties.extra_animation.&lt;key&gt;" 覆盖，缺失回退默认名。
 */
public record ModelAnimations(List<YsmModelScanner.AnimEntry> root,
                              Map<String, List<YsmModelScanner.AnimEntry>> submenus,
                              List<YsmBinaryModelWalker.YsmConfigButton> configButtons) {

    /** 配置按钮轮盘条目的 key 前缀（+ 按钮 id） */
    public static final String CONFIG_KEY_PREFIX = "#config:";

    /** 空实例：YsmExtraAnimationIndex.lookup 未命中时返回 */
    public static final ModelAnimations EMPTY = new ModelAnimations(List.of(), Map.of(), List.of());

    public ModelAnimations {
        root = List.copyOf(root);
        // Map.copyOf 不保证迭代顺序，子菜单表保持声明顺序（LinkedHashMap）便于调试与测试断言
        submenus = Collections.unmodifiableMap(new LinkedHashMap<>(submenus));
        configButtons = List.copyOf(configButtons);
    }

    /** 根表、子菜单与配置按钮都空才算空（调用方据此判空） */
    public boolean isEmpty() {
        return root.isEmpty() && submenus.isEmpty() && configButtons.isEmpty();
    }

    /** 按 id 查配置按钮（轮盘 '#config:<id>' 条目点击时用），未命中返回 null */
    public YsmBinaryModelWalker.YsmConfigButton findConfigButton(String id) {
        for (YsmBinaryModelWalker.YsmConfigButton button : configButtons) {
            if (button.id().equals(id)) {
                return button;
            }
        }
        return null;
    }
}
