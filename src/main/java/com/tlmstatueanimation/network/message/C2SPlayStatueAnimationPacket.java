package com.tlmstatueanimation.network.message;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.server.StatueMaidNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端请求雕像播放/停止 YSM 动作的 C2S 包（D5：本附属 mod 唯一自建网络面）。
 */
public record C2SPlayStatueAnimationPacket(BlockPos corePos, String animationKey, boolean stop) {
    /** 允许的最大交互距离（与 TLM 女仆交互距离一致的常用值），平方为 256 */
    private static final double MAX_DISTANCE_SQR = 256.0;

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
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> handleOnServer(sender));
        context.setPacketHandled(true);
    }

    /**
     * 服务端校验链（任一失败静默 no-op + debug 日志）：
     * 距离 ≤16 格 → 区块已加载 → TE 类型匹配（雕像须核心块）→ maidNbt 非空且 IsYsmModel。
     * 校验通过后就地改 NBT，经 refresh()/setData() 触发原版 ClientboundBlockEntityDataPacket 同步（D5）。
     */
    private void handleOnServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        ServerLevel level = sender.serverLevel();
        if (sender.distanceToSqr(this.corePos.getX() + 0.5, this.corePos.getY() + 0.5, this.corePos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue animation packet: sender too far from {}", this.corePos);
            return;
        }
        if (!level.hasChunkAt(this.corePos)) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue animation packet: chunk not loaded at {}", this.corePos);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(this.corePos);
        if (blockEntity instanceof TileEntityStatue statue) {
            // 客户端已解析好核心块坐标；数据竞争下打到非核心块时静默忽略
            if (!statue.isCoreBlock()) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue animation packet: {} is not core statue block", this.corePos);
                return;
            }
            CompoundTag maidNbt = statue.getExtraMaidData();
            if (maidNbt == null || !StatueMaidNbt.isYsmMaid(maidNbt)) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue animation packet: statue at {} has no ysm maid data", this.corePos);
                return;
            }
            write(maidNbt);
            statue.refresh();
            return;
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            CompoundTag maidNbt = garageKit.getExtraData();
            if (!StatueMaidNbt.isYsmMaid(maidNbt)) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue animation packet: garage kit at {} has no ysm maid data", this.corePos);
                return;
            }
            write(maidNbt);
            garageKit.setData(garageKit.getFacing(), garageKit.getExtraData());
            return;
        }
        TlmStatueAnimation.LOGGER.debug("Ignore statue animation packet: no statue/garage kit block entity at {}", this.corePos);
    }

    private void write(CompoundTag maidNbt) {
        if (this.stop) {
            StatueMaidNbt.writeStop(maidNbt);
        } else {
            StatueMaidNbt.writePlay(maidNbt, this.animationKey);
        }
    }
}
