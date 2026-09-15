package com.tlmstatueanimation.network.message;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationCompat;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import com.tlmstatueanimation.server.StatueActionPhaseTicker;
import com.tlmstatueanimation.server.StatueForgeDataMerge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * 雕像表情/动作控制的 C2S 包（moreanimation 软联动，本 mod 第二个自建网络包，id 1）。
 * 语义：把 setKeys 合并进雕像女仆 NBT 的 "ForgeData" 子标签，并删除 removeKeys。
 * 一次性动作（play）客户端只发动作 key，start/until/priority/lock 由服务端按
 * level.getGameTime() 与 moreanimation 的时长/优先级补齐（见 handleOnServer）。
 */
public record C2SStatueExpressionPacket(BlockPos corePos, CompoundTag setKeys, List<String> removeKeys) {
    /** 允许的最大交互距离（与 C2SPlayStatueAnimationPacket 一致：16 格，平方 256） */
    private static final double MAX_DISTANCE_SQR = 256.0;
    /** 键数量防御上限（正常操作不超过十几个） */
    private static final int MAX_KEYS = 64;

    public C2SStatueExpressionPacket {
        setKeys = setKeys == null ? new CompoundTag() : setKeys;
        removeKeys = removeKeys == null ? List.of() : List.copyOf(removeKeys);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.corePos);
        buf.writeNbt(this.setKeys);
        buf.writeCollection(this.removeKeys, FriendlyByteBuf::writeUtf);
    }

    public static C2SStatueExpressionPacket decode(FriendlyByteBuf buf) {
        BlockPos corePos = buf.readBlockPos();
        CompoundTag setKeys = buf.readNbt();
        List<String> removeKeys = buf.readList(FriendlyByteBuf::readUtf);
        return new C2SStatueExpressionPacket(corePos, setKeys, removeKeys);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> handleOnServer(sender));
        context.setPacketHandled(true);
    }

    /**
     * 服务端校验链（任一失败静默 no-op + debug 日志）：moreanimation 已安装 → 距离 ≤16 格 →
     * 区块已加载 → 键数量上限 → TE 类型匹配（雕像须核心块）→ maidNbt 非空。
     * 与 C2SPlayStatueAnimationPacket 的差异：不要求 IsYsmModel——moreanimation 对
     * TLM 默认 bedrock 模型同样生效。校验通过后就地改 ForgeData，经 refresh()/setData()
     * 触发原版 ClientboundBlockEntityDataPacket 同步。
     */
    private void handleOnServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        // 未安装 moreanimation 时写入其状态键无意义；此分支也保证 MoreAnimationCompat 内部类不被触达
        if (!MoreAnimationCompat.isLoaded()) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: moreanimation not loaded");
            return;
        }
        ServerLevel level = sender.serverLevel();
        if (sender.distanceToSqr(this.corePos.getX() + 0.5, this.corePos.getY() + 0.5, this.corePos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: sender too far from {}", this.corePos);
            return;
        }
        if (!level.hasChunkAt(this.corePos)) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: chunk not loaded at {}", this.corePos);
            return;
        }
        if (this.setKeys.size() > MAX_KEYS || this.removeKeys.size() > MAX_KEYS) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: too many keys ({}/{})", this.setKeys.size(), this.removeKeys.size());
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(this.corePos);
        if (blockEntity instanceof TileEntityStatue statue) {
            // 客户端已解析好核心块坐标；数据竞争下打到非核心块时静默忽略
            if (!statue.isCoreBlock()) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: {} is not core statue block", this.corePos);
                return;
            }
            CompoundTag maidNbt = statue.getExtraMaidData();
            if (maidNbt == null) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: statue at {} has no maid data", this.corePos);
                return;
            }
            applyWrites(level, maidNbt);
            statue.refresh();
            return;
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            CompoundTag maidNbt = garageKit.getExtraData();
            if (maidNbt == null) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: garage kit at {} has no maid data", this.corePos);
                return;
            }
            applyWrites(level, maidNbt);
            garageKit.setData(garageKit.getFacing(), garageKit.getExtraData());
            return;
        }
        TlmStatueAnimation.LOGGER.debug("Ignore statue expression packet: no statue/garage kit block entity at {}", this.corePos);
    }

    private void applyWrites(ServerLevel level, CompoundTag maidNbt) {
        // 一次性动作：客户端只发动作 key，服务端用 gameTime + moreanimation 时长/优先级补齐时间窗
        if (this.setKeys.contains(MoreAnimationNbtKeys.ACTIVE, Tag.TAG_STRING)
                && !this.setKeys.getString(MoreAnimationNbtKeys.ACTIVE).isEmpty()) {
            String action = this.setKeys.getString(MoreAnimationNbtKeys.ACTIVE);
            StatueForgeDataMerge.fillPlayTiming(this.setKeys, level.getGameTime(),
                    MoreAnimationCompat.duration(action), MoreAnimationCompat.playPriorityFor(action));
        }
        StatueForgeDataMerge.apply(maidNbt, this.setKeys, this.removeKeys);
        // 两阶段动作（tastetail→eattail）的接力由服务端 tick 补全（§8.15）：登记本雕像
        if (this.setKeys.contains(MoreAnimationNbtKeys.ACTIVE, Tag.TAG_STRING)
                && !this.setKeys.getString(MoreAnimationNbtKeys.ACTIVE).isEmpty()) {
            StatueActionPhaseTicker.track(level, this.corePos);
        }
    }
}
