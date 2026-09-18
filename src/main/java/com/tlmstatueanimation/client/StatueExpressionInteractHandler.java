package com.tlmstatueanimation.client;

import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.targeting.StatueRef;
import com.tlmstatueanimation.client.targeting.StatueTargeting;
import com.tlmstatueanimation.client.targeting.StatueTargetingForge;
import com.tlmstatueanimation.compat.moreanimation.MoreAnimationCompat;
import com.tlmstatueanimation.compat.moreanimation.StatueExpressionScreen;
import com.tlmstatueanimation.network.NetworkHandler;
import com.tlmstatueanimation.network.message.C2SToggleStatueSitPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 雕像/手办右键交互入口：
 * <ul>
 *   <li>主手持 moreanimation:expression_item（动作控制终端）右键 → 打开复刻的表情/动作控制屏
 *       （{@link StatueExpressionScreen}）；</li>
 *   <li>蹲下 + 右键（终端或<b>原版木棍</b>均可）→ 不开屏，直接发送 {@link C2SToggleStatueSitPacket}
 *       切换雕像站姿/坐姿。木棍路径不依赖 moreanimation——姿势切换是本 mod 自有功能（§8.21）。</li>
 * </ul>
 * <p>
 * 手持这些物品右键雕像方块本体不触发 moreanimation 与 TLM 的任何既有逻辑（ExpressionItem
 * 只实现了 interactLivingEntity，雕像/手办方块的 use 对本物品均 PASS；木棍对雕像无原版行为），
 * 故无需取消事件。本类不引用任何 moreanimation 类：物品判定用注册名比较，屏幕实例化被
 * {@link MoreAnimationCompat#isLoaded()} 门控（能持有该物品说明 mod 必然已装，双保险）。
 */
@Mod.EventBusSubscriber(modid = TlmStatueAnimation.MOD_ID, value = Dist.CLIENT)
public final class StatueExpressionInteractHandler {

    private StatueExpressionInteractHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 开屏是物理客户端行为（事件双侧都会触发），且只认主手
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        boolean isTerminal = MoreAnimationCompat.EXPRESSION_ITEM_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        boolean isStick = stack.is(Items.STICK);
        if (!isTerminal && !isStick) {
            return;
        }
        // 终端开屏需 moreanimation 在场（屏幕类引用其 MaidAnimationData）；木棍切换姿势不需要
        if (isTerminal && !MoreAnimationCompat.isLoaded()) {
            return;
        }
        // moreanimation 对 TLM 默认 bedrock 模型也生效，这里用不过滤 YSM 的解析变体
        StatueTargeting.resolveAnyMaid(event.getPos(), pos -> StatueTargetingForge.probe(event.getLevel(), pos))
                .ifPresent(ref -> {
                    // 蹲下 + 右键：不开屏，直接发 C2S 包切换雕像站姿/坐姿（终端/木棍均可）
                    if (event.getEntity().isShiftKeyDown()) {
                        NetworkHandler.sendToServer(new C2SToggleStatueSitPacket(ref.corePos()));
                        return;
                    }
                    // 普通右键：仅终端开屏；木棍无动作
                    if (isTerminal) {
                        openScreen(ref);
                    }
                });
    }

    private static void openScreen(StatueRef ref) {
        // StatueExpressionScreen 引用了 moreanimation 的 MaidAnimationData（经 MoreAnimationCompat）；
        // 执行到此处说明 moreanimation 已安装（物品存在且 isLoaded 为真），类加载安全
        Minecraft.getInstance().setScreen(new StatueExpressionScreen(ref.corePos(), ref.maidNbt()));
    }
}
