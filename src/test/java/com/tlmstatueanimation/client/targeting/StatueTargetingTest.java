package com.tlmstatueanimation.client.targeting;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StatueTargeting.resolve 绾€昏緫娴嬭瘯锛圖3 鍑嗘槦妫€娴嬩笌鏍稿績鍧楄В鏋愶級銆? * BlockProbe 鐢?lambda/Map 浼€狅紝鏃犻渶娓告垙鍚姩锛汣ompoundTag 鐩存帴 new銆? */
class StatueTargetingTest {

    private static final BlockPos CORE_POS = new BlockPos(10, 64, -20);
    private static final BlockPos NON_CORE_POS = new BlockPos(10, 65, -20);
    private static final BlockPos KIT_POS = new BlockPos(0, 70, 5);
    private static final BlockPos PLAIN_POS = new BlockPos(1, 2, 3);

    private static CompoundTag ysmNbt(String modelId) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MaidNbtTags.IS_YSM_MODEL, true);
        tag.putString(MaidNbtTags.YSM_MODEL_ID, modelId);
        return tag;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
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
    void coreStatueHitResolvesDirectly() {
        CompoundTag nbt = ysmNbt("wine_fox/12_little");
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolve(hit(CORE_POS), probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(CORE_POS, ref.get().corePos());
        assertEquals(StatueRef.Kind.STATUE, ref.get().kind());
        assertEquals(nbt, ref.get().maidNbt());
    }

    @Test
    void nonCoreStatueHitResolvesViaCorePos() {
        CompoundTag nbt = ysmNbt("wine_fox/12_little");
        Map<BlockPos, StatueTargeting.BlockProbe> world = new HashMap<>();
        world.put(NON_CORE_POS, nonCoreStatue(CORE_POS));
        world.put(CORE_POS, coreStatue(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolve(hit(NON_CORE_POS), probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(CORE_POS, ref.get().corePos());
        assertEquals(StatueRef.Kind.STATUE, ref.get().kind());
        assertEquals(nbt, ref.get().maidNbt());
    }

    @Test
    void garageKitHitResolvesWithOwnPos() {
        CompoundTag nbt = ysmNbt("maid_sit");
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(KIT_POS, garageKit(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolve(hit(KIT_POS), probeOf(world));

        assertTrue(ref.isPresent());
        assertEquals(KIT_POS, ref.get().corePos());
        assertEquals(StatueRef.Kind.GARAGE_KIT, ref.get().kind());
        assertEquals(nbt, ref.get().maidNbt());
    }

    @Test
    void plainBlockHitIsEmpty() {
        Optional<StatueRef> ref = StatueTargeting.resolve(hit(PLAIN_POS), probeOf(Map.of()));
        assertTrue(ref.isEmpty());
    }

    @Test
    void nullMaidNbtIsEmpty() {
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(null));
        Optional<StatueRef> ref = StatueTargeting.resolve(hit(CORE_POS), probeOf(world));
        assertTrue(ref.isEmpty());
    }

    @Test
    void nonYsmModelIsEmpty() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean(MaidNbtTags.IS_YSM_MODEL, false);
        nbt.putString(MaidNbtTags.YSM_MODEL_ID, "wine_fox/12_little");
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(nbt));

        Optional<StatueRef> ref = StatueTargeting.resolve(hit(CORE_POS), probeOf(world));
        assertTrue(ref.isEmpty());
    }

    @Test
    void emptyModelIdIsEmpty() {
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(CORE_POS, coreStatue(ysmNbt("")));
        Optional<StatueRef> ref = StatueTargeting.resolve(hit(CORE_POS), probeOf(world));
        assertTrue(ref.isEmpty());
    }

    @Test
    void nonCoreHitWithMissingCoreBlockIsEmpty() {
        // 非核心块指向的 corePos 处不是雕像（数据不同步/已破坏）→ 空
        Map<BlockPos, StatueTargeting.BlockProbe> world = Map.of(NON_CORE_POS, nonCoreStatue(CORE_POS));
        Optional<StatueRef> ref = StatueTargeting.resolve(hit(NON_CORE_POS), probeOf(world));
        assertTrue(ref.isEmpty());
    }
}
