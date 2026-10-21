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
    /** 假女仆首次出现的 gameTime（重进存档后 YSM 新生 animatable 可能吞掉首个 dirty 沿，前 3 秒每秒补一次边沿） */
    private static final Long2ObjectMap<Long> FIRST_SEEN_TICK = new Long2ObjectOpenHashMap<>();
    /** 上一帧的客户端世界实例：检测退出存档/换维度（实例切换） */
    private static Level lastLevel;

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
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            LAST_APPLIED.clear();
            FIRST_SEEN_TICK.clear();
            if (mc.level != null) {
                // 热重进/换维度：TLM 的 STATUE_CACHE 是 10 秒访问过期的进程级缓存，缓存里的假女仆
                // 还绑定着已卸载的旧世界——实测此时 YSM 不再驱动它的 animatable（dirty 旗标永不
                // 被消费，雕像定格在最后姿势）。清缓存强制按新世界重建。
                EntityCacheUtil.STATUE_CACHE.invalidateAll();
                EntityCacheUtil.GARAGE_KIT_CACHE.invalidateAll();
            }
        }
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
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
            // 假女仆出现后前 3 秒内每秒重补一次播放边沿：重进存档时 YSM 的 animatable 是新建的，
            // 首个 dirty 边沿可能被初始化时序吞掉（实测：不重补则重进后动作不播）
            if (decision.playing() && decision.animKey() != null) {
                long firstSeen = FIRST_SEEN_TICK.computeIfAbsent(entry.getKey(), k -> now);
                if (now - firstSeen < 60 && now % 20 == 0) {
                    maid.playRouletteAnim(decision.animKey());
                }
            }
            LAST_APPLIED.put(entry.getKey(), new Applied(decision.animKey(), decision.playing()));
        }
        // 缓存中已消失（10 秒无访问过期/区块卸载）的坐标同步逐出
        LAST_APPLIED.keySet().removeIf(key -> !cache.containsKey(key));
        FIRST_SEEN_TICK.keySet().removeIf(key -> !cache.containsKey(key));
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
