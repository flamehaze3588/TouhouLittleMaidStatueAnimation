package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tlmstatueanimation.TlmStatueAnimation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 雕像/女仆漫游变量（模型配置项）的 YSM 渲染端驱动（§8.22 修复）。
 * <p>
 * 背景：TLM EntityMaidRenderer 每帧把 maid.roamingVars 推给 YSM 的 MaidCapability
 * （updateRoamingVars），但实测反编译 2.6.5 官方 jar：该方法与 getPropertyContainer 均为
 * 空实现——TLM NBT（YsmRoamingVars）→ YSM molang 的链路在女仆侧根本没接通。
 * 而 YSM 轮盘勾选配置项生效走的是另一条路：直接在 animatable 上执行 molang 赋值
 * （parseSimpleExpression + executeExpression，客户端本地）。
 * <p>
 * 此处在同一渲染入口（与 moreanimation 的 YSM 桥相同注入点）HEAD 补执行：
 * 女仆实体的 roamingVars 中每个变量值与上次应用值不同时，组 "v.roaming.<name>=<value>"
 * 执行一次。对雕像（NBT 由本 mod 的包写入）即配置项即时生效；真女仆该字段恒空则零开销。
 * 反射目标均为 YSM 2.6.5 混淆名（与 §8.19 同版本锁定），解析/执行结果按表达式缓存，
 * 失败时记录一次 warn 并回退 YSM 原行为。
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OOoo0o0oO000ooO0Oo00OoOo", remap = false)
public abstract class YsmStatueRoamingMixin {

    /** animatable 基类（com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo）：实体获取与表达式执行 */
    private static final String ANIMATABLE_CLASS = "com.elfmcys.yesstevemodel.o0000OoOooO0oo0o0oooo0Oo";
    /** molang 表达式解析缓存类（官方轮盘字节码实证：parseSimpleExpression 是它的静态方法；
     *  注意 animatable 基类上有同描述符的桩实例方法（恒返回 null），不可用） */
    private static final String PARSER_CLASS = "com.elfmcys.yesstevemodel.O00o0ooOoo00o00o0OOOO0o0";
    private static final String EXPRESSION_CLASS = "com.elfmcys.yesstevemodel.O0o0OOO00000oO00O00oOOo0";

    @Unique
    private static Method tlmStatueAnimation$entityGetter;
    @Unique
    private static Method tlmStatueAnimation$parseExpression;
    @Unique
    private static Method tlmStatueAnimation$executeExpression;
    @Unique
    private static boolean tlmStatueAnimation$reflectFailed;
    /** 解析结果缓存：表达式文本 → 解析产物（值种类极少，进程级缓存） */
    @Unique
    private static final Map<String, Object> tlmStatueAnimation$parsedCache = new ConcurrentHashMap<>();
    /** 每个 animatable 上次应用的变量值（仅在值变化时重新执行赋值） */
    @Unique
    private static final WeakHashMap<Object, Map<String, Float>> tlmStatueAnimation$applied = new WeakHashMap<>();
    /** executeExpression 的 Consumer 不接受 null（实测 NPE），用共享空操作代替 */
    @Unique
    private static final Consumer<String> tlmStatueAnimation$NO_OP_CONSUMER = s -> {
    };

    @Inject(method = "Oo0Oo0o00O00Oo0OOoOOoooo(Lcom/elfmcys/yesstevemodel/o0O0oOooOo0OoOo0oOo00O00;Lnet/minecraft/resources/ResourceLocation;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), remap = false)
    private void tlmStatueAnimation$applyRoamingVars(@Coerce Object animatable, ResourceLocation texture,
                                                     float entityYaw, float partialTick, PoseStack poseStack,
                                                     MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (tlmStatueAnimation$reflectFailed) {
            return;
        }
        try {
            if (tlmStatueAnimation$entityGetter == null) {
                Class<?> base = Class.forName(ANIMATABLE_CLASS);
                tlmStatueAnimation$entityGetter = base.getMethod("OO00OOOOo0Ooo0oo0o0Oo0OO");
                // 解析器是解析缓存类的静态方法（官方轮盘实证），invoke 时实例传 null
                tlmStatueAnimation$parseExpression = Class.forName(PARSER_CLASS)
                        .getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", String.class);
                tlmStatueAnimation$executeExpression = base.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo",
                        Class.forName(EXPRESSION_CLASS), boolean.class, boolean.class, java.util.function.Consumer.class);
            }
            if (!(tlmStatueAnimation$entityGetter.invoke(animatable) instanceof EntityMaid maid)
                    || maid.roamingVars.isEmpty()) {
                return;
            }
            Map<String, Float> appliedMap = tlmStatueAnimation$applied.computeIfAbsent(animatable, k -> new HashMap<>());
            for (var entry : maid.roamingVars.object2FloatEntrySet()) {
                String name = entry.getKey();
                float value = entry.getFloatValue();
                Float applied = appliedMap.get(name);
                if (applied != null && applied == value) {
                    continue;
                }
                String expression = "v.roaming." + name + "=" + value;
                Object parsed = tlmStatueAnimation$parsedCache.computeIfAbsent(expression, key -> {
                    try {
                        return tlmStatueAnimation$parseExpression.invoke(null, key);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                });
                tlmStatueAnimation$executeExpression.invoke(animatable, parsed, true, false, tlmStatueAnimation$NO_OP_CONSUMER);
                appliedMap.put(name, value);
            }
        } catch (Throwable t) {
            tlmStatueAnimation$reflectFailed = true;
            TlmStatueAnimation.LOGGER.warn("YSM roaming-var drive failed; statue model config toggles will not render", t);
        }
    }
}
