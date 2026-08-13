package com.tlmstatueanimation.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 雕像动作轮盘屏（D4：自建径向菜单，不复用 YSM 轮盘）。
 * 准星检测、扇区渲染与点击交互均在后续任务实现。
 */
public class StatueAnimationRouletteScreen extends Screen {
    private final BlockPos corePos;
    private final String modelId;
    private final List<RouletteEntry> entries;

    public StatueAnimationRouletteScreen(BlockPos corePos, String modelId, List<RouletteEntry> entries) {
        super(Component.empty());
        this.corePos = corePos;
        this.modelId = modelId;
        this.entries = entries;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
