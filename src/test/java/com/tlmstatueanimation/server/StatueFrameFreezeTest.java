package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueFrameFreeze 定格切换与时移测试（§8.23）。
 */
class StatueFrameFreezeTest {

    @Test
    void freezeThenUnfreeze() {
        CompoundTag nbt = new CompoundTag();
        assertFalse(StatueFrameFreeze.isFrozen(nbt));
        // 解冻驱动下定格：freezeTick 记 gameTime
        assertTrue(StatueFrameFreeze.toggle(nbt, 5000, true));
        assertTrue(StatueFrameFreeze.isFrozen(nbt));
        assertEquals(5000L, nbt.getLong(MaidNbtTags.STATUE_FREEZE_AT));
        assertEquals(5000, nbt.getInt(MaidNbtTags.STATUE_FREEZE_TICK));
        // 解除：两键清除
        assertFalse(StatueFrameFreeze.toggle(nbt, 6000, true));
        assertFalse(StatueFrameFreeze.isFrozen(nbt));
        assertFalse(nbt.contains(MaidNbtTags.STATUE_FREEZE_TICK));
    }

    @Test
    void frozenStatueRecordsZeroTick() {
        // 定格前已冻结（非 YSM 且无 moreanimation 活跃）：freezeTick=0
        CompoundTag nbt = new CompoundTag();
        StatueFrameFreeze.toggle(nbt, 7000, false);
        assertEquals(0, nbt.getInt(MaidNbtTags.STATUE_FREEZE_TICK));
    }

    @Test
    void frozenActiveStartKeepsElapsedConstant() {
        // elapsed = now - frozenActiveStart = freezeAt - originalStart（恒定）
        long originalStart = 1000;
        long freezeAt = 5000;
        long frozen = StatueFrameFreeze.frozenActiveStart(originalStart, 8000, freezeAt);
        assertEquals(freezeAt - originalStart, 8000 - frozen);
        long frozen2 = StatueFrameFreeze.frozenActiveStart(originalStart, 9000, freezeAt);
        assertEquals(freezeAt - originalStart, 9000 - frozen2);
    }
}
