package com.tlmstatueanimation.server;

import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueActionPhaseTicker 的 tastetail→eattail 接力逻辑测试（镜像 moreanimation
 * MaidAnimationData.serverTick 的硬编码参数：45 tick 后接力，eattail 续 100 tick）。
 */
class StatueActionPhaseTickerTest {

    @Test
    void handoffOnlyForTastetail() {
        assertFalse(StatueActionPhaseTicker.needsHandoff("eattail", 1000, 1200, 1046));
        assertFalse(StatueActionPhaseTicker.needsHandoff("circledance", 1000, 1200, 1046));
        assertFalse(StatueActionPhaseTicker.needsHandoff("", 1000, 1200, 1046));
    }

    @Test
    void handoffAtExactBoundary() {
        // 44 tick：未到接力点
        assertFalse(StatueActionPhaseTicker.needsHandoff("tastetail", 1000, 1200, 1044));
        // 45 tick：恰好接力
        assertTrue(StatueActionPhaseTicker.needsHandoff("tastetail", 1000, 1200, 1045));
    }

    @Test
    void noHandoffAfterExpiry() {
        // now >= until：已过期，不接力（由客户端宽限窗回落姿势）
        assertFalse(StatueActionPhaseTicker.needsHandoff("tastetail", 1000, 1040, 1050));
    }

    @Test
    void applyPhaseTwoWritesEattailWindow() {
        CompoundTag forgeData = new CompoundTag();
        forgeData.putString(MoreAnimationNbtKeys.ACTIVE, "tastetail");
        forgeData.putLong(MoreAnimationNbtKeys.ACTIVE_START, 1000);
        forgeData.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, 1100);
        forgeData.putInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY, 20);
        StatueActionPhaseTicker.applyPhaseTwo(forgeData, 1045);
        assertEquals("eattail", forgeData.getString(MoreAnimationNbtKeys.ACTIVE));
        assertEquals(1045, forgeData.getLong(MoreAnimationNbtKeys.ACTIVE_START));
        assertEquals(1145, forgeData.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL));
        // priority 沿用、lockMovement 恒 false
        assertEquals(20, forgeData.getInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY));
        assertFalse(forgeData.getBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT));
    }
}
