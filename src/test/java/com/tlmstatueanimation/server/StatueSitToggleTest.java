package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueSitToggle 纯函数测试：Sitting 键缺失视为站立、双向翻转、不动其他 tag。
 */
class StatueSitToggleTest {

    @Test
    void togglesAbsentToSitting() {
        CompoundTag nbt = new CompoundTag();
        assertTrue(StatueSitToggle.toggle(nbt), "absent Sitting must toggle to true");
        assertTrue(nbt.getBoolean(MaidNbtTags.SITTING));
    }

    @Test
    void togglesSittingBackToStanding() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean(MaidNbtTags.SITTING, true);
        assertFalse(StatueSitToggle.toggle(nbt), "sitting=true must toggle to false");
        assertFalse(nbt.getBoolean(MaidNbtTags.SITTING));
    }

    @Test
    void preservesOtherTags() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(MaidNbtTags.YSM_MODEL_ID, "ysm:default");
        nbt.putBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING, true);
        StatueSitToggle.toggle(nbt);
        assertEquals("ysm:default", nbt.getString(MaidNbtTags.YSM_MODEL_ID));
        assertTrue(nbt.getBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING));
    }

    @Test
    void toggleInteractiveMarksPoseControl() {
        CompoundTag nbt = new CompoundTag();
        assertTrue(StatueSitToggle.toggleInteractive(nbt));
        assertTrue(nbt.getBoolean(MaidNbtTags.STATUE_POSE_INTERACTIVE),
                "交互切换必须打上姿势控制标记（§8.17：YSM 雕像脱离内置 statue 姿势）");
        // 再次切换（回站姿）标记保持
        assertFalse(StatueSitToggle.toggleInteractive(nbt));
        assertTrue(nbt.getBoolean(MaidNbtTags.STATUE_POSE_INTERACTIVE));
    }

    @Test
    void plainToggleDoesNotMark() {
        CompoundTag nbt = new CompoundTag();
        StatueSitToggle.toggle(nbt);
        assertFalse(nbt.contains(MaidNbtTags.STATUE_POSE_INTERACTIVE));
    }
}
