package com.tlmstatueanimation.client.model;

import com.tlmstatueanimation.client.gui.RouletteEntry;

import java.util.List;

/**
 * YSM 模型 extra 动作表索引（D1）。
 * TODO: 当前为最小占位实现，由扫描器任务（T12）替换为真实实现：
 * 扫描 config/yes_steve_model/{built,custom,auth} 解析 properties.extra_animation。
 * 签名必须保持 {@code static List<RouletteEntry> lookup(String modelId)}。
 */
public final class YsmExtraAnimationIndex {

    private YsmExtraAnimationIndex() {
    }

    public static List<RouletteEntry> lookup(String modelId) {
        return List.of();
    }
}
