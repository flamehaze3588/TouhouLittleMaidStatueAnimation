package com.tlmstatueanimation.server;

import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * 雕像/手办女仆 NBT 的 ForgeData（moreanimation 状态区）写入助手（纯函数，可单测）。
 * NBT 为引用语义：就地修改后由方块实体侧的 refresh()/setData() 触发同步（与 StatueMaidNbt 同模式）。
 * 安全约束：只放行 {@code moreanimation_} 前缀的键，防御伪造包改写雕像 NBT 中其他 mod 的数据。
 */
public final class StatueForgeDataMerge {

    private StatueForgeDataMerge() {
    }

    /**
     * 把 setKeys 合并进 maidNbt 的 "ForgeData" 子标签（不存在且有可写键时新建），再删除 removeKeys。
     * 合并的值做深拷贝，调用方持有的 setKeys 后续改动不影响 NBT。
     * 仅删除且 ForgeData 不存在时不创建空壳（无意义写入会扩大 NBT）。
     */
    public static void apply(CompoundTag maidNbt, CompoundTag setKeys, List<String> removeKeys) {
        CompoundTag forgeData;
        if (maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA, Tag.TAG_COMPOUND)) {
            forgeData = maidNbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA);
        } else {
            if (setKeys.isEmpty()) {
                return;
            }
            forgeData = new CompoundTag();
            maidNbt.put(MoreAnimationNbtKeys.FORGE_DATA, forgeData);
        }
        for (String key : setKeys.getAllKeys()) {
            if (!isAllowedKey(key)) {
                continue;
            }
            Tag value = setKeys.get(key);
            if (value != null) {
                forgeData.put(key, value.copy());
            }
        }
        for (String key : removeKeys) {
            if (isAllowedKey(key)) {
                forgeData.remove(key);
            }
        }
    }

    /**
     * 为动作补齐时间窗（服务端调用）：客户端只发动作 key，start 用 level.getGameTime() 补齐。
     * UNTIL 恒为 Long.MAX_VALUE（§8.18）：moreanimation 的动作在活跃期内本就按其
     * isLoopingAction 循环播放，结束只因 until 过期；雕像作为装饰应让动作保持循环，
     * 停止靠控制屏"停止"按钮（清空 ACTIVE* 键）或蹲下切换姿势。
     * lockMovement 对雕像恒 false——雕像是装饰方块实体，没有可锁的移动。
     */
    public static void fillPlayTiming(CompoundTag setKeys, long gameTime, int priority) {
        setKeys.putLong(MoreAnimationNbtKeys.ACTIVE_START, gameTime);
        setKeys.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, Long.MAX_VALUE);
        setKeys.putInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY, priority);
        setKeys.putBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT, false);
    }

    private static boolean isAllowedKey(String key) {
        return key.startsWith(MoreAnimationNbtKeys.KEY_PREFIX);
    }
}
