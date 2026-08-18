package com.tlmstatueanimation.client.targeting;

import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityStatue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 薄胶水：把真实 Level 的 getBlockEntity 适配成 {@link StatueTargeting.BlockProbe}。
 * instanceof TileEntityStatue / TileEntityGarageKit 判定只在本层做，不含任何过滤逻辑。
 */
public final class StatueTargetingForge {

    private StatueTargetingForge() {
    }

    public static StatueTargeting.BlockProbe probe(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileEntityStatue statue) {
            // getCoreBlockPos 已同步到客户端（coreBlockPos 在 getUpdateTag 中，D3）
            return new StatueTargeting.BlockProbe(true, false, statue.isCoreBlock(),
                    statue.getCoreBlockPos(), statue.getExtraMaidData());
        }
        if (blockEntity instanceof TileEntityGarageKit garageKit) {
            return new StatueTargeting.BlockProbe(false, true, false,
                    pos.immutable(), garageKit.getExtraData());
        }
        return StatueTargeting.BlockProbe.notStatue();
    }
}
