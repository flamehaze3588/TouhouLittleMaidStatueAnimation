package com.tlmstatueanimation.client;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * StatueIdentity：同一坐标 UUID 恒定、不同坐标互异（§8.20）。
 */
class StatueIdentityTest {

    @Test
    void stableForSamePos() {
        BlockPos pos = new BlockPos(13, -60, -18);
        assertEquals(StatueIdentity.forPos(pos), StatueIdentity.forPos(pos));
    }

    @Test
    void distinctForDistinctPos() {
        assertNotEquals(StatueIdentity.forPos(new BlockPos(0, 0, 0)),
                StatueIdentity.forPos(new BlockPos(1, 0, 0)));
    }
}
