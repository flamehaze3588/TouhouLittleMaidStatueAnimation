package com.tlmstatueanimation.network.message;

import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * C2SStatueExpressionPacket 编解码（codec）往返测试。
 * 断言：decode(encode(x)) == x。
 */
class C2SStatueExpressionPacketTest {

    private static C2SStatueExpressionPacket roundTrip(C2SStatueExpressionPacket original) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.encode(buf);
            return C2SStatueExpressionPacket.decode(buf);
        } finally {
            buf.release();
        }
    }

    @Test
    void expressionSetRoundTrip() {
        CompoundTag setKeys = new CompoundTag();
        setKeys.putString(MoreAnimationNbtKeys.EXPRESSION, "lips");
        C2SStatueExpressionPacket original =
                new C2SStatueExpressionPacket(new BlockPos(10, 64, -20), setKeys, List.of());
        assertEquals(original, roundTrip(original));
    }

    @Test
    void emptyPayloadRoundTrip() {
        C2SStatueExpressionPacket original =
                new C2SStatueExpressionPacket(new BlockPos(0, 0, 0), new CompoundTag(), List.of());
        assertEquals(original, roundTrip(original));
    }

    @Test
    void removeKeysRoundTrip() {
        // 多个删除键的顺序需保持
        List<String> removes = List.of(MoreAnimationNbtKeys.EXPRESSION, MoreAnimationNbtKeys.ACTIVE,
                MoreAnimationNbtKeys.ACTIVE_START, MoreAnimationNbtKeys.ACTIVE_UNTIL);
        C2SStatueExpressionPacket original =
                new C2SStatueExpressionPacket(new BlockPos(1, 2, 3), new CompoundTag(), removes);
        assertEquals(original, roundTrip(original));
    }

    @Test
    void nestedSetKeysRoundTrip() {
        // 分类启用列表（ListTag）与嵌套复合标签
        CompoundTag setKeys = new CompoundTag();
        ListTag enabled = new ListTag();
        enabled.add(StringTag.valueOf("circledance"));
        enabled.add(StringTag.valueOf("!??!"));
        setKeys.put(MoreAnimationNbtKeys.ENABLED_PREFIX + "stand", enabled);
        CompoundTag nested = new CompoundTag();
        nested.putLong(MoreAnimationNbtKeys.ACTIVE_UNTIL, 123456789L);
        setKeys.put("moreanimation_nested", nested);
        setKeys.putBoolean(MoreAnimationNbtKeys.AUTO_PET_SET, true);
        C2SStatueExpressionPacket original =
                new C2SStatueExpressionPacket(new BlockPos(-5, 70, 5), setKeys, List.of(MoreAnimationNbtKeys.EXPRESSION));
        assertEquals(original, roundTrip(original));
    }

    @Test
    void utf8ValueRoundTrip() {
        CompoundTag setKeys = new CompoundTag();
        setKeys.putString(MoreAnimationNbtKeys.EXPRESSION, "表情.测试");
        C2SStatueExpressionPacket original =
                new C2SStatueExpressionPacket(new BlockPos(3, 65, 7), setKeys, List.of());
        assertEquals(original, roundTrip(original));
    }

    @Test
    void largeCoordinatesRoundTrip() {
        CompoundTag setKeys = new CompoundTag();
        setKeys.putInt(MoreAnimationNbtKeys.FORM_MODE, 2);
        C2SStatueExpressionPacket original =
                new C2SStatueExpressionPacket(new BlockPos(29999999, 320, 29999999), setKeys, List.of());
        assertEquals(original, roundTrip(original));
    }
}
