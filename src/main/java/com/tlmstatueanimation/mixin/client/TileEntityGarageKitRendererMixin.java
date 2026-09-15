package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityGarageKitRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.client.StatueAnimFreezeControl;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 与 TileEntityStatueRendererMixin 同理：手办（Garage Kit）播放轮盘动作时
 * renderState 从 GARAGE_KIT 改为 ENTITY，避免 YSM 强制循环 "garage_kit" 姿势动画（§8.7）；
 * 并同样把假女仆实体坐标修正为手办方块坐标，修复 YSM 动作自带音乐在拍照坐标播放的问题（§8.11）。
 */
@Mixin(TileEntityGarageKitRenderer.class)
public abstract class TileEntityGarageKitRendererMixin {

    @Redirect(method = "renderEntity",
            at = @At(value = "FIELD",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;renderState:Lcom/github/tartaricacid/touhoulittlemaid/api/client/render/MaidRenderState;",
                    opcode = Opcodes.PUTFIELD),
            remap = false)
    private void tlmStatueAnimation$overrideRenderState(EntityMaid maid, MaidRenderState original,
                                                        TileEntityGarageKit te, PoseStack poseStack,
                                                        MultiBufferSource bufferIn, int combinedLightIn,
                                                        CompoundTag data, Level world, EntityType<?> type) {
        BlockPos pos = te.getBlockPos();
        maid.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // 同 TileEntityStatueRendererMixin：clearMaidDataResidue 每帧清坐姿，这里按 NBT 重新声明（§8.13）
        maid.setInSittingPose(data.getBoolean(MaidNbtTags.SITTING));
        // §8.17：蹲下切换过姿势的 YSM 手办脱离内置 statue 姿势（避免与坐姿叠加成"站姿半身入地"）
        if (maid.isYsmModel() && (maid.rouletteAnimPlaying || data.getBoolean(MaidNbtTags.STATUE_POSE_INTERACTIVE))) {
            maid.renderState = MaidRenderState.ENTITY;
            return;
        }
        maid.renderState = original;
    }

    /**
     * 冻结控制（§8.12/§8.14）：同 TileEntityStatueRendererMixin——解冻判定委托给
     * StatueAnimFreezeControl（moreanimation 活跃恒解冻 + 状态变化宽限窗）。
     */
    @Redirect(method = "renderEntity",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isYsmModel()Z"),
            remap = false)
    private boolean tlmStatueAnimation$unfreezeForMoreAnimation(EntityMaid maid,
                                                                TileEntityGarageKit te, PoseStack poseStack,
                                                                MultiBufferSource bufferIn, int combinedLightIn,
                                                                CompoundTag data, Level world, EntityType<?> type) {
        if (maid.isYsmModel()) {
            return true;
        }
        return StatueAnimFreezeControl.shouldUnfreeze(maid, data, world);
    }
}
