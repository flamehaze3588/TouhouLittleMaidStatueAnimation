package com.tlmstatueanimation.network.message;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.server.StatueMaidNbt;
import com.tlmstatueanimation.server.StatueRoamingVars;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 雕像 YSM 漫游变量（模型配置项）写入的 C2S 包（本 mod 第四个自建网络包，id 3，§8.22）。
 * 触发：轮盘配置页勾选/切换配置项（显示/隐藏法印、九尾切换等）。
 * 语义：把 varName=value 写进雕像女仆 NBT 的 "YsmRoamingVars" 子标签（TLM 原生落盘位置），
 * 渲染时经 load() 灌进假女仆 roamingVars，YSM molang 的 v.roaming.* 随即读到新值。
 * 与 moreanimation 无关（纯 YSM 特性），不门控 moreanimation。
 */
public record C2SStatueRoamingVarPacket(BlockPos corePos, String varName, float value) {
    /** 允许的最大交互距离（与其余 C2S 包一致：16 格，平方 256） */
    private static final double MAX_DISTANCE_SQR = 256.0;
    /** 变量名长度防御上限（YSM RoamingStruct 为 32，放宽一倍防御异常包） */
    private static final int MAX_NAME_LENGTH = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.corePos);
        buf.writeUtf(this.varName);
        buf.writeFloat(this.value);
    }

    public static C2SStatueRoamingVarPacket decode(FriendlyByteBuf buf) {
        return new C2SStatueRoamingVarPacket(buf.readBlockPos(), buf.readUtf(MAX_NAME_LENGTH), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> handleOnServer(sender));
        context.setPacketHandled(true);
    }

    /**
     * 服务端校验链（任一失败静默 no-op + debug 日志）：距离 ≤16 格 → 区块已加载 →
     * TE 类型匹配（雕像须核心块）→ maidNbt 非空且为 YSM 模型（漫游变量只对 YSM 模型有意义）→
     * 变量名/值合法（StatueRoamingVars.setVar 内）。通过后写 NBT 并经 refresh()/setData() 同步。
     */
    private void handleOnServer(ServerPlayer sender) {
        if (sender == null) {
            return;
        }
        ServerLevel level = sender.serverLevel();
        if (sender.distanceToSqr(this.corePos.getX() + 0.5, this.corePos.getY() + 0.5, this.corePos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue roaming var packet: sender too far from {}", this.corePos);
            return;
        }
        if (!level.hasChunkAt(this.corePos)) {
            TlmStatueAnimation.LOGGER.debug("Ignore statue roaming var packet: chunk not loaded at {}", this.corePos);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(this.corePos);
        if (blockEntity instanceof TileEntityStatue statue) {
            if (!statue.isCoreBlock()) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue roaming var packet: {} is not core statue block", this.corePos);
                return;
            }
            CompoundTag maidNbt = statue.getExtraMaidData();
            if (maidNbt == null || !StatueMaidNbt.isYsmMaid(maidNbt)) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue roaming var packet: statue at {} has no ysm maid data", this.corePos);
                return;
            }
            if (StatueRoamingVars.setVar(maidNbt, this.varName, this.value)) {
                statue.refresh();
            }
            return;
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            CompoundTag maidNbt = garageKit.getExtraData();
            if (maidNbt == null || !StatueMaidNbt.isYsmMaid(maidNbt)) {
                TlmStatueAnimation.LOGGER.debug("Ignore statue roaming var packet: garage kit at {} has no ysm maid data", this.corePos);
                return;
            }
            if (StatueRoamingVars.setVar(maidNbt, this.varName, this.value)) {
                garageKit.setData(garageKit.getFacing(), garageKit.getExtraData());
            }
            return;
        }
        TlmStatueAnimation.LOGGER.debug("Ignore statue roaming var packet: no statue/garage kit block entity at {}", this.corePos);
    }
}
