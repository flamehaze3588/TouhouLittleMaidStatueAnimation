package com.tlmstatueanimation.compat.moreanimation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueExpressionState 纯逻辑测试（ForgeData 读取缺省语义 + 发包键值拼装）。
 * 目录与默认值以参数注入（生产环境由 MoreAnimationCompat 从 moreanimation 取），
 * 因此本测试不加载任何 moreanimation 类。
 */
class StatueExpressionStateTest {

    private static final List<String> CATALOG = List.of("circledance", "!??!");
    private static final List<String> DEFAULTS = List.of("circledance");

    // ---------- 读取 ----------

    @Test
    void enabledActionsAbsentKeyReturnsDefaults() {
        List<String> enabled = StatueExpressionState.enabledActions(new CompoundTag(), "stand", CATALOG, DEFAULTS);
        assertEquals(DEFAULTS, enabled);
    }

    @Test
    void enabledActionsReadsListAndFiltersCatalog() {
        CompoundTag forgeData = new CompoundTag();
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("!??!"));
        list.add(StringTag.valueOf("not_in_catalog"));
        list.add(StringTag.valueOf("circledance"));
        forgeData.put(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", list);

        List<String> enabled = StatueExpressionState.enabledActions(forgeData, "stand", CATALOG, DEFAULTS);

        // 保留 NBT 顺序，过滤掉目录外动作
        assertEquals(List.of("!??!", "circledance"), enabled);
    }

