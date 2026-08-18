package com.tlmstatueanimation.client.anim;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 动画施加决策状态机（D6，纯逻辑，无 MC 依赖，可单测）。
 * 输入为 NBT 中的目标状态与上次已施加的状态，输出本次应采取的动作与新的已施加状态。
 * dirty-once：目标状态不变时返回 NONE，绝不重复置位。
 */
public final class AnimationDecision {

    private AnimationDecision() {
    }

    public enum Action {
        NONE, PLAY, STOP
    }

    /**
     * @param action   本次应采取的动作
     * @param animKey  新的已施加动作 key（lastApplied 状态的一部分）
     * @param playing  新的已施加播放标志（lastApplied 状态的一部分）
     */
    public record Decision(Action action, @Nullable String animKey, boolean playing) {
    }

    /**
     * @param nbtAnimKey   NBT 中的动作 key（可能为空串）
     * @param nbtPlaying   NBT 中的播放标志
     * @param lastKey      上次施加的动作 key（首次为 null）
     * @param lastPlaying  上次施加的播放标志（首次为 false）
     */
    public static Decision decide(String nbtAnimKey, boolean nbtPlaying,
                                  @Nullable String lastKey, boolean lastPlaying) {
        if (nbtPlaying) {
            // 防御：空 key 没有可播放的动画，忽略且不变更已施加状态
            if (nbtAnimKey.isEmpty()) {
                return new Decision(Action.NONE, lastKey, lastPlaying);
            }
            if (lastPlaying && Objects.equals(nbtAnimKey, lastKey)) {
                return new Decision(Action.NONE, lastKey, true);
            }
            return new Decision(Action.PLAY, nbtAnimKey, true);
        }
        if (lastPlaying) {
            return new Decision(Action.STOP, nbtAnimKey, false);
        }
        return new Decision(Action.NONE, nbtAnimKey.isEmpty() ? lastKey : nbtAnimKey, false);
    }
}
