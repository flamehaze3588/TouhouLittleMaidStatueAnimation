package com.tlmstatueanimation.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityGarageKitRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGarageKit;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationNbtKeys;
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
        if (maid.isYsmModel() && maid.rouletteAnimPlaying) {
            maid.renderState = MaidRenderState.ENTITY;
            return;
        }
        maid.renderState = original;
    }

    /**
     * moreanimation 联动（§8.12）：同 TileEntityStatueRendererMixin 的解冻逻辑——
     * 假女仆 ForgeData 带 moreanimation 活跃状态时视同 YSM 模型（tickCount=gameTime），
     * 让 TLM 皮肤包手办的 moreanimation 表情/动作不再定格在第 0 帧。
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
        CompoundTag persistentData = maid.getPersistentData();
        boolean moreAnimationActive = !persistentData.getString(MoreAnimationNbtKeys.EXPRESSION).isEmpty()
                || (persistentData.contains(MoreAnimationNbtKeys.ACTIVE)
                && persistentData.getLong(MoreAnimationNbtKeys.ACTIVE_UNTIL) > world.getGameTime());
        return moreAnimationActive;
    }
}
