package com.tlmstatueanimation.server;

import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueForgeDataMerge 纯函数测试（ForgeData 合并/删除/一次性动作时间窗补齐）。
 */
class StatueForgeDataMergeTest {

    private static CompoundTag forgeDataOf(CompoundTag maidNbt) {
        return maidNbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA);
    }

    @Test
    void applyCreatesForgeDataWhenAbsent() {
        CompoundTag maidNbt = new CompoundTag();
        CompoundTag setKeys = new CompoundTag();
        setKeys.putString(MoreAnimationNbtKeys.EXPRESSION, "lips");

        StatueForgeDataMerge.apply(maidNbt, setKeys, List.of());

        assertTrue(maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA, Tag.TAG_COMPOUND));
        assertEquals("lips", forgeDataOf(maidNbt).getString(MoreAnimationNbtKeys.EXPRESSION));
    }

    @Test
    void applyMergesPreservingExistingKeys() {
        CompoundTag maidNbt = new CompoundTag();
        CompoundTag forgeData = new CompoundTag();
        forgeData.putString(MoreAnimationNbtKeys.EXPRESSION, "wuyu");
        forgeData.putInt("other_mod_key", 42);
        maidNbt.put(MoreAnimationNbtKeys.FORGE_DATA, forgeData);

        CompoundTag setKeys = new CompoundTag();
        setKeys.putString(MoreAnimationNbtKeys.EXPRESSION, "sosad");
        StatueForgeDataMerge.apply(maidNbt, setKeys, List.of());

        assertEquals("sosad", forgeDataOf(maidNbt).getString(MoreAnimationNbtKeys.EXPRESSION));
        // ForgeData 内既有键（含非 moreanimation_ 键）不受影响
        assertEquals(42, forgeDataOf(maidNbt).getInt("other_mod_key"));
    }

    @Test
    void applyRemovesKeys() {
        CompoundTag maidNbt = new CompoundTag();
        CompoundTag forgeData = new CompoundTag();
        forgeData.putString(MoreAnimationNbtKeys.EXPRESSION, "lips");
        forgeData.putString(MoreAnimationNbtKeys.ACTIVE, "circledance");
        maidNbt.put(MoreAnimationNbtKeys.FORGE_DATA, forgeData);

        StatueForgeDataMerge.apply(maidNbt, new CompoundTag(), List.of(MoreAnimationNbtKeys.EXPRESSION));

        assertFalse(forgeDataOf(maidNbt).contains(MoreAnimationNbtKeys.EXPRESSION));
        assertEquals("circledance", forgeDataOf(maidNbt).getString(MoreAnimationNbtKeys.ACTIVE));
    }

    @Test
    void removeOnlyWithoutForgeDataDoesNotCreateShell() {
        CompoundTag maidNbt = new CompoundTag();

        StatueForgeDataMerge.apply(maidNbt, new CompoundTag(), List.of(MoreAnimationNbtKeys.EXPRESSION));

        // 仅删除且 ForgeData 不存在时不得创建空壳
        assertFalse(maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA));
    }

    @Test
    void applyFiltersNonMoreAnimationKeys() {
        CompoundTag maidNbt = new CompoundTag();
        CompoundTag forgeData = new CompoundTag();
        forgeData.putInt("other_mod_key", 7);
        maidNbt.put(MoreAnimationNbtKeys.FORGE_DATA, forgeData);

        CompoundTag setKeys = new CompoundTag();
        setKeys.putInt("evil_key", 1);
        setKeys.putString(MoreAnimationNbtKeys.EXPRESSION, "kuang");
        StatueForgeDataMerge.apply(maidNbt, setKeys, List.of("other_mod_key", "also_evil"));

        // 非 moreanimation_ 前缀的写入与删除都被过滤
        assertFalse(forgeDataOf(maidNbt).contains("evil_key"));
        assertEquals(7, forgeDataOf(maidNbt).getInt("other_mod_key"));
        assertEquals("kuang", forgeDataOf(maidNbt).getString(MoreAnimationNbtKeys.EXPRESSION));
    }

    @Test
    void applyCopiesValues() {
        CompoundTag maidNbt = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putString("inner", "before");
        CompoundTag setKeys = new CompoundTag();
        setKeys.put(MoreAnimationNbtKeys.EXPRESSION, nested);

        StatueForgeDataMerge.apply(maidNbt, setKeys, List.of());
        // 合并后修改源 tag，NBT 中的副本不得变化（深拷贝语义）
        nested.putString("inner", "after");

        assertEquals("before", forgeDataOf(maidNbt).getCompound(MoreAnimationNbtKeys.EXPRESSION).getString("inner"));
    }

    @Test
    void fillPlayTimingWritesInfiniteWindow() {
        // §8.18：雕像动作无限循环——UNTIL 恒为 Long.MAX_VALUE，停止靠控制屏/蹲下切换
        CompoundTag setKeys = new CompoundTag();
        StatueForgeDataMerge.fillPlayTiming(setKeys, 1000L, 20);

        assertEquals(1000L, setKeys.getLong(MoreAnimationNbtKeys.ACTIVE_START));
        assertEquals(Long.MAX_VALUE, setKeys.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL));
        assertEquals(20, setKeys.getInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY));
        // 锁移动对雕像恒 false
        assertFalse(setKeys.getBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT));
    }

    @Test
    void playKeysPlusTimingThenMergeYieldsFullActiveState() {
        // 模拟 handler 的完整写入路径：动作 key + 时间窗 → 合并进 ForgeData
        CompoundTag maidNbt = new CompoundTag();
        CompoundTag setKeys = new CompoundTag();
        setKeys.putString(MoreAnimationNbtKeys.ACTIVE, "circledance");
        StatueForgeDataMerge.fillPlayTiming(setKeys, 2000L, 20);
        StatueForgeDataMerge.apply(maidNbt, setKeys, List.of());

        CompoundTag forgeData = forgeDataOf(maidNbt);
        assertEquals("circledance", forgeData.getString(MoreAnimationNbtKeys.ACTIVE));
        assertEquals(2000L, forgeData.getLong(MoreAnimationNbtKeys.ACTIVE_START));
        assertEquals(Long.MAX_VALUE, forgeData.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL));
        assertEquals(20, forgeData.getInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY));
        assertFalse(forgeData.getBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT));
    }

    @Test
    void applySupportsListTagValues() {
        CompoundTag maidNbt = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("circledance"));
        enabled.add(StringTag.valueOf("!??!"));
        CompoundTag setKeys = new CompoundTag();
        setKeys.put(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", enabled);

        StatueForgeDataMerge.apply(maidNbt, setKeys, List.of());

        ListTag merged = forgeDataOf(maidNbt).getList(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", Tag.TAG_STRING);
        assertEquals(2, merged.size());
        assertEquals("circledance", merged.getString(0));
        assertEquals("!??!", merged.getString(1));
    }
}
