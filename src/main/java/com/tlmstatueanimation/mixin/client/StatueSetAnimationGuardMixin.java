package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimationState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.tlmstatueanimation.client.StatueIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 雕像动画重载抑制（§8.25）。
 * <p>
 * moreanimation 的动作处理器（onPredicateParallel/onPredicateMain）在回放记录与当前值不一致时
 * 会 markNeedsReload 并以相同动画内容再次 setAnimation——原版逻辑里 needsAnimationReload=true
 * 会强制 shouldResetTick=true + TRANSITIONING，动画每帧回到第 0 帧（实测场景：原型女仆与
 * gecko 雕像同框时雕像停住）。雕像动画是无限循环，相同内容的强制重载永远无意义：
 * 清掉 reload 标志并取消本次调用即可。
 * <p>
 * 内容不同（换动作/换模型）或控制器已停止（PLAY_ONCE 播完需重播）时不干预。
 */
@Mixin(AnimationController.class)
public abstract class StatueSetAnimationGuardMixin {

    @Shadow
    @Final
    protected AnimatableEntity<?> animatable;
    @Shadow
    protected AnimationBuilder currentAnimationBuilder;
    @Shadow
    protected boolean needsAnimationReload;
    @Shadow
    protected AnimationState animationState;

    @Inject(method = "setAnimation", at = @At("HEAD"), remap = false, cancellable = true)
    private void tlmStatueAnimation$guardSpuriousReload(AnimationBuilder builder, CallbackInfo ci) {
        if (!this.needsAnimationReload || builder == null || builder.getRawAnimationList().isEmpty()) {
            return;
        }
        if (this.animationState == AnimationState.STOPPED) {
            return;
        }
        Object entity = this.animatable.getEntity();
        if (!(entity instanceof EntityMaid maid) || !StatueIdentity.isStatue(maid.getUUID())) {
            return;
        }
        if (this.currentAnimationBuilder != null
                && builder.getRawAnimationList().equals(this.currentAnimationBuilder.getRawAnimationList())) {
            this.needsAnimationReload = false;
            ci.cancel();
        }
    }
}
