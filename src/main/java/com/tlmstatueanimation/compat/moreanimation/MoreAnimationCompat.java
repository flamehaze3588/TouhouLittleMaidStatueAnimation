package com.tlmstatueanimation.compat.moreanimation;

import com.github.JumDa5he.moreanimation.compat.animation.MaidAnimationData;
import com.github.JumDa5he.moreanimation.config.MoreAnimationConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.Map;

/**
 * moreanimation（酒狐更多动画）软联动门面。
 * <p>
 * 类加载安全约定：所有直接引用 moreanimation 类的代码只存在于内部类 {@link Loaded} 中，
 * 且任何到达 {@code Loaded} 的调用都先经过 {@link #isLoaded()} 判真——JVM 对方法体的类解析是惰性的，
 * 未安装 moreanimation 时 {@code Loaded} 永远不会被加载，本类其余成员（常量、isLoaded、表情清单）
 * 均为纯 Java 构造，可安全调用。外部调用方不应直接触碰 moreanimation 的类。
 */
public final class MoreAnimationCompat {
    public static final String MOD_ID = "moreanimation";
    /** 表情控制终端物品注册名（moreanimation core/ModItems.EXPRESSION_ITEM） */
    public static final ResourceLocation EXPRESSION_ITEM_ID = new ResourceLocation(MOD_ID, "expression_item");

    /**
     * 8 个表情 id（镜像 ExpressionScreen.EXPRESSIONS，该字段为 private 无法引用；
     * 顺序即界面排布顺序）。
     */
    private static final List<String> EXPRESSIONS = List.of(
            "veryangry", "wuyu", "sosad", "provoke", "lips", "sneer", "dizziness", "kuang");

    /** 未安装 moreanimation 时动作的兜底优先级（= MaidAnimationData.PRIORITY_MANUAL 20） */
    private static final int DEFAULT_PRIORITY = 20;

    private MoreAnimationCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** 8 个表情 id 清单（纯字符串常量，任何时候调用都安全）。 */
    public static List<String> expressions() {
        return EXPRESSIONS;
    }

    /** 动作目录：分类（stand/sit/sleep）→ 动作 key 列表（镜像 MaidAnimationData.ACTIONS）。 */
    public static Map<String, List<String>> actions() {
        return isLoaded() ? Loaded.actions() : Map.of();
    }

    /**
     * 动作优先级（镜像 TerminalControlPacket "play" 分支：injured_kneel 用 PRIORITY_INJURED，
     * 其余用 PRIORITY_MANUAL）；未安装 moreanimation 时返回兜底值 20。
     */
    public static int playPriorityFor(String action) {
        return isLoaded() ? Loaded.playPriorityFor(action) : DEFAULT_PRIORITY;
    }

    /** 分类动作启用列表的配置默认值（镜像 MaidAnimationData.enabledActions 的缺 key 分支）。 */
    public static List<String> defaultEnabledActions(String category) {
        return isLoaded() ? Loaded.defaultEnabledActions(category) : List.of();
    }

    /** 自动摸头的配置默认值（MoreAnimationConfig.isAutoPetDefaultEnabled）。 */
    public static boolean defaultAutoPet() {
        return isLoaded() && Loaded.defaultAutoPet();
    }

    /** 自动拥抱的配置默认值（MoreAnimationConfig.isAutoHugDefaultEnabled）。 */
    public static boolean defaultAutoHug() {
        return isLoaded() && Loaded.defaultAutoHug();
    }

    /**
     * 隔离舱：唯一直接引用 moreanimation 类的地方。只有 isLoaded() 为真后外部调用链
     * 才会触达这里，此时 moreanimation 必然已加载，类解析安全。
     */
    private static final class Loaded {
        private static Map<String, List<String>> actions() {
            return MaidAnimationData.ACTIONS;
        }

        private static int playPriorityFor(String action) {
            return "injured_kneel".equals(action)
                    ? MaidAnimationData.PRIORITY_INJURED : MaidAnimationData.PRIORITY_MANUAL;
        }

        private static List<String> defaultEnabledActions(String category) {
            try {
                return MoreAnimationConfig.getEnabledActions(category);
            } catch (Throwable t) {
                // 配置未加载等异常兜底：全开（与 moreanimation 默认配置一致）
                return MaidAnimationData.ACTIONS.getOrDefault(category, List.of());
            }
        }

        private static boolean defaultAutoPet() {
            try {
                return MoreAnimationConfig.isAutoPetDefaultEnabled();
            } catch (Throwable t) {
                return false;
            }
        }

        private static boolean defaultAutoHug() {
            try {
                return MoreAnimationConfig.isAutoHugDefaultEnabled();
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
