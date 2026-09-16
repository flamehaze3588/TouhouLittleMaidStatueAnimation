package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.StatueAnimFreezeControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * 抑制女仆随机 idle 与动作并行播放（§8.19）。
 * <p>
 * 背景：YSM 模型的随机 idle 由模型自带的动画控制器调度（如酒狐的 player.post_main：
 * {@code v.next_idle == N && ctrl.idle && !ctrl.playing_extra_animation} 时插播 idle1~4）。
 * 模型本有"播放额外动作时别插播"的防护条件 ctrl.playing_extra_animation，但 YSM 的实现
 * （CtrlBinding.isPlayingExtraAnimation）只对玩家实体生效——女仆（MaidCapability）恒 false，
 * 因此女仆播放轮盘动作 / moreanimation 动作时随机 idle 仍会并行插入（TLM×YSM 本体问题）。
 * <p>
 * 此处向该方法 HEAD 注入女仆分支：实体为 EntityMaid 且（轮盘动作播放中 || moreanimation
 * 动作未过期）时返回 true，模型自带的防护即生效。表情不参与判定（表情只改面部，
 * 不抑制随机 idle）。对真女仆同样生效——这是上游防护条件的本意，属顺带修复。
 * <p>
 * YSM 是混淆 jar，目标类/方法与 IContext.entity() 均用字符串名 + 反射（与 moreanimation
 * 的 YSM 桥同模式）；反射失败时记录一次 warn 并回退 YSM 原行为。YSM 版本被
 * moreanimation 硬校验锁死在 2.6.5-forge+mc1.20.1，混淆名失配风险可控。
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OoOOOOOo0oo0000oooOOOoOO", remap = false)
public abstract class YsmCtrlBindingMixin {

    /** IContext.entity() 的反射句柄（混淆名，YSM 2.6.5 实测） */
    @Unique
    private static Method tlmStatueAnimation$entityGetter;
    @Unique
    private static boolean tlmStatueAnimation$reflectFailed;

    @Inject(method = "Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/oo0oOO0000o0Ooooo0OoOo0O;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void tlmStatueAnimation$maidPlayingExtraAnimation(@Coerce Object context,
                                                                     CallbackInfoReturnable<Boolean> cir) {
        if (tlmStatueAnimation$reflectFailed) {
            return;
        }
        try {
            if (tlmStatueAnimation$entityGetter == null) {
                tlmStatueAnimation$entityGetter = Class
                        .forName("com.elfmcys.yesstevemodel.oo0oOO0000o0Ooooo0OoOo0O")
                        .getMethod("Oo0Oo0o00O00Oo0OOoOOoooo");
            }
            Object entity = tlmStatueAnimation$entityGetter.invoke(context);
            if (entity instanceof EntityMaid maid
                    && (maid.rouletteAnimPlaying
                    || StatueAnimFreezeControl.isActionActive(maid.getPersistentData(), maid.level().getGameTime()))) {
                cir.setReturnValue(true);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            tlmStatueAnimation$reflectFailed = true;
            TlmStatueAnimation.LOGGER.warn("YSM ctrl.playing_extra_animation mixin reflection failed; "
                    + "falling back to YSM default behavior (random idle may overlap actions)", e);
        }
    }
}
