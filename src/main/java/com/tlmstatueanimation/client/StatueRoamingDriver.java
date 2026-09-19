package com.tlmstatueanimation.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.EntityCacheUtil;
import com.tlmstatueanimation.TlmStatueAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 漫游变量的"轮盘同款"直达写入（§8.22 排障）：YSM 原版轮盘勾选配置项时是在 UI 线程直接对
 * 女仆 capability 执行 molang 赋值，此处镜像该路径——从 TLM 雕像缓存取假女仆，经 Forge
 * capability 拿到 YSM MaidCapability（animatable），执行 "v.roaming.<name>=<value>"。
 * 与渲染帧路径（YsmStatueRoamingMixin）互补，用于区分"写入时机被每帧重置覆盖"与
 * "雕像 capability 的漫游读取根本没接"两类故障：点击后带一次读回日志。
 * <p>
 * YSM 混淆名锚点（2.6.5，与 §8.19 同版本锁定）：
 * capability token = OO0oo0O00O0Oo0O00000o0oO.Oo0Oo0o00O00Oo0OOoOOoooo（静态 Capability 字段）；
 * animatable 基类 = o0000OoOooO0oo0o0oooo0Oo（parse=O00OOOooOoooOoo0o0o0oO0O(String)，
 * execute=Oo0Oo0o00O00Oo0OOoOOoooo(Expression, Z, Z, Consumer)）。
 */
public final class StatueRoamingDriver {
    private static final String PROVIDER_CLASS = "com.elfmcys.yesstevemodel.OO0oo0O00O0Oo0O00000o0oO";
    private static final String TOKEN_FIELD = "Oo0Oo0o00O00Oo0OOoOOoooo";
    private static final String ANIMATABLE_CLASS = "com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo";
    /** molang 解析缓存类（parseSimpleExpression 真身；animatable 基类上的同名实例方法是桩，不可用） */
    private static final String PARSER_CLASS = "com.elfmcys.yesstevemodel.O00o0ooOoo00o00o0OOOO0o0";
    private static final String EXPRESSION_CLASS = "com.elfmcys.yesstevemodel.O0o0OOO00000oO00O00oOOo0";

    private static Object capabilityToken;
    private static Method parseExpression;
    private static Method executeExpression;
    private static boolean failed;
    private static final Map<String, Object> PARSED_CACHE = new ConcurrentHashMap<>();

    private StatueRoamingDriver() {
    }

    private static boolean init() {
        if (failed) {
            return false;
        }
        if (capabilityToken != null) {
            return true;
        }
        try {
            Field tokenField = Class.forName(PROVIDER_CLASS).getField(TOKEN_FIELD);
            capabilityToken = tokenField.get(null);
            Class<?> base = Class.forName(ANIMATABLE_CLASS);
            parseExpression = Class.forName(PARSER_CLASS).getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", String.class);
            executeExpression = base.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo",
                    Class.forName(EXPRESSION_CLASS), boolean.class, boolean.class, Consumer.class);
            return true;
        } catch (Throwable t) {
            failed = true;
            TlmStatueAnimation.LOGGER.warn("Statue roaming driver reflection init failed", t);
            return false;
        }
    }

    /**
     * 点击路径直达写入（与 YSM 原版轮盘同线程同方式：UI 线程直接对 capability 执行 molang 赋值），
     * 让配置项即时生效，无需等下一帧渲染路径。雕像/手办方块共用 TLM 的 STATUE_CACHE
     * （key=方块坐标），缓存恰好过期时静默跳过（渲染帧路径 YsmStatueRoamingMixin 仍会覆盖到）。
     */
    public static void applyFromScreen(BlockPos corePos, String varName, float value) {
        if (!init()) {
            return;
        }
        Entity entity = EntityCacheUtil.STATUE_CACHE.getIfPresent(corePos.asLong());
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }
        try {
            Object capability = maid.getCapability((net.minecraftforge.common.capabilities.Capability) capabilityToken)
                    .orElse(null);
            if (capability == null) {
                return;
            }
            String expression = "v.roaming." + varName + "=" + value;
            Object parsed = PARSED_CACHE.computeIfAbsent(expression, key -> parseUnchecked(capability, key));
            executeExpression.invoke(capability, parsed, true, false, (Consumer<String>) s -> {
            });
        } catch (Throwable t) {
            TlmStatueAnimation.LOGGER.debug("Statue roaming direct-write failed", t);
        }
    }

    private static Object parseUnchecked(Object animatable, String text) {
        try {
            // 静态解析器：实例参数传 null
            return parseExpression.invoke(null, text);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
