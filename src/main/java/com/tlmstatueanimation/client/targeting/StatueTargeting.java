package com.tlmstatueanimation.client.targeting;

import com.tlmstatueanimation.MaidNbtTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 准星雕像检测纯逻辑核心（D3），面向抽象编程：
 * 世界视图被抽象为 {@link BlockProbe}，真实 Level 适配见 {@code StatueTargetingForge}，
 * 因此本类不依赖任何 TLM 类，可用 lambda 伪造 probe 直接单测。
 */
public final class StatueTargeting {

    private StatueTargeting() {
    }

    /**
     * 对单个方块位置的只读探测视图。
     *
     * @param isStatue    是否雕像方块（含非核心块）
     * @param isGarageKit 是否手办方块
     * @param isCoreStatue 是否雕像核心块
     * @param corePos     雕像核心块坐标（非核心块须带正确值；手办/非雕像可为 null 或自身）
     * @param maidNbt     该方块上的女仆 extra NBT（雕像=核心块 extraMaidData，手办=extraData），可为 null
     */
    public record BlockProbe(boolean isStatue, boolean isGarageKit, boolean isCoreStatue,
                             BlockPos corePos, CompoundTag maidNbt) {

        public static BlockProbe notStatue() {
            return new BlockProbe(false, false, false, null, null);
        }
    }

    /**
     * 解析准星命中的方块：
     * 命中雕像核心块 → 直接取 maidNbt；命中雕像非核心块 → 经 corePos 二次 probe 取核心块 maidNbt；
     * 命中手办 → 取 extraData。随后统一过滤：maidNbt 非 null、IsYsmModel=true、YsmModelId 非空。
     *
     * @return 非雕像方块、NBT 缺失、非 YSM 模型时返回 {@link Optional#empty()}
     */
    public static Optional<StatueRef> resolve(BlockHitResult hit, Function<BlockPos, BlockProbe> probe) {
        return resolve(hit.getBlockPos(), probe, StatueTargeting::isYsmMaidNbt);
    }

    /**
     * moreanimation 软联动用的解析变体：遍历规则与 {@link #resolve} 相同，
     * 但不过滤 YSM 模型——moreanimation 的表情/动作对 TLM 默认 bedrock 模型也生效，
     * 只要求 maidNbt 非 null。
     */
    public static Optional<StatueRef> resolveAnyMaid(BlockPos pos, Function<BlockPos, BlockProbe> probe) {
        return resolve(pos, probe, nbt -> true);
    }

    private static Optional<StatueRef> resolve(BlockPos hitPos, Function<BlockPos, BlockProbe> probe,
                                               Predicate<CompoundTag> maidNbtFilter) {
        BlockProbe hitProbe = probe.apply(hitPos);
        if (hitProbe == null) {
            return Optional.empty();
        }
        if (hitProbe.isGarageKit()) {
            return filter(hitPos, StatueRef.Kind.GARAGE_KIT, hitProbe.maidNbt(), maidNbtFilter);
        }
        if (!hitProbe.isStatue()) {
            return Optional.empty();
        }
        if (hitProbe.isCoreStatue()) {
            return filter(hitPos, StatueRef.Kind.STATUE, hitProbe.maidNbt(), maidNbtFilter);
        }
        BlockPos corePos = hitProbe.corePos();
        if (corePos == null) {
            return Optional.empty();
        }
        BlockProbe coreProbe = probe.apply(corePos);
        if (coreProbe == null || !coreProbe.isStatue()) {
            return Optional.empty();
        }
        return filter(corePos, StatueRef.Kind.STATUE, coreProbe.maidNbt(), maidNbtFilter);
    }

    private static Optional<StatueRef> filter(BlockPos corePos, StatueRef.Kind kind, CompoundTag maidNbt,
                                              Predicate<CompoundTag> maidNbtFilter) {
        if (maidNbt == null || !maidNbtFilter.test(maidNbt)) {
            return Optional.empty();
        }
        return Optional.of(new StatueRef(corePos, kind, maidNbt));
    }

    private static boolean isYsmMaidNbt(CompoundTag maidNbt) {
        return maidNbt.getBoolean(MaidNbtTags.IS_YSM_MODEL)
                && !maidNbt.getString(MaidNbtTags.YSM_MODEL_ID).isEmpty();
    }
}
