package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityStatueRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.client.StatueAnimFreezeControl;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 轮盘动作播放期间，把缓存假女仆的 renderState 从 STATUE 改为 ENTITY（§8.7）。
 * 原因：YSM 的 MaidStatusAnimationPredicate 对 renderState==STATUE 强制循环播放模型内置的
 * "statue" 姿势动画，且层优先级高于轮盘动作层，导致轮盘动作被雕像姿势污染。
 * renderState=ENTITY 后，YSM 走与真实女仆一致的 待机+轮盘动作 判定链。
 * <p>
 * 顺带修正实体坐标（§8.11）：假女仆每帧 load(NBT) 会把坐标还原成拍照时的 Pos，
 * YSM 动作自带音乐/音效按实体坐标播放——会错误地出现在拍照地点。本注入点位于 load() 之后、
 * 渲染之前，把实体坐标改为雕像方块坐标，声音即跟随雕像。视觉位置由 poseStack 平移决定，不受影响。
 * <p>
 * 播放状态直接读缓存女仆自身的 rouletteAnimPlaying 字段（由本 mod 的 StatueAnimationApplier
 * 在每 tick 按 NBT 置位，渲染发生在同帧 tick 之后，时序安全），无需捕获渲染器方法参数。
 */
@Mixin(TileEntityStatueRenderer.class)
public abstract class TileEntityStatueRendererMixin {

    @Redirect(method = "renderEntity",
            at = @At(value = "FIELD",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;renderState:Lcom/github/tartaricacid/touhoulittlemaid/api/client/render/MaidRenderState;",
                    opcode = Opcodes.PUTFIELD),
            remap = false)
    private void tlmStatueAnimation$overrideRenderState(EntityMaid maid, MaidRenderState original,
                                                        TileEntityStatue te, PoseStack poseStack,
                                                        MultiBufferSource bufferIn, int combinedLightIn,
                                                        CompoundTag data, Level world, EntityType<?> type) {
        BlockPos pos = te.getBlockPos();
        maid.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // 上一行 TLM 的 clearMaidDataResidue 每帧强制 setInSittingPose(false)（雕像恒站立的设计）；
        // 本 mod 支持蹲下+右键切换姿势（§8.13），故按 NBT 的 Sitting 键重新声明坐姿。
        // 坐姿翻转会触发 FreezeGraceTracker 的宽限解冻窗（§8.14），冻结雕像也能平滑切 sit/idle。
        maid.setInSittingPose(data.getBoolean(MaidNbtTags.SITTING));
        // §8.17：蹲下切换过姿势的 YSM 雕像脱离内置 statue 姿势（renderState=ENTITY，走真实女仆
        // 渲染链）——否则 STATUE 状态强制的模型内置站姿剪辑会与坐姿叠加成"站姿半身入地"
        if (maid.isYsmModel() && (maid.rouletteAnimPlaying || data.getBoolean(MaidNbtTags.STATUE_POSE_INTERACTIVE))) {
            maid.renderState = MaidRenderState.ENTITY;
            return;
        }
        maid.renderState = original;
    }

    /**
     * 冻结控制（§8.12/§8.14）：TLM 对非 YSM 雕像强制 tickCount=0（定格第 0 帧），
     * geckolib 动画时间 = entity.tickCount + partialTick。
     * 此处重定向 renderEntity 中唯一的 isYsmModel() 调用（tickCount 定格判定条件的一部分），
     * 解冻判定委托给 StatueAnimFreezeControl：moreanimation 活跃（表情非空 / 一次性动作未过期）
     * 恒解冻；坐姿翻转或动作刚过期的下降沿触发 10 tick 宽限解冻窗（geckolib 动画过渡需要时间
     * 前进才能完成，单帧强制求值会导致切换不即时/骨骼残影）。该分支内只有 tickCount 赋值一个
     * 动作，无其他副作用；目标是 TLM 自有方法，remap=false。
     */
    @Redirect(method = "renderEntity",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isYsmModel()Z"),
            remap = false)
    private boolean tlmStatueAnimation$unfreezeForMoreAnimation(EntityMaid maid,
                                                                TileEntityStatue te, PoseStack poseStack,
                                                                MultiBufferSource bufferIn, int combinedLightIn,
                                                                CompoundTag data, Level world, EntityType<?> type) {
        if (maid.isYsmModel()) {
            return true;
        }
        return StatueAnimFreezeControl.shouldUnfreeze(maid, data, world);
    }
}
