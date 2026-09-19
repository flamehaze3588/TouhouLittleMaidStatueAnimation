package com.tlmstatueanimation.server;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueRoamingVars 漫游变量写入测试（§8.22）：合法名写入 + 更新计数递增；非法名/非有限值拒绝。
 */
class StatueRoamingVarsTest {

    @Test
    void setVarWritesAndBumpsFlag() {
        CompoundTag nbt = new CompoundTag();
        assertTrue(StatueRoamingVars.setVar(nbt, "B", 1.0f));
        assertEquals(1.0f, nbt.getCompound(MaidNbtTags.YSM_ROAMING_VARS).getFloat("B"));
        assertEquals(1, nbt.getInt(MaidNbtTags.YSM_ROAMING_UPDATE_FLAG));
        // 再写：覆盖值并继续递增
        assertTrue(StatueRoamingVars.setVar(nbt, "B", 0.0f));
        assertEquals(0.0f, nbt.getCompound(MaidNbtTags.YSM_ROAMING_VARS).getFloat("B"));
        assertEquals(2, nbt.getInt(MaidNbtTags.YSM_ROAMING_UPDATE_FLAG));
    }

    @Test
    void setVarRejectsBadNameAndValue() {
        CompoundTag nbt = new CompoundTag();
        assertFalse(StatueRoamingVars.setVar(nbt, "9bad", 1.0f));
        assertFalse(StatueRoamingVars.setVar(nbt, "has space", 1.0f));
        assertFalse(StatueRoamingVars.setVar(nbt, "ok", Float.NaN));
        assertFalse(nbt.contains(MaidNbtTags.YSM_ROAMING_VARS), "校验失败不应创建子标签");
    }

    @Test
    void setVarPreservesExistingVars() {
        CompoundTag nbt = new CompoundTag();
        StatueRoamingVars.setVar(nbt, "B", 1.0f);
        StatueRoamingVars.setVar(nbt, "C", 1.0f);
        CompoundTag vars = nbt.getCompound(MaidNbtTags.YSM_ROAMING_VARS);
        assertEquals(1.0f, vars.getFloat("B"));
        assertEquals(1.0f, vars.getFloat("C"));
    }
}
