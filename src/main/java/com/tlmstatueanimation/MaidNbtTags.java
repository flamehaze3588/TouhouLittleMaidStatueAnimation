package com.tlmstatueanimation;

/**
 * 女仆 NBT tag 名常量。
 * 注意：刻意不引用 TLM EntityMaid 的常量——EntityMaid 类加载重，单元测试环境会炸；
 * 这些字符串与 TLM 上游约定保持一致即可（依据见 docs/TLM-YSM-Linkage-Principle.md §2.1、§5.5 D5）。
 */
public final class MaidNbtTags {
    /** TLM EntityMaid.IS_YSM_MODEL_TAG */
    public static final String IS_YSM_MODEL = "IsYsmModel";
    /** TLM EntityMaid.YSM_MODEL_ID_TAG */
    public static final String YSM_MODEL_ID = "YsmModelId";
    /** TLM EntityMaid.YSM_ROULETTE_ANIM_TAG：轮盘动作 key */
    public static final String YSM_ROULETTE_ANIM = "YsmRouletteAnim";
    /** TLM EntityMaid.YSM_ROAMING_VARS_TAG：YSM 漫游变量（模型配置项，§8.22） */
    public static final String YSM_ROAMING_VARS = "YsmRoamingVars";
    /** TLM EntityMaid.YSM_ROAMING_UPDATE_FLAG_TAG：漫游变量更新计数（TLM 同步语义，写变量时 +1） */
    public static final String YSM_ROAMING_UPDATE_FLAG = "YsmRoamingUpdateFlag";
    /** 本附属 mod 自定义的播放标志（TLM 的 rouletteAnimPlaying 不持久化，见文档 §5.5 D5/D6） */
    public static final String STATUE_ROULETTE_PLAYING = "StatueRoulettePlaying";
    /** 原版 TamableAnimal 的坐姿持久化键（TLM 女仆继承）；load 时据此恢复坐下姿势 */
    public static final String SITTING = "Sitting";
    /**
     * 本附属 mod 自定义标记（§8.17）：蹲下+右键切换过姿势的 YSM 雕像脱离内置 "statue" 姿势，
     * 走与真实女仆一致的渲染链（普通站姿/坐姿）；仅客户端渲染读取，不影响真女仆
     */
    public static final String STATUE_POSE_INTERACTIVE = "StatuePoseInteractive";

    private MaidNbtTags() {
    }
}
