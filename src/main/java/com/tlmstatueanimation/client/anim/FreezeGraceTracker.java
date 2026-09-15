package com.tlmstatueanimation.client.anim;

/**
 * 冻结雕像的"宽限解冻窗"状态机（纯逻辑，无 MC 依赖，可单测，§8.14）。
 * <p>
 * 背景：非 YSM 雕像被 TLM 强制 tickCount=0（时间冻结），geckolib 控制器切换动画时的
 * 过渡插值需要时间前进才能完成——冻结态下单帧强制求值无法完成过渡（坐姿切换不即时、
 * 骨骼残影）。因此不在渲染端强制求值，而是给雕像一个短暂的解冻窗口：
 * 侦测到 <b>坐姿翻转</b> 或 <b>moreanimation 活跃状态下降沿</b>（一次性动作刚过期）时，
 * 解冻 {@value #GRACE_TICKS} tick，让谓词逐帧求值、过渡正常完成，随后重新冻结在落定姿势上。
 * <p>
 * 每个雕像（缓存假女仆）一个实例，由 StatueAnimFreezeControl 按实体持有。
 */
public final class FreezeGraceTracker {
    /** 宽限窗口长度（tick）：过渡插值仅需 2 tick，10 tick 留足余量且肉眼无感 */
    public static final long GRACE_TICKS = 10;

    private boolean initialized;
    private boolean lastSitting;
    private boolean lastActive;
    private long graceUntil = Long.MIN_VALUE;

    /**
     * 每帧调用一次，返回本帧雕像是否应解冻（tickCount=gameTime）。
     *
     * @param sitting 当前 NBT 坐姿（Sitting 键）
     * @param active  当前 moreanimation 是否活跃（表情非空 / 一次性动作未过期）
     * @param now     当前客户端游戏时间（level.getGameTime()）
     */
    public boolean shouldUnfreeze(boolean sitting, boolean active, long now) {
        if (this.initialized) {
            boolean sittingFlipped = sitting != this.lastSitting;
            boolean activeFell = this.lastActive && !active;
            if (sittingFlipped || activeFell) {
                this.graceUntil = now + GRACE_TICKS;
            }
        }
        this.initialized = true;
        this.lastSitting = sitting;
        this.lastActive = active;
        return active || now < this.graceUntil;
    }
}
