package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueBasePose 坐姿变体测试（§8.16，ACTIVE 通道版）：
 * 抽中 sit2 = 记忆标记 + ACTIVE=sit2/UNTIL=MAX；未抽中与起身 = 清除；
 * restoreBasePose 在一次性动作过期后按记忆标记+坐姿恢复。
 */
class StatueBasePoseTest {

    private static CompoundTag forgeDataOf(CompoundTag nbt) {
        return nbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA);
    }

    @Test
    void winningRollWritesMarkerAndPersistentAction() {
        CompoundTag nbt = new CompoundTag();
        StatueBasePose.roll(nbt, true, true, 5000);
        CompoundTag fd = forgeDataOf(nbt);
        assertEquals("sit2", fd.getString(MoreAnimationNbtKeys.STATUE_BASE_POSE));
        assertEquals("sit2", fd.getString(MoreAnimationNbtKeys.ACTIVE));
        assertEquals(5000, fd.getLong(MoreAnimationNbtKeys.ACTIVE_START));
        assertEquals(Long.MAX_VALUE, fd.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL));
        assertEquals(10, fd.getInt(MoreAnimationNbtKeys.ACTIVE_PRIORITY));
        assertFalse(fd.getBoolean(MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT));
    }

    @Test
    void losingRollKeepsDefaultSit() {
        CompoundTag nbt = new CompoundTag();
        StatueBasePose.roll(nbt, true, false, 5000);
        assertFalse(nbt.contains(MoreAnimationNbtKeys.FORGE_DATA), "默认坐姿不应创建 ForgeData");
    }

    @Test
    void standingClearsMarkerAndSit2Action() {
        CompoundTag nbt = new CompoundTag();
        StatueBasePose.roll(nbt, true, true, 5000);
        StatueBasePose.roll(nbt, false, true, 6000);
        CompoundTag fd = forgeDataOf(nbt);
        assertEquals("", fd.getString(MoreAnimationNbtKeys.STATUE_BASE_POSE));
        assertEquals("", fd.getString(MoreAnimationNbtKeys.ACTIVE));
        assertFalse(fd.contains(MoreAnimationNbtKeys.ACTIVE_UNTIL));
    }

    @Test
    void standingClearsAnyInfiniteLoopButKeepsFiniteAction() {
        CompoundTag nbt = new CompoundTag();
        StatueBasePose.roll(nbt, true, true, 5000);
        // 模拟控制屏播放的无限循环动作（§8.18：UNTIL=MAX）
        CompoundTag fd = forgeDataOf(nbt);
        fd.putString(MoreAnimationNbtKeys.ACTIVE, "ha");
        // until 已是 MAX（roll 写入的），无限循环动作应被姿势切换停止
        StatueBasePose.roll(nbt, false, false, 6000);
        assertEquals("", fd.getString(MoreAnimationNbtKeys.ACTIVE), "无限循环动作应随姿势切换停止");
        assertEquals("", fd.getString(MoreAnimationNbtKeys.STATUE_BASE_POSE), "记忆标记仍应清除");

        // 有限时长的 ACTIVE（旧存档残留）不受影响
        CompoundTag nbt2 = new CompoundTag();
        StatueBasePose.roll(nbt2, true, true, 5000);
        CompoundTag fd2 = forgeDataOf(nbt2);
        fd2.putString(MoreAnimationNbtKeys.ACTIVE, "ha");
        fd2.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, 6100);
        StatueBasePose.roll(nbt2, false, false, 6000);
        assertEquals("ha", fd2.getString(MoreAnimationNbtKeys.ACTIVE), "有限时长动作不应被清除");
    }

    @Test
    void restoreBringsBackSit2WhenSitting() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean(MaidNbtTags.SITTING, true);
        StatueBasePose.roll(nbt, true, true, 5000);
        // 模拟一次性动作覆盖后过期
        CompoundTag fd = forgeDataOf(nbt);
        fd.putString(MoreAnimationNbtKeys.ACTIVE, "ha");
        assertTrue(StatueBasePose.restoreBasePose(nbt, 7000));
        assertEquals("sit2", fd.getString(MoreAnimationNbtKeys.ACTIVE));
        assertEquals(Long.MAX_VALUE, fd.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL));
    }

    @Test
    void restoreDoesNothingWhenStandingOrUnmarked() {
        CompoundTag nbt = new CompoundTag();
        StatueBasePose.roll(nbt, true, true, 5000);
        assertFalse(StatueBasePose.restoreBasePose(nbt, 7000), "未坐下（Sitting=false）不恢复");
        CompoundTag plain = new CompoundTag();
        plain.putBoolean(MaidNbtTags.SITTING, true);
        assertFalse(StatueBasePose.restoreBasePose(plain, 7000), "无记忆标记不恢复");
    }
}
