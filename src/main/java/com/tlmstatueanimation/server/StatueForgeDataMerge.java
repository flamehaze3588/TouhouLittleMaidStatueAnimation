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
     * 为一次性动作补齐时间窗（服务端调用）：客户端只发动作 key，
     * start/until 用 level.getGameTime() 与动作时长（MoreAnimationCompat.duration）在这里补齐。
     * 时长下限 1 刻（镜像 MaidAnimationData.start 的 Math.max(1, duration)）；
     * lockMovement 对雕像恒 false——雕像是装饰方块实体，没有可锁的移动。
     */
    public static void fillPlayTiming(CompoundTag setKeys, long gameTime, int durationTicks, int priority) {
        setKeys.putLong(MoreAnimationNbtKeys.ACTIVE_START, gameTime);
        setKeys.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, gameTime + Math.max(1, durationTicks));
        setKeys.putInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY, priority);
        setKeys.putBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT, false);
    }

    private static boolean isAllowedKey(String key) {
        return key.startsWith(MoreAnimationNbtKeys.KEY_PREFIX);
    }
}
