package com.tlmstatueanimation.network.message;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C2SPlayStatueAnimationPacket 编解码（codec）往返测试。
 * 断言：decode(encode(x)) == x。
 */
class C2SPlayStatueAnimationPacketTest {

    private static C2SPlayStatueAnimationPacket roundTrip(C2SPlayStatueAnimationPacket original) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(buf);
            return C2SPlayStatueAnimationPacket.decode(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    void normalValuesRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(10, 64, -20), "maid.swing", false);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void emptyKeyRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(0, 0, 0), "", false);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void emptySentinelKeyRoundTrip() {
        // "empty" 哨兵字符串（上游约定表示"清空/无动作"）
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(1, 2, 3), "empty", true);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void longKeyRoundTrip() {
        String longKey = "anim_" + "x".repeat(251); // 总长度 256
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(-5, 70, 5), longKey, false);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void utf8ChineseKeyRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(3, 65, 7), "女仆.挥舞.中国剑", false);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void stopTrueRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(0, 64, 0), "maid.idle", true);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void stopFalseRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(0, 64, 0), "maid.idle", false);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void negativeCoordinatesRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(-1000, -64, -1000), "maid.sit", false);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void largeCoordinatesRoundTrip() {
        C2SPlayStatueAnimationPacket original =
                new C2SPlayStatueAnimationPacket(new BlockPos(29999999, 320, 29999999), "maid.walk", true);
        assertEquals(original, roundTrip(original));
    }
}
