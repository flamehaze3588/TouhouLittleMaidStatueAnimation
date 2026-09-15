package com.tlmstatueanimation.network.message;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationCompat;
import com.tlmstatueanimation.server.StatueSitToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 雕像站姿 ↔ 坐姿切换的 C2S 包（本 mod 第三个自建网络包，id 2）。
 * 触发：主手持 moreanimation:expression_item，蹲下 + 右键雕像/手办
 * （此时不再打开 {@code StatueExpressionScreen}）。
 * 语义：翻转雕像女仆 NBT 根部的原版 "Sitting" 布尔键（见 {@link StatueSitToggle}）。
 * 注意不走 StatueForgeDataMerge——该合并器只放行 moreanimation_ 前缀键，
 * 而 Sitting 是实体根级键，属另一条写入路径。
 */
public record C2SToggleStatueSitPacket(BlockPos corePos) {
    /** 允许的最大交互距离（与 C2SPlayStatueAnimationPacket 一致：16 格，平方 256） */
    private static final double MAX_DISTANCE_SQR = 256.0;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.corePos);
    }

    public static C2SToggleStatueSitPacket decode(FriendlyByteBuf buf) {
        return new C2SToggleStatueSitPacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> handleOnServer(sender));
        context.setPacketHandled(true);
    }

    /**
     * 服务端校验链（任一失败静默 no-op + debug 日志）：moreanimation 已安装（触发物品来自该
     * mod，未装则无合法触发路径）→ 距离 ≤16 格 → 区块已加载 → TE 类型匹配（雕像须核心块）→
     * maidNbt 非空。校验通过后就地翻转 Sitting，经 refresh()/setData() 触发原版
     * ClientboundBlockEntityDataPacket 同步（与 C2SStatueExpressionPacket 同模式）。
     */
    private void handleOnServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        if (!MoreAnimationCompat.isLoaded()) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: moreanimation not loaded");
            return;
        }
        ServerLevel level = sender.serverLevel();
        if (sender.distanceToSqr(this.corePos.getX() + 0.5, this.corePos.getY() + 0.5, this.corePos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: sender too far from {}", this.corePos);
            return;
        }
        if (!level.hasChunkAt(this.corePos)) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: chunk not loaded at {}", this.corePos);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(this.corePos);
        if (blockEntity instanceof TileEntityStatue statue) {
            // 客户端已解析好核心块坐标；数据竞争下打到非核心块时静默忽略
            if (!statue.isCoreBlock()) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: {} is not core statue block", this.corePos);
                return;
            }
            CompoundTag maidNbt = statue.getExtraMaidData();
            if (maidNbt == null) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: statue at {} has no maid data", this.corePos);
                return;
            }
            boolean sitting = StatueSitToggle.toggle(maidNbt);
            statue.refresh();
            TlmStatueAnimation.LOGGER.debug("Statue at {} sitting toggled to {}", this.corePos, sitting);
            return;
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            CompoundTag maidNbt = garageKit.getExtraData();
            if (maidNbt == null) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: garage kit at {} has no maid data", this.corePos);
                return;
            }
            boolean sitting = StatueSitToggle.toggle(maidNbt);
            garageKit.setData(garageKit.getFacing(), garageKit.getExtraData());
            TlmStatueAnimation.LOGGER.debug("Garage kit at {} sitting toggled to {}", this.corePos, sitting);
            return;
        }
        TlmStatueAnimation.LOGGER.debug("Ignore statue sit toggle packet: no statue/garage kit block entity at {}", this.corePos);
    }
}
