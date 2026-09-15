package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 雕像坐姿变体（sit2）助手（纯函数，可单测，§8.16）。
 * 镜像 moreanimation 对真女仆的语义（GameLostAnimation.selectedBasePoseAction）：
 * 进入坐姿时从 [默认坐姿, sit2] 随机抽一次；抽中 sit2 则作为持续基础姿势循环播放。
 * <p>
 * 播放通道：原生 ACTIVE 机制（ACTIVE="sit2"、UNTIL=Long.MAX_VALUE 表示不过期）——
 * gecko 路径由 moreanimation 的 AnimationManagerMixin 谓词播放（sit2 是 LOOPING_ACTION），
 * YSM 路径由 moreanimation 的 YsmAnimationBridge 读 activeAction 应用剪辑，两条路通吃。
 * STATUE_BASE_POSE 键仅作"记忆标记"：一次性动作（ha/tastetail 等）覆盖 ACTIVE 后，
 * 过期时由 StatueActionPhaseTicker 据此恢复基础姿势。
 */
public final class StatueBasePose {

    private StatueBasePose() {
    }

    /**
     * 按切换后的坐姿状态掷签（C2SToggleStatueSitPacket 调用）。
     *
     * @param maidNbt    雕像女仆 NBT（就地修改其 ForgeData 子标签）
     * @param nowSitting 切换后的坐姿（true = 坐下）
     * @param rollSit2   服务端随机结果（true = 抽中 sit2）
     * @param now        服务端游戏刻
     */
    public static void roll(CompoundTag maidNbt, boolean nowSitting, boolean rollSit2, long now) {
        if (!maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA, Tag.TAG_COMPOUND)) {
            if (!nowSitting || !rollSit2) {
                return; // 默认坐姿/站姿均无键可写，不创建空壳
            }
            maidNbt.put(MoreAnimationNbtKeys.FORGE_DATA, new CompoundTag());
        }
        CompoundTag forgeData = maidNbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA);
        if (nowSitting && rollSit2) {
            forgeData.putString(MoreAnimationNbtKeys.STATUE_BASE_POSE, MoreAnimationNbtKeys.SIT_VARIANT_SIT2);
            activateSit2(forgeData, now);
        } else {
            forgeData.remove(MoreAnimationNbtKeys.STATUE_BASE_POSE);
            clearSit2Action(forgeData);
        }
    }

    /**
     * 一次性动作过期/被清除后恢复基础姿势（StatueActionPhaseTicker 调用）。
     *
     * @return 是否发生了恢复写入（true = 调用方应触发 TE 同步）
     */
    public static boolean restoreBasePose(CompoundTag maidNbt, long now) {
        if (!maidNbt.getBoolean(MaidNbtTags.SITTING)
                || !maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag forgeData = maidNbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA);
        if (!MoreAnimationNbtKeys.SIT_VARIANT_SIT2.equals(forgeData.getString(MoreAnimationNbtKeys.STATUE_BASE_POSE))) {
            return false;
        }
        activateSit2(forgeData, now);
        return true;
    }

    /** 激活 sit2 为永不过期的基础姿势动作（低优先级，可被任何一次性动作覆盖）。 */
    static void activateSit2(CompoundTag forgeData, long now) {
        forgeData.putString(MoreAnimationNbtKeys.ACTIVE, MoreAnimationNbtKeys.SIT_VARIANT_SIT2);
        forgeData.putLong(MoreAnimationNbtKeys.ACTIVE_START, now);
        forgeData.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, Long.MAX_VALUE);
        forgeData.putInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY, MoreAnimationNbtKeys.PRIORITY_RANDOM);
        forgeData.putBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT, false);
    }

    /** 仅当当前 ACTIVE 是基础姿势 sit2 时清除动作键（播放中的一次性动作不受影响）。 */
    static void clearSit2Action(CompoundTag forgeData) {
        if (!MoreAnimationNbtKeys.SIT_VARIANT_SIT2.equals(forgeData.getString(MoreAnimationNbtKeys.ACTIVE))) {
            return;
        }
        forgeData.remove(MoreAnimationNbtKeys.ACTIVE);
        forgeData.remove(MoreAnimationNbtKeys.ACTIVE_START);
        forgeData.remove(MoreAnimationNbtKeys.ACTIVE_UNTIL);
        forgeData.remove(MoreAnimationNbtKeys.ACTIVE_PRIORITY);
        forgeData.remove(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT);
    }
}
