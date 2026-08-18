package com.tlmstatueanimation.client.anim;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.TlmStatueAnimation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 客户端动画施加器（D6）：把雕像方块实体 NBT 中的轮盘动作状态
 * 施加到 TLM 渲染缓存的假 EntityMaid 上，YSM 接管渲染器据此播放动作。
 * <p>
 * 遍历方式选择：直接遍历 STATUE_CACHE.asMap() 的 key（方案 a）。
 * 理由：缓存 key 就是"正在被渲染的雕像集合"，天然过滤了视野外的雕像，
 * 无需自己维护雕像集合；渲染器每帧 access 缓存，玩家看着雕像时不会过期。
 */
@Mod.EventBusSubscriber(modid = TlmStatueAnimation.MOD_ID, value = Dist.CLIENT)
public final class StatueAnimationApplier {
    private static final Long2ObjectMap<Applied> LAST_APPLIED = new Long2ObjectOpenHashMap<>();

    private StatueAnimationApplier() {
    }

    private record Applied(@Nullable String key, boolean playing) {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LAST_APPLIED.clear();
            return;
        }
        Map<Long, EntityMaid> cache = EntityCacheUtil.STATUE_CACHE.asMap();
        for (Map.Entry<Long, EntityMaid> entry : cache.entrySet()) {
            EntityMaid maid = entry.getValue();
            if (maid == null || !maid.isYsmModel()) {
                continue;
            }
            CompoundTag maidNbt = readMaidNbt(mc.level, BlockPos.of(entry.getKey()));
            if (maidNbt == null) {
                continue;
            }
            Applied last = LAST_APPLIED.get(entry.getKey());
            AnimationDecision.Decision decision = AnimationDecision.decide(
                    maidNbt.getString(MaidNbtTags.YSM_ROULETTE_ANIM),
                    maidNbt.getBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING),
                    last == null ? null : last.key(), last != null && last.playing());
            switch (decision.action()) {
                case PLAY -> maid.playRouletteAnim(decision.animKey());
                case STOP -> maid.stopRouletteAnim();
                default -> {
                }
            }
            LAST_APPLIED.put(entry.getKey(), new Applied(decision.animKey(), decision.playing()));
        }
        // 缓存中已消失（10 秒无访问过期/区块卸载）的坐标同步逐出
        LAST_APPLIED.keySet().removeIf(key -> !cache.containsKey(key));
    }

    /**
     * 读取缓存坐标对应雕像/手办的女仆 NBT。
     * 防御性处理：雕像非核心块经 getCoreBlockPos() 重定位（正常情况缓存 key 即核心块）。
     */
    @Nullable
    private static CompoundTag readMaidNbt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileEntityStatue statue) {
            if (!statue.isCoreBlock()) {
                BlockEntity core = level.getBlockEntity(statue.getCoreBlockPos());
                if (core instanceof TileEntityStatue coreStatue) {
                    return coreStatue.getExtraMaidData();
                }
                return null;
            }
            return statue.getExtraMaidData();
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            return garageKit.getExtraData();
        }
        return null;
    }
}
