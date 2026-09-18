package com.tlmstatueanimation.client;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * 雕像假女仆的独立身份（§8.20）。
 * <p>
 * 背景：雕像 NBT 是原女仆的完整实体快照，含 UUID；TLM 渲染器每帧 `entity.load(data)`
 * 会把快照 UUID 装到假女仆身上——若原女仆（照片放出的同 UUID 个体）还活着，
 * 任何按 UUID/身份键控的 mod 运行时状态都会串台（实测：原女仆死亡时雕像假女仆的
 * deadOrDying 被置位，YSM 播放死亡动画并定格狐狸形态）。
 * 渲染前把假女仆 UUID 换成按雕像坐标派生的稳定值，切断该关联。
 * 同一雕像 UUID 恒定（帧间/会话间不变），不同雕像互不相同。
 */
public final class StatueIdentity {
    /** 高 64 位魔数（"TLMSA_UD"），与真实实体随机 UUID 碰撞概率忽略不计 */
    private static final long MAGIC = 0x544C4D53415F5544L;

    private StatueIdentity() {
    }

    public static UUID forPos(BlockPos pos) {
        return new UUID(MAGIC, pos.asLong());
    }
}
