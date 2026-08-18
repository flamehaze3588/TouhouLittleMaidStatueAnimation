package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.nbt.CompoundTag;

/**
 * 雕像/手办女仆 NBT 的轮盘动作写入助手（D5，纯函数，可单测）。
 * NBT 为引用语义：就地修改后由方块实体侧的 refresh()/setData() 触发同步。
 */
public final class StatueMaidNbt {

    private StatueMaidNbt() {
    }

    /** 播放：写入动作 key 并置播放标志。 */
    public static void writePlay(CompoundTag maidNbt, String animKey) {
        maidNbt.putString(MaidNbtTags.YSM_ROULETTE_ANIM, animKey);
        maidNbt.putBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING, true);
    }

    /** 停止：仅清播放标志，保留动作 key（TLM 停止语义：playing=false 即停止，见文档 §6-c）。 */
    public static void writeStop(CompoundTag maidNbt) {
        maidNbt.putBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING, false);
    }

    /** 该女仆 NBT 是否为 YSM 模型（缺 tag 时 getBoolean 返回 false，安全）。 */
    public static boolean isYsmMaid(CompoundTag maidNbt) {
        return maidNbt.getBoolean(MaidNbtTags.IS_YSM_MODEL);
    }
}
