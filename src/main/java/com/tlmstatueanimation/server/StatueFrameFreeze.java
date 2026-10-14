package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 雕像相机定格（§8.23，纯函数，可单测）。
 * 定格 = 把当前动作帧冻住而非回到第 0 帧，两条时间轴分别处理：
 * <ul>
 *   <li>gecko/YSM 自主动画：动画时间 = tickCount + partialTick，写入 StatueFreezeTick 后每帧
 *       把 tickCount 钉在该值即定格当前帧；</li>
 *   <li>moreanimation 的 YSM 桥：进度 = (now - ACTIVE_START)/20，让 ACTIVE_START 随 now
 *       等量前移（frozenActiveStart），即保持进度恒定。</li>
 * </ul>
 */
public final class StatueFrameFreeze {

    private StatueFrameFreeze() {
    }

    public static boolean isFrozen(CompoundTag maidNbt) {
        return maidNbt.contains(MaidNbtTags.STATUE_FREEZE_AT, Tag.TAG_LONG);
    }

    /**
     * 切换定格状态。
     *
     * @param currentlyUnfrozen 定格前是否处于解冻驱动（YSM 模型或 moreanimation 活跃），
     *                          决定 freezeTick 记 gameTime 还是 0（保持连续性）
     * @return 切换后是否定格（true = 定格，false = 解除定格）
     */
    public static boolean toggle(CompoundTag maidNbt, long gameTime, boolean currentlyUnfrozen) {
        if (isFrozen(maidNbt)) {
            maidNbt.remove(MaidNbtTags.STATUE_FREEZE_AT);
            maidNbt.remove(MaidNbtTags.STATUE_FREEZE_TICK);
            return false;
        }
        maidNbt.putLong(MaidNbtTags.STATUE_FREEZE_AT, gameTime);
        maidNbt.putInt(MaidNbtTags.STATUE_FREEZE_TICK, currentlyUnfrozen ? (int) gameTime : 0);
        return true;
    }

    /**
     * moreanimation YSM 桥的 ACTIVE_START 时移：
     * 令 now - frozenActiveStart = freezeAt - originalStart（恒定），即冻结在定格瞬间的进度。
     * 解除定格后 NBT 原值恢复，动作按原时间线继续（循环动作相位跳一段，可接受）。
     */
    public static long frozenActiveStart(long originalStart, long now, long freezeAt) {
        return originalStart + now - freezeAt;
    }
}
