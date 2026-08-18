package com.tlmstatueanimation.client;

import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.targeting.StatueTargeting;
import com.tlmstatueanimation.client.targeting.StatueTargetingForge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 雕像轮盘按键 tick 胶水（D2）：ClientTickEvent.End 中 consumeClick 轮询，
 * 准星命中方块时经 StatueTargeting 解析并打开轮盘。
 */
@Mod.EventBusSubscriber(modid = TlmStatueAnimation.MOD_ID, value = Dist.CLIENT)
public final class StatueRouletteTickHandler {

    private StatueRouletteTickHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        while (StatueAnimationKeys.OPEN_STATUE_ROULETTE.consumeClick()) {
            if (mc.hitResult instanceof BlockHitResult blockHit) {
                StatueTargeting.resolve(blockHit, pos -> StatueTargetingForge.probe(mc.level, pos))
                        .ifPresent(StatueRouletteOpener::open);
            }
        }
    }
}
