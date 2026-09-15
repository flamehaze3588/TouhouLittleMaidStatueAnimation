package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.nbt.CompoundTag;

/**
 * 雕像/手办站姿 ↔ 坐姿切换助手（纯函数，可单测）。
 * "Sitting" 是原版 TamableAnimal 的持久化键（TLM 女仆继承之）：实体 load 时据此恢复
 * 坐下姿势（orderedToSit + inSittingPose），TLM bedrock 渲染与 YSM 联动均消费该状态。
 * 雕像 NBT 是完整实体快照，翻转此布尔值即可切换姿势；键缺失时 getBoolean 返回 false（视为站立）。
 * NBT 为引用语义：就地修改后由方块实体侧的 refresh()/setData() 触发同步（与 StatueMaidNbt 同模式）。
 */
public final class StatueSitToggle {

    private StatueSitToggle() {
    }

    /**
     * 翻转 maidNbt 根部的 Sitting 状态。
     *
     * @return 翻转后的状态（true = 坐姿）
     */
    public static boolean toggle(CompoundTag maidNbt) {
        boolean nowSitting = !maidNbt.getBoolean(MaidNbtTags.SITTING);
        maidNbt.putBoolean(MaidNbtTags.SITTING, nowSitting);
        return nowSitting;
    }

    /**
     * 翻转坐姿并打上"姿势交互"标记（§8.17）：YSM 模型的 renderState==STATUE 会强制播放
     * 模型内置 statue 姿势（站姿烘焙剪辑），与坐姿叠加会出现"站姿下半身入地"；
     * 标记后渲染器 mixin 让其走 ENTITY 渲染链（普通站姿/坐姿），不再回到内置雕像姿势。
     *
     * @return 翻转后的状态（true = 坐姿）
     */
    public static boolean toggleInteractive(CompoundTag maidNbt) {
        maidNbt.putBoolean(MaidNbtTags.STATUE_POSE_INTERACTIVE, true);
        return toggle(maidNbt);
    }
}
