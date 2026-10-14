package com.tlmstatueanimation.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmstatueanimation.TlmStatueAnimation;
import net.minecraftforge.common.capabilities.Capability;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * moreanimation YSM 桥的表情时间源时移（§8.23 补充）。
 * <p>
 * 桥里动作进度 = (now - ACTIVE_START)/20（已由 StatueFrameFreeze 时移覆盖），
 * 但表情进度 = (now - PLAYING_EXPRESSIONS[animatable].start())/20——另一个独立时间源，
 * 存在 moreanimation 客户端静态 Map 中，必须同步时移才能定格表情。
 * <p>
 * 实现全部用反射（不改 moreanimation 任何字节码、不加 mixin）：
 * 取 PLAYING_EXPRESSIONS 中当前记录，按定格时长构造新的 start 并回写
 * （新 start = 原 start + now - freezeAt，使 now - start 恒等于定格瞬间进度）。
 * 记录的 start 不可变，故用规范构造器新建 ExpressionPlaying 放回 Map。
 * 反射失败时记一次 warn 并停止后续尝试（降级为仅身体定格）。
 */
public final class YsmExpressionFreeze {
    private static final String BRIDGE_CLASS = "com.github.JumDa5he.moreanimation.compat.ysm.YsmAnimationBridge";
    private static final String PLAYING_CLASS = BRIDGE_CLASS + "$ExpressionPlaying";
    private static final String PROVIDER_CLASS = "com.elfmcys.yesstevemodel.OO0oo0O00O0Oo0O00000o0oO";
    private static final String TOKEN_FIELD = "Oo0Oo0o00O00Oo0OOoOOoooo";

    private static Object capabilityToken;
    private static Field playingExpressionsField;
    private static Constructor<?> expressionPlayingCtor;
    private static boolean failed;
    /**
     * 每个 animatable 定格瞬间的原始 start（关键：不能用桥里已被时移过的 start 重复叠加，
     * 否则进度每帧漂移而不是恒定）。
     */
    private static final WeakHashMap<Object, Long> ORIGINAL_STARTS = new WeakHashMap<>();

    private YsmExpressionFreeze() {
    }

    private static boolean init() {
        if (failed) {
            return false;
        }
        if (playingExpressionsField != null) {
            return true;
        }
        try {
            capabilityToken = Class.forName(PROVIDER_CLASS).getField(TOKEN_FIELD).get(null);
            playingExpressionsField = Class.forName(BRIDGE_CLASS).getDeclaredField("PLAYING_EXPRESSIONS");
            playingExpressionsField.setAccessible(true);
            expressionPlayingCtor = Class.forName(PLAYING_CLASS)
                    .getDeclaredConstructor(String.class, long.class);
            expressionPlayingCtor.setAccessible(true);
            return true;
        } catch (Throwable t) {
            failed = true;
            TlmStatueAnimation.LOGGER.warn("YsmExpressionFreeze reflection init failed; expression freeze disabled", t);
            return false;
        }
    }

    /**
     * 定格期间每帧调用：把该 animatable 的表情 start 按定格时长前移，保持表情进度恒定。
     * 非定格帧不要调用（让桥自然处理）。
     */
    public static void shiftExpressionStart(EntityMaid maid, long now, long freezeAt) {
        if (!init()) {
            return;
        }
        try {
            Object animatable = maid.getCapability((Capability) capabilityToken).orElse(null);
            if (animatable == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<Object, Object> playing = (Map<Object, Object>) playingExpressionsField.get(null);
            Object current = playing.get(animatable);
            if (current == null) {
                return;
            }
            Method expressionGetter = current.getClass().getMethod("expression");
            Method startGetter = current.getClass().getMethod("start");
            // ExpressionPlaying 是 private 嵌套 record：其 public 访问器也必须 setAccessible 才能反射调用
            expressionGetter.setAccessible(true);
            startGetter.setAccessible(true);
            String expression = (String) expressionGetter.invoke(current);
            long originalStart = ORIGINAL_STARTS.computeIfAbsent(animatable,
                    key -> {
                        try {
                            return (long) startGetter.invoke(current);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    });
            // 每帧以定格瞬间的原始 start 为基准前移，now - start 恒等于定格瞬间进度
            long adjusted = originalStart + now - freezeAt;
            if (originalStart == adjusted) {
                return;
            }
            playing.put(animatable, expressionPlayingCtor.newInstance(expression, adjusted));
        } catch (Throwable t) {
            TlmStatueAnimation.LOGGER.debug("YsmExpressionFreeze shift failed", t);
        }
    }
}
