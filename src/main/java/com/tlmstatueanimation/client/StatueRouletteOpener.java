package com.tlmstatueanimation.client;

import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.gui.StatueAnimationRouletteScreen;
import com.tlmstatueanimation.client.model.ModelAnimations;
import com.tlmstatueanimation.client.model.YsmExtraAnimationIndex;
import com.tlmstatueanimation.client.targeting.StatueRef;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 轮盘打开中间层：StatueRef → 动作表查询 → StatueAnimationRouletteScreen。 */
public final class StatueRouletteOpener {

    private StatueRouletteOpener() {
    }

    public static void open(StatueRef ref) {
        String modelId = ref.maidNbt().getString(MaidNbtTags.YSM_MODEL_ID);
        ModelAnimations animations = YsmExtraAnimationIndex.lookup(modelId);
        if (animations.isEmpty()) {
            // 雕像是 YSM 模型但动作表查无此 modelId：给玩家可见反馈，便于排查（模型目录扫描盲区/格式不符）
            TlmStatueAnimation.LOGGER.info("No extra animations indexed for statue model id: {}", modelId);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("chat.tlm_statue_animation.no_animations", modelId), true);
            }
            return;
        }
        Minecraft.getInstance().setScreen(new StatueAnimationRouletteScreen(ref.corePos(), modelId, animations));
    }
}
