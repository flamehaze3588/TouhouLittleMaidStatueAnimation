package com.tlmstatueanimation.client.targeting;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueTargeting.resolveAnyMaid（moreanimation 软联动变体）纯逻辑测试：
 * 遍历规则与 resolve 相同，但不过滤 YSM 模型，只要求 maidNbt 非 null。
 */
class StatueTargetingAnyMaidTest {

    private static final BlockPos CORE_POS = new BlockPos(10, 64, -20);
    private static final BlockPos NON_CORE_POS = new BlockPos(10, 65, -20);
    private static final BlockPos KIT_POS = new BlockPos(0, 70, 5);
    private static final BlockPos PLAIN_POS = new BlockPos(1, 2, 3);

    /** 普通 TLM bedrock 模型女仆的 NBT：无任何 YSM tag */
    private static CompoundTag plainMaidNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ModelId", "touhou_little_maid:hakurei_reimu");
        return tag;
    }

    private static StatueTargeting.BlockProbe coreStatue(CompoundTag nbt) {
        return new StatueTargeting.BlockProbe(true, false, true, CORE_POS, nbt);
    }

    private static StatueTargeting.BlockProbe nonCoreStatue(BlockPos corePos) {
        return new StatueTargeting.BlockProbe(true, false, false, corePos, null);
    }

    private static StatueTargeting.BlockProbe garageKit(CompoundTag nbt) {
        return new StatueTargeting.BlockProbe(false, true, false, KIT_POS, nbt);
    }

    private static Function<BlockPos, StatueTargeting.BlockProbe> probeOf(Map<BlockPos, StatueTargeting.BlockProbe> map) {
        return pos -> map.getOrDefault(pos, StatueTargeting.BlockProbe.notStatue());
    }

    @Test
    void nonYsmModelStatueStillHits() {
        // 关键点：IsYsmModel=false 的普通模型雕像也命中（resolve 会拒绝，resolveAnyMaid 放行）
        CompoundTag nbt = plainMaidNbt();
        nbt.putBoolean(MaidNbtTags.IS_YSM_MODEL, false);
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolveAnyMaid(CORE_POS, probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(CORE_POS, ref.get().corePos());
        assertEquals(StatueRef.Kind.STATUE, ref.get().kind());
        assertEquals(nbt, ref.get().maidNbt());
    }

    @Test
    void nbtWithoutAnyYsmTagsHits() {
        CompoundTag nbt = plainMaidNbt();
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolveAnyMaid(CORE_POS, probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(nbt, ref.get().maidNbt());
    }

    @Test
    void garageKitHitsWithOwnPos() {
        CompoundTag nbt = plainMaidNbt();
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(KIT_POS, garageKit(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolveAnyMaid(KIT_POS, probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(KIT_POS, ref.get().corePos());
        assertEquals(StatueRef.Kind.GARAGE_KIT, ref.get().kind());
    }

    @Test
    void nonCoreStatueResolvesViaCorePos() {
        CompoundTag nbt = plainMaidNbt();
        Map<BlockPos, StatueTargeting.BlockProbe> world = new HashMap<>();
        world.put(NON_CORE_POS, nonCoreStatue(CORE_POS));
        world.put(CORE_POS, coreStatue(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolveAnyMaid(NON_CORE_POS, probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(CORE_POS, ref.get().corePos());
        assertEquals(nbt, ref.get().maidNbt());
    }

    @Test
    void nullMaidNbtIsEmpty() {
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(null));
        assertTrue(StatueTargeting.resolveAnyMaid(CORE_POS, probeOf(world)).isEmpty());
    }

    @Test
    void plainBlockIsEmpty() {
        assertTrue(StatueTargeting.resolveAnyMaid(PLAIN_POS, probeOf(Map.of())).isEmpty());
    }

    @Test
    void nullProbeResultIsEmpty() {
        assertTrue(StatueTargeting.resolveAnyMaid(PLAIN_POS, pos -> null).isEmpty());
    }

    @Test
    void nonCoreHitWithMissingCoreBlockIsEmpty() {
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(NON_CORE_POS, nonCoreStatue(CORE_POS));
        assertTrue(StatueTargeting.resolveAnyMaid(NON_CORE_POS, probeOf(world)).isEmpty());
    }
}
