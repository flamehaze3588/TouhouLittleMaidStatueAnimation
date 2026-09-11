package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityStatueRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
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
        if (maid.isYsmModel() && maid.rouletteAnimPlaying) {
            maid.renderState = MaidRenderState.ENTITY;
            return;
        }
        maid.renderState = original;
    }
}
