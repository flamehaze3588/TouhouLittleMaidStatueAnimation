package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 抑制"雕像 + 并行动作"时 TLM 主控制器 idle 兜底（§8.24）。
 * <p>
 * moreanimation 对并行动作（circledance 等）不在主控制器接管，于是 TLM 原版 predicateMain
 * 会退到 idle 动画，与并行动画同时播放。此处仅当实体处于 STATUE/GARAGE_KIT 渲染态且
 * 有未过期的并行动作时返回 STOP——普通 idle 动画不再与并行动作并行。
 * 非并行动作不受影响（moreanimation 自己接管主控制器）；真女仆 renderState=ENTITY 不受影响。
 * 目标是 TLM 自有类与方法（未混淆），remap=false。
 */
@Mixin(AnimationManager.class)
public abstract class AnimationManagerStatueMixin {

    @Inject(method = "predicateMain", at = @At("HEAD"), cancellable = true, remap = false)
    private void tlmStatueAnimation$stopIdleForParallelStatue(AnimationEvent<GeckoMaidEntity<?>> event,
                                                              CallbackInfoReturnable<PlayState> cir) {
        IMaid maid = event.getAnimatableEntity().getMaid();
        if (!(maid instanceof EntityMaid entity)) {
            return;
        }
        if (entity.renderState != MaidRenderState.STATUE && entity.renderState != MaidRenderState.GARAGE_KIT) {
            return;
        }
        if (!MoreAnimationCompat.isLoaded()) {
            return;
        }
        String action = MoreAnimationCompat.activeAction(entity);
        if (!action.isEmpty() && MoreAnimationCompat.isParallelAction(action)) {
            cir.setReturnValue(PlayState.STOP);
        }
    }
}
