package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueMaidNbt 纯函数测试（D5 服务端 NBT 写入语义）。
 */
class StatueMaidNbtTest {

    @Test
    void writePlaySetsAnimKeyAndPlaying() {
        CompoundTag nbt = new CompoundTag();
        StatueMaidNbt.writePlay(nbt, "extra3");

        assertEquals("extra3", nbt.getString(MaidNbtTags.YSM_ROULETTE_ANIM));
        assertTrue(nbt.getBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING));
    }

    @Test
    void writePlayOverwritesExistingKey() {
        CompoundTag nbt = new CompoundTag();
        StatueMaidNbt.writePlay(nbt, "extra1");
        StatueMaidNbt.writePlay(nbt, "extra5");

        assertEquals("extra5", nbt.getString(MaidNbtTags.YSM_ROULETTE_ANIM));
        assertTrue(nbt.getBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING));
    }

    @Test
    void writeStopKeepsAnimKey() {
        CompoundTag nbt = new CompoundTag();
        StatueMaidNbt.writePlay(nbt, "extra2");
        StatueMaidNbt.writeStop(nbt);

        assertFalse(nbt.getBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING));
        // 停止语义是 playing=false，动作 key 必须原样保留（文档 §6-c）
        assertEquals("extra2", nbt.getString(MaidNbtTags.YSM_ROULETTE_ANIM));
    }

    @Test
    void writeStopOnFreshTagDoesNotCreateAnimKey() {
        CompoundTag nbt = new CompoundTag();
        StatueMaidNbt.writeStop(nbt);

        assertFalse(nbt.getBoolean(MaidNbtTags.STATUE_ROULETTE_PLAYING));
        assertFalse(nbt.contains(MaidNbtTags.YSM_ROULETTE_ANIM));
    }

    @Test
    void isYsmMaidThreeStates() {
        CompoundTag ysm = new CompoundTag();
        ysm.putBoolean(MaidNbtTags.IS_YSM_MODEL, true);
        assertTrue(StatueMaidNbt.isYsmMaid(ysm));

        CompoundTag notYsm = new CompoundTag();
        notYsm.putBoolean(MaidNbtTags.IS_YSM_MODEL, false);
        assertFalse(StatueMaidNbt.isYsmMaid(notYsm));

        CompoundTag missing = new CompoundTag();
        assertFalse(StatueMaidNbt.isYsmMaid(missing));
    }
}
