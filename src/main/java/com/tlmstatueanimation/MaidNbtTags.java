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
    /** 本附属 mod 自定义的播放标志（TLM 的 rouletteAnimPlaying 不持久化，见文档 §5.5 D5/D6） */
    public static final String STATUE_ROULETTE_PLAYING = "StatueRoulettePlaying";
    /** 原版 TamableAnimal 的坐姿持久化键（TLM 女仆继承）；load 时据此恢复坐下姿势 */
    public static final String SITTING = "Sitting";

    private MaidNbtTags() {
    }
}
