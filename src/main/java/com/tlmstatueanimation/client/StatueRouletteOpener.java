package com.tlmstatueanimation.client;

import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.client.gui.RouletteEntry;
import com.tlmstatueanimation.client.gui.StatueAnimationRouletteScreen;
import com.tlmstatueanimation.client.model.YsmExtraAnimationIndex;
import com.tlmstatueanimation.client.targeting.StatueRef;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 轮盘打开中间层：StatueRef �?动作表查�?�?StatueAnimationRouletteScreen�? */
public final class StatueRouletteOpener {

    private StatueRouletteOpener() {
    }

    public static void open(StatueRef ref) {
        String modelId = ref.maidNbt().getString(MaidNbtTags.YSM_MODEL_ID);
        List<RouletteEntry> entries = YsmExtraAnimationIndex.lookup(modelId);
        if (entries.isEmpty()) {
            return;
        }
        Minecraft.getInstance().setScreen(new StatueAnimationRouletteScreen(ref.corePos(), modelId, entries));
    }
}
