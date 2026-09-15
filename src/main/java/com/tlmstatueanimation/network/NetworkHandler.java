package com.tlmstatueanimation.network;

import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.network.message.C2SPlayStatueAnimationPacket;
import com.tlmstatueanimation.network.message.C2SStatueExpressionPacket;
import com.tlmstatueanimation.network.message.C2SToggleStatueSitPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class NetworkHandler {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(TlmStatueAnimation.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private NetworkHandler() {
    }

    public static void init() {
        CHANNEL.registerMessage(0, C2SPlayStatueAnimationPacket.class, C2SPlayStatueAnimationPacket::encode,
                C2SPlayStatueAnimationPacket::decode, C2SPlayStatueAnimationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // id 1：moreanimation 软联动的雕像表情/动作控制包
        CHANNEL.registerMessage(1, C2SStatueExpressionPacket.class, C2SStatueExpressionPacket::encode,
                C2SStatueExpressionPacket::decode, C2SStatueExpressionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // id 2：蹲下 + expression_item 右键 → 雕像站姿/坐姿切换包
        CHANNEL.registerMessage(2, C2SToggleStatueSitPacket.class, C2SToggleStatueSitPacket::encode,
                C2SToggleStatueSitPacket::decode, C2SToggleStatueSitPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(Object message) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), message);
    }
}