    @Test
    void enabledActionsEmptyListMeansNoneEnabled() {
        CompoundTag forgeData = new CompoundTag();
        forgeData.put(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", new ListTag());
        assertTrue(StatueExpressionState.enabledActions(forgeData, "stand", CATALOG, DEFAULTS).isEmpty());
    }

    @Test
    void maskAndListRoundTrip() {
        assertEquals(0b01, StatueExpressionState.maskOf(List.of("circledance"), CATALOG));
        assertEquals(0b11, StatueExpressionState.maskOf(CATALOG, CATALOG));
        assertEquals(0, StatueExpressionState.maskOf(List.of(), CATALOG));
        assertEquals(List.of("circledance"), StatueExpressionState.enabledFromMask(0b01, CATALOG));
        assertEquals(CATALOG, StatueExpressionState.enabledFromMask(0b11, CATALOG));
        assertEquals(List.of(), StatueExpressionState.enabledFromMask(0, CATALOG));
    }

    @Test
    void injuredAutoSemantics() {
        // 未显式设置过 → true
        assertTrue(StatueExpressionState.injuredAuto(new CompoundTag()));
        // 设置标记为假 → 视为未设置 → true
        CompoundTag notSet = new CompoundTag();
        notSet.putBoolean(MoreAnimationNbtKeys.INJURED_AUTO, false);
        assertTrue(StatueExpressionState.injuredAuto(notSet));
        // 显式设置后读值
        CompoundTag off = new CompoundTag();
        off.putBoolean(MoreAnimationNbtKeys.INJURED_AUTO_SET, true);
        off.putBoolean(MoreAnimationNbtKeys.INJURED_AUTO, false);
        assertFalse(StatueExpressionState.injuredAuto(off));
        CompoundTag on = new CompoundTag();
        on.putBoolean(MoreAnimationNbtKeys.INJURED_AUTO_SET, true);
        on.putBoolean(MoreAnimationNbtKeys.INJURED_AUTO, true);
        assertTrue(StatueExpressionState.injuredAuto(on));
    }

    @Test
    void autoPetSemantics() {
        // 未显式设置 → 配置默认值
        assertTrue(StatueExpressionState.autoPet(new CompoundTag(), true));
        assertFalse(StatueExpressionState.autoPet(new CompoundTag(), false));
        // 显式设置优先于配置默认值
        CompoundTag set = new CompoundTag();
        set.putBoolean(MoreAnimationNbtKeys.AUTO_PET_SET, true);
        set.putBoolean(MoreAnimationNbtKeys.AUTO_PET, true);
        assertTrue(StatueExpressionState.autoPet(set, false));
    }

    @Test
    void randomSleepPoseSemantics() {
        assertTrue(StatueExpressionState.randomSleepPose(new CompoundTag()));
        CompoundTag off = new CompoundTag();
        off.putBoolean(MoreAnimationNbtKeys.RANDOM_SLEEP_POSE_SET, true);
        off.putBoolean(MoreAnimationNbtKeys.RANDOM_SLEEP_POSE, false);
        assertFalse(StatueExpressionState.randomSleepPose(off));
    }

    @Test
    void formModeClamp() {
        assertEquals(MoreAnimationNbtKeys.FORM_AUTO, StatueExpressionState.formMode(new CompoundTag()));
        CompoundTag fox = new CompoundTag();
        fox.putInt(MoreAnimationNbtKeys.FORM_MODE, 2);
        assertEquals(MoreAnimationNbtKeys.FORM_FOX, StatueExpressionState.formMode(fox));
        CompoundTag tooBig = new CompoundTag();
        tooBig.putInt(MoreAnimationNbtKeys.FORM_MODE, 99);
        assertEquals(MoreAnimationNbtKeys.FORM_AUTO, StatueExpressionState.formMode(tooBig));
        CompoundTag negative = new CompoundTag();
        negative.putInt(MoreAnimationNbtKeys.FORM_MODE, -1);
        assertEquals(MoreAnimationNbtKeys.FORM_AUTO, StatueExpressionState.formMode(negative));
    }

    // ---------- 键值拼装 ----------

    @Test
    void expressionKeysWritesExpression() {
        CompoundTag keys = StatueExpressionState.expressionKeys("wuyu");
        assertEquals("wuyu", keys.getString(MoreAnimationNbtKeys.EXPRESSION));
        assertEquals(1, keys.size());
    }

    @Test
    void expressionStopRemovesExpressionKey() {
        assertEquals(List.of(MoreAnimationNbtKeys.EXPRESSION), StatueExpressionState.expressionStopRemoveKeys());
    }

    @Test
    void playKeysOnlyCarriesActionKey() {
        CompoundTag keys = StatueExpressionState.playKeys("circledance");
        assertEquals("circledance", keys.getString(MoreAnimationNbtKeys.ACTIVE));
        // 时间窗由服务端补齐，客户端不携带
        assertFalse(keys.contains(MoreAnimationNbtKeys.ACTIVE_START));
        assertFalse(keys.contains(MoreAnimationNbtKeys.ACTIVE_UNTIL));
        assertEquals(1, keys.size());
    }

    @Test
    void categoryEnabledKeysRejectsUnknownAction() {
        assertNull(StatueExpressionState.categoryEnabledKeys("stand", CATALOG, DEFAULTS, "unknown", true));
    }

    @Test
    void categoryEnabledKeysEnableAppendsAndDisablesRemoves() {
        CompoundTag enabledTag = StatueExpressionState.categoryEnabledKeys(
                "stand", CATALOG, List.of("circledance"), "!??!", true);
        assert enabledTag != null;
        ListTag enabledList = enabledTag.getList(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", Tag.TAG_STRING);
        assertEquals(2, enabledList.size());
        assertEquals("circledance", enabledList.getString(0));
        assertEquals("!??!", enabledList.getString(1));

        CompoundTag disabledTag = StatueExpressionState.categoryEnabledKeys(
                "stand", CATALOG, List.of("circledance", "!??!"), "circledance", false);
        assert disabledTag != null;
        ListTag disabledList = disabledTag.getList(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", Tag.TAG_STRING);
        assertEquals(1, disabledList.size());
        assertEquals("!??!", disabledList.getString(0));
    }

    @Test
    void categoryEnabledKeysEnableIsIdempotent() {
        CompoundTag tag = StatueExpressionState.categoryEnabledKeys(
                "stand", CATALOG, List.of("circledance"), "circledance", true);
        assert tag != null;
        assertEquals(1, tag.getList(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", Tag.TAG_STRING).size());
    }

    @Test
    void boolSwitchKeysSetsFlagAndSetMarker() {
        CompoundTag keys = StatueExpressionState.boolSwitchKeys(
                MoreAnimationNbtKeys.AUTO_HUG_SET, MoreAnimationNbtKeys.AUTO_HUG, false);
        assertTrue(keys.getBoolean(MoreAnimationNbtKeys.AUTO_HUG_SET));
        assertFalse(keys.getBoolean(MoreAnimationNbtKeys.AUTO_HUG));
    }

    @Test
    void formModeKeysClamps() {
        assertEquals(MoreAnimationNbtKeys.FORM_FOX,
                StatueExpressionState.formModeKeys(99).getInt(MoreAnimationNbtKeys.FORM_MODE));
        assertEquals(MoreAnimationNbtKeys.FORM_AUTO,
                StatueExpressionState.formModeKeys(-3).getInt(MoreAnimationNbtKeys.FORM_MODE));
    }

    @Test
    void interactionSoloActionMapping() {
        assertEquals("pet_other_head", StatueExpressionState.interactionSoloAction("pet_owner"));
        assertEquals("pet_other_head", StatueExpressionState.interactionSoloAction("pet_maid"));
        assertEquals("hugtogether", StatueExpressionState.interactionSoloAction("hug_owner"));
        assertEquals("hugtogether", StatueExpressionState.interactionSoloAction("hug_maid"));
        assertEquals("", StatueExpressionState.interactionSoloAction("unknown"));
    }
}
