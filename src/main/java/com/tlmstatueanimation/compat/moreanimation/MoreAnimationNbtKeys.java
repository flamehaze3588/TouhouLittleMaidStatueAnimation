package com.tlmstatueanimation.compat.moreanimation;

/**
 * moreanimation 写入女仆 {@code getPersistentData()}（= 实体 NBT 的 "ForgeData" 复合标签）的键名常量。
 * 注意：moreanimation 侧这些常量是 MaidAnimationData 的 private 成员，无法编译期引用，
 * 这里按其源码（com.github.JumDa5he.moreanimation.compat.animation.MaidAnimationData /
 * client.gui.ExpressionScreen）逐字镜像；本类为纯常量，不引用任何 moreanimation 类，
 * 因此可在单测与未安装 moreanimation 的环境中安全加载。
 */
public final class MoreAnimationNbtKeys {
    /** Forge 实体持久数据区：Entity NBT 下的 "ForgeData" 子标签（Entity.getPersistentData() 的落盘位置） */
    public static final String FORGE_DATA = "ForgeData";

    /** 允许写入/删除的键前缀：防御伪造包改写雕像 NBT 中其他 mod 的数据 */
    public static final String KEY_PREFIX = "moreanimation_";

    /** 当前表情（ExpressionPacket 写入；空=删除） */
    public static final String EXPRESSION = "moreanimation_expression";
    /** 当前一次性动作 */
    public static final String ACTIVE = "moreanimation_active_action";
    /** 一次性动作开始游戏刻 */
    public static final String ACTIVE_START = "moreanimation_active_start";
    /** 一次性动作截止游戏刻（渲染层 isActive 判定的窗口右端） */
    public static final String ACTIVE_UNTIL = "moreanimation_active_until";
    /** 一次性动作优先级 */
    public static final String ACTIVE_PRIORITY = "moreanimation_active_priority";
    /** 一次性动作是否锁移动（雕像无移动可锁，恒 false） */
    public static final String ACTIVE_LOCK_MOVEMENT = "moreanimation_active_lock_movement";
    /** 分类动作启用列表键前缀（+"stand"/"sit"/"sleep"），值为字符串 ListTag */
    public static final String ENABLED_PREFIX = "moreanimation_enabled_";
    /** 重伤自动动作开关及其"已显式设置"标记 */
    public static final String INJURED_AUTO = "moreanimation_injured_auto";
    public static final String INJURED_AUTO_SET = "moreanimation_injured_auto_set";
    /** 自动摸头开关及其设置标记 */
    public static final String AUTO_PET = "moreanimation_auto_pet";
    public static final String AUTO_PET_SET = "moreanimation_auto_pet_set";
    /** 自动拥抱开关及其设置标记 */
    public static final String AUTO_HUG = "moreanimation_auto_hug";
    public static final String AUTO_HUG_SET = "moreanimation_auto_hug_set";
    /** 随机睡姿开关及其设置标记 */
    public static final String RANDOM_SLEEP_POSE = "moreanimation_random_sleep_pose";
    public static final String RANDOM_SLEEP_POSE_SET = "moreanimation_random_sleep_pose_set";
    /** 形态模式（酒狐模型强制人形/狐形），int，取值见下方 FORM_* */
    public static final String FORM_MODE = "moreanimation_form_mode";

    /**
     * 【本 mod 自有键，非 moreanimation 原生】雕像坐姿变体的记忆标记（§8.16）：
     * 蹲下切换至坐姿时服务端 50% 随机写入 "sit2"（moreanimation 新增坐姿）；空/缺失 = 默认坐姿。
     * 实际播放走原生 ACTIVE 通道（ACTIVE="sit2" + UNTIL=Long.MAX_VALUE），gecko 与 YSM 桥通吃；
     * 本键用于在一次性动作过期后恢复基础姿势（见 StatueActionPhaseTicker）。
     * 带 moreanimation_ 前缀以通过 StatueForgeDataMerge 白名单。
     */
    public static final String STATUE_BASE_POSE = "moreanimation_statue_base_pose";
    /** 坐姿变体候选（镜像 moreanimation GameLostAnimation.SIT_BASE_VARIANTS 的 "sit2"） */
    public static final String SIT_VARIANT_SIT2 = "sit2";

    /** 镜像 MaidAnimationData.FORM_AUTO/HUMAN/FOX（0/1/2） */
    public static final int FORM_AUTO = 0;
    public static final int FORM_HUMAN = 1;
    public static final int FORM_FOX = 2;
    /** 镜像 MaidAnimationData.PRIORITY_RANDOM（基础姿势/随机动作的优先级） */
    public static final int PRIORITY_RANDOM = 10;

    private MoreAnimationNbtKeys() {
    }
}
