package com.tlmstatueanimation.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.client.anim.FreezeGraceTracker;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.WeakHashMap;

/**
 * 雕像动画冻结控制的适配层（§8.14）：把 MC 对象（假女仆实体 / NBT / 世界时钟）
 * 翻译成 {@link FreezeGraceTracker} 的纯布尔/时间参数，并按实体持有状态机实例。
 * 仅在两个渲染器 mixin 的解冻判定（isYsmModel 重定向）中每帧调用一次。
 * WeakHashMap 键：假女仆由 TLM 的 STATUE_CACHE 持有（10 秒过期），键失效后自动回收。
 */
public final class StatueAnimFreezeControl {
    private static final WeakHashMap<EntityMaid, FreezeGraceTracker> TRACKERS = new WeakHashMap<>();

    private StatueAnimFreezeControl() {
    }

    /**
     * 本帧该雕像是否应解冻（非 YSM 模型才会走到这里；YSM 模型 TLM 本来就恒解冻）。
     * moreanimation 活跃恒解冻：表情非空 / 一次性动作未过期（含 sit2 基础姿势——
     * 其 UNTIL=Long.MAX_VALUE 天然命中此判定，§8.16）；坐姿翻转或活跃下降沿触发宽限窗。
     */
    public static boolean shouldUnfreeze(EntityMaid maid, CompoundTag data, Level world) {
        long now = world.getGameTime();
        boolean active = isMoreAnimationActive(maid.getPersistentData(), now);
        boolean sitting = data.getBoolean(MaidNbtTags.SITTING);
        return TRACKERS.computeIfAbsent(maid, k -> new FreezeGraceTracker()).shouldUnfreeze(sitting, active, now);
    }

    /**
     * moreanimation 活跃判定（§8.12/§8.18）：表情非空，或 ACTIVE 键存在且未过期
     * （UNTIL=Long.MAX_VALUE 的无限动作/基础姿势恒活跃）。渲染器解冻与 YSM ctrl 防护共用。
     */
    public static boolean isMoreAnimationActive(CompoundTag persistentData, long now) {
        return !persistentData.getString(MoreAnimationNbtKeys.EXPRESSION).isEmpty()
                || isActionActive(persistentData, now);
    }

    /** 是否有未过期的一次性/持续动作（不含表情；§8.19 的随机 idle 抑制判定用）。纯函数，可单测。 */
    public static boolean isActionActive(CompoundTag persistentData, long now) {
        return persistentData.contains(MoreAnimationNbtKeys.ACTIVE)
                && persistentData.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL) > now;
    }
}
