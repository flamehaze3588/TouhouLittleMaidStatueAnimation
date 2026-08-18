package com.tlmstatueanimation.client.targeting;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 准星命中的雕像/手办引用（D3）。
 *
 * @param corePos 核心块坐标（手办为单方块，即自身坐标）
 * @param kind    雕像或手办
 * @param maidNbt 核心块（或手办）上的女仆 extra NBT，已通过 YSM 过滤
 */
public record StatueRef(BlockPos corePos, Kind kind, CompoundTag maidNbt) {

    public enum Kind {
        STATUE, GARAGE_KIT
    }
}
