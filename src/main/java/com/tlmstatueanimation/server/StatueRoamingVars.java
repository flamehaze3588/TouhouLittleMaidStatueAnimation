package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.client.model.RoamingAssignments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 雕像女仆 NBT 的 YSM 漫游变量写入助手（§8.22，纯函数，可单测）。
 * 漫游变量 = 模型配置项（显示/隐藏部件等）的 molang 变量，TLM 落盘于 "YsmRoamingVars" 子标签，
 * 渲染时经 load() 灌进假女仆 roamingVars 供 YSM molang 读取。
 * NBT 为引用语义：就地修改后由方块实体侧的 refresh()/setData() 触发同步。
 */
public final class StatueRoamingVars {

    private StatueRoamingVars() {
    }

    /**
     * 写入一个漫游变量（变量名合法性 + 值有限性校验，镜像 YSM RoamingStruct 约束）。
     * 同步 bump YsmRoamingUpdateFlag（TLM 的漫游变量更新计数语义）。
     *
     * @return false = 校验失败（未写入）
     */
    public static boolean setVar(CompoundTag maidNbt, String varName, float value) {
        if (!RoamingAssignments.isValidVarName(varName) || !Float.isFinite(value)) {
            return false;
        }
        CompoundTag vars;
        if (maidNbt.contains(MaidNbtTags.YSM_ROAMING_VARS, Tag.TAG_COMPOUND)) {
            vars = maidNbt.getCompound(MaidNbtTags.YSM_ROAMING_VARS);
        } else {
            vars = new CompoundTag();
            maidNbt.put(MaidNbtTags.YSM_ROAMING_VARS, vars);
        }
        vars.putFloat(varName, value);
        maidNbt.putInt(MaidNbtTags.YSM_ROAMING_UPDATE_FLAG, maidNbt.getInt(MaidNbtTags.YSM_ROAMING_UPDATE_FLAG) + 1);
        return true;
    }
}
