package com.tlmstatueanimation.network.message;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端请求雕像播放/停止 YSM 动作的 C2S 包（D5：本附属 mod 唯一自建网络面）。
 */
public record C2SPlayStatueAnimationPacket(BlockPos corePos, String animationKey, boolean stop) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.corePos);
        buf.writeUtf(this.animationKey);
        buf.writeBoolean(this.stop);
    }

    public static C2SPlayStatueAnimationPacket decode(FriendlyByteBuf buf) {
        BlockPos corePos = buf.readBlockPos();
        String animationKey = buf.readUtf();
        boolean stop = buf.readBoolean();
        return new C2SPlayStatueAnimationPacket(corePos, animationKey, stop);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        // TODO: 服务端处理逻辑在后续任务实现（enqueueWork：校验距离 ≤16 格、区块加载、TE 类型与
        //  IsYsmModel，就地修改 extraMaidData 的 YsmRouletteAnim / StatueRoulettePlaying，
        //  然后 TileEntityStatue.refresh() / TileEntityGarageKit.setData(...) 触发原版 S2C 同步）
    }
}
