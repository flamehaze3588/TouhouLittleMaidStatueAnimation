package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityStatueRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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
    private void tlmStatueAnimation$overrideRenderState(EntityMaid maid, MaidRenderState original) {
        if (maid.isYsmModel() && maid.rouletteAnimPlaying) {
            maid.renderState = MaidRenderState.ENTITY;
            return;
        }
        maid.renderState = original;
    }
}
