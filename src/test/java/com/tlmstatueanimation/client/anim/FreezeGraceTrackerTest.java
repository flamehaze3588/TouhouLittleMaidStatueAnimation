package com.tlmstatueanimation.client.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FreezeGraceTracker 状态机测试：moreanimation 活跃恒解冻；坐姿翻转与活跃下降沿
 * 各触发 GRACE_TICKS 宽限窗；窗过期后恢复冻结；首帧（未初始化）不触发宽限。
 */
class FreezeGraceTrackerTest {

    @Test
    void frozenByDefaultWhenNothingActive() {
        FreezeGraceTracker tracker = new FreezeGraceTracker();
        assertFalse(tracker.shouldUnfreeze(false, false, 1000));
        assertFalse(tracker.shouldUnfreeze(false, false, 1001));
    }

    @Test
    void activeKeepsUnfrozen() {
        FreezeGraceTracker tracker = new FreezeGraceTracker();
        assertTrue(tracker.shouldUnfreeze(false, true, 1000));
        assertTrue(tracker.shouldUnfreeze(false, true, 1001));
    }

    @Test
    void sittingFlipTriggersGraceWindow() {
        FreezeGraceTracker tracker = new FreezeGraceTracker();
        tracker.shouldUnfreeze(false, false, 1000);
        // 翻转当帧起解冻
        assertTrue(tracker.shouldUnfreeze(true, false, 1010));
        // 宽限窗内持续解冻
        assertTrue(tracker.shouldUnfreeze(true, false, 1010 + FreezeGraceTracker.GRACE_TICKS - 1));
        // 窗口结束后重新冻结
        assertFalse(tracker.shouldUnfreeze(true, false, 1010 + FreezeGraceTracker.GRACE_TICKS));
    }

    @Test
    void activeFallingEdgeTriggersGraceWindow() {
        FreezeGraceTracker tracker = new FreezeGraceTracker();
        tracker.shouldUnfreeze(false, false, 1000);
        tracker.shouldUnfreeze(false, true, 1010);
        // 一次性动作过期：下降沿触发宽限窗（让控制器过渡回 idle/sit）
        assertTrue(tracker.shouldUnfreeze(false, false, 1020));
        assertFalse(tracker.shouldUnfreeze(false, false, 1020 + FreezeGraceTracker.GRACE_TICKS));
    }

    @Test
    void firstFrameDoesNotTriggerGrace() {
        FreezeGraceTracker tracker = new FreezeGraceTracker();
        // 雕像 NBT 本身就是坐姿：首帧不应触发宽限窗（保持原有定格行为）
        assertFalse(tracker.shouldUnfreeze(true, false, 500));
        // 之后的稳定帧也保持冻结
        assertFalse(tracker.shouldUnfreeze(true, false, 501));
    }

    @Test
    void activeRisingEdgeNeedsNoGrace() {
        FreezeGraceTracker tracker = new FreezeGraceTracker();
        tracker.shouldUnfreeze(false, false, 1000);
        // 动作开始：active=true 本身就解冻
        assertTrue(tracker.shouldUnfreeze(false, true, 1010));
    }
}
