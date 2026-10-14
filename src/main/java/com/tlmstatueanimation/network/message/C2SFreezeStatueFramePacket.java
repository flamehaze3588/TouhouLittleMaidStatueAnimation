package com.tlmstatueanimation.network.message;

import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.StatueAnimFreezeControl;
import com.tlmstatueanimation.server.StatueFrameFreeze;
import com.tlmstatueanimation.server.StatueMaidNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 相机定格/解除定格的 C2S 包（本 mod 第五个自建网络包，id 4，§8.23）。
 * 触发：主手持 touhou_little_maid:camera（文文的相机）右键雕像/手办。
 * 语义：定格则写入 StatueFreezeAt（当前 gameTime）与 StatueFreezeTick（当前 tickCount 语义值），
 * 已定格则清除两键解除。定格瞬间播放快门音效 item.camera_use（广播给周围玩家）。
 */
public record C2SFreezeStatueFramePacket(BlockPos corePos) {
    /** 允许的最大交互距离（与其余 C2S 包一致：16 格，平方 256） */
    private static final double MAX_DISTANCE_SQR = 256.0;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.corePos);
    }

    public static C2SFreezeStatueFramePacket decode(FriendlyByteBuf buf) {
        return new C2SFreezeStatueFramePacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> handleOnServer(sender));
        context.setPacketHandled(true);
    }

    private void handleOnServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        ServerLevel level = sender.serverLevel();
        if (sender.distanceToSqr(this.corePos.getX() + 0.5, this.corePos.getY() + 0.5, this.corePos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue freeze packet: sender too far from {}", this.corePos);
            return;
        }
        if (!level.hasChunkAt(this.corePos)) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue freeze packet: chunk not loaded at {}", this.corePos);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(this.corePos);
        if (blockEntity instanceof TileEntityStatue statue) {
            if (!statue.isCoreBlock()) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue freeze packet: {} is not core statue block", this.corePos);
                return;
            }
            CompoundTag maidNbt = statue.getExtraMaidData();
            if (maidNbt == null) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue freeze packet: statue at {} has no maid data", this.corePos);
                return;
            }
            applyToggle(level, maidNbt);
            statue.refresh();
            return;
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            CompoundTag maidNbt = garageKit.getExtraData();
            if (maidNbt == null) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue freeze packet: garage kit at {} has no maid data", this.corePos);
                return;
            }
            applyToggle(level, maidNbt);
            garageKit.setData(garageKit.getFacing(), garageKit.getExtraData());
            return;
        }
        TlmStatueAnimation.LOGGER.debug("Ignore statue freeze packet: no statue/garage kit block entity at {}", this.corePos);
    }

    private void applyToggle(ServerLevel level, CompoundTag maidNbt) {
        long now = level.getGameTime();
        boolean currentlyUnfrozen = StatueMaidNbt.isYsmMaid(maidNbt)
                || (maidNbt.contains("ForgeData") && StatueAnimFreezeControl.isMoreAnimationActive(
                maidNbt.getCompound("ForgeData"), now));
        boolean nowFrozen = StatueFrameFreeze.toggle(maidNbt, now, currentlyUnfrozen);
        if (nowFrozen) {
            level.playSound(null, this.corePos, InitSounds.CAMERA_USE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }
}
