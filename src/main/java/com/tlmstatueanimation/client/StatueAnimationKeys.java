package com.tlmstatueanimation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tlm_statue_animation", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StatueAnimationKeys {
    public static final KeyMapping OPEN_STATUE_ROULETTE = new KeyMapping("key.tlm_statue_animation.open_statue_roulette",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, "key.categories.tlm_statue_animation");

    private StatueAnimationKeys() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_STATUE_ROULETTE);
    }
}
