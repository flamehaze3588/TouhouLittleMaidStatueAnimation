package com.tlmstatueanimation.server;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雕像一次性动作的"阶段接力"服务端补全（§8.15）。
 * <p>
 * moreanimation 的 {@code MaidAnimationData.serverTick} 挂在真女仆实体的每 tick 上，负责
 * 两阶段动作的接力：tastetail（抱尾巴导入段）开始 45 tick 后改写为 eattail（啃尾巴循环段）
 * 并续 100 tick。雕像是方块实体 + 客户端假女仆，服务端没有这个 tick 钩子——雕像只会播完
 * 导入段就直接过期，缺了循环段。此处复刻该接力：C2S 包写入一次性动作时登记雕像坐标，
 * 服务端每 tick 检查登记的雕像，到点改写 ForgeData 并触发同步（refresh/setData）。
 * <p>
 * 接力参数（45 tick / eattail / 100 tick）镜像 moreanimation 的硬编码值；
 * 登记项在动作过期、ACTIVE 键清空或女仆数据不可达（拆除/区块卸载）时移除。
 */
@Mod.EventBusSubscriber(modid = TlmStatueAnimation.MOD_ID)
public final class StatueActionPhaseTicker {
    /** 以下常量镜像 moreanimation MaidAnimationData.serverTick 的 tastetail→eattail 接力 */
    private static final String PHASE_ONE = "tastetail";
    private static final String PHASE_TWO = "eattail";
    private static final long HANDOFF_TICKS = 45;

    private record TrackedKey(ResourceKey<Level> dim, BlockPos pos) {
    }

    private static final Set<TrackedKey> TRACKED = ConcurrentHashMap.newKeySet();

    private StatueActionPhaseTicker() {
    }

    /** 登记一个刚写入一次性动作的雕像（C2SStatueExpressionPacket 调用）。 */
    public static void track(ServerLevel level, BlockPos corePos) {
        TRACKED.add(new TrackedKey(level.dimension(), corePos.immutable()));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (TrackedKey key : TRACKED) {
            ServerLevel level = server.getLevel(key.dim());
            if (level == null) {
                TRACKED.remove(key);
                continue;
            }
            tickStatue(level, key);
        }
    }

    private static void tickStatue(ServerLevel level, TrackedKey key) {
        BlockEntity blockEntity = level.getBlockEntity(key.pos());
        CompoundTag maidNbt = extractMaidNbt(blockEntity);
        if (maidNbt == null || !maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA, Tag.TAG_COMPOUND)) {
            // 雕像被拆/无女仆数据/区块未加载（未加载时 getBlockEntity 返回 null）——
            // 区分不出"拆了"与"未加载"：保守移除，等用户下次操作时重新登记
            TRACKED.remove(key);
            return;
        }
        CompoundTag forgeData = maidNbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA);
        String active = forgeData.getString(MoreAnimationNbtKeys.ACTIVE);
        long now = level.getGameTime();
        long until = forgeData.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL);
        if (active.isEmpty() || now >= until) {
            // 动作结束/过期：若雕像是 sit2 坐姿则恢复基础姿势（§8.16），否则登记使命完成
            if (StatueBasePose.restoreBasePose(maidNbt, now)) {
                sync(blockEntity);
                TlmStatueAnimation.LOGGER.debug("Statue at {} restored sit2 base pose", key.pos());
            }
            TRACKED.remove(key);
            return;
        }
        if (needsHandoff(active, forgeData.getLong(MoreAnimationNbtKeys.ACTIVE_START), until, now)) {
            applyPhaseTwo(forgeData, now);
            sync(blockEntity);
            TlmStatueAnimation.LOGGER.debug("Statue at {} phase handoff: {} -> {}", key.pos(), PHASE_ONE, PHASE_TWO);
        }
    }

    /** 纯判定：是否到了 tastetail→eattail 的接力点（进行中且已播满 45 tick）。可单测。 */
    static boolean needsHandoff(String active, long start, long until, long now) {
        return PHASE_ONE.equals(active) && now < until && now - start >= HANDOFF_TICKS;
    }

    /** 纯写入：把 ForgeData 改写为第二阶段（eattail 循环段）。UNTIL=MAX（§8.18：循环不停）。 */
    static void applyPhaseTwo(CompoundTag forgeData, long now) {
        forgeData.putString(MoreAnimationNbtKeys.ACTIVE, PHASE_TWO);
        forgeData.putLong(MoreAnimationNbtKeys.ACTIVE_START, now);
        forgeData.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, Long.MAX_VALUE);
        // priority 沿用；lockMovement 对雕像恒 false（与 StatueForgeDataMerge.fillPlayTiming 一致）
        forgeData.putBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT, false);
    }

    private static CompoundTag extractMaidNbt(BlockEntity blockEntity) {
        if (blockEntity instanceof TileEntityStatue statue && statue.isCoreBlock()) {
            return statue.getExtraMaidData();
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            return garageKit.getExtraData();
        }
        return null;
    }

    private static void sync(BlockEntity blockEntity) {
        if (blockEntity instanceof TileEntityStatue statue) {
            statue.refresh();
        } else if (blockEntity instanceof TileEntityGarageKit garageKit) {
            garageKit.setData(garageKit.getFacing(), garageKit.getExtraData());
        }
    }
}
