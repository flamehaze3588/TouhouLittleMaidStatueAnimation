package com.tlmstatueanimation.compat.moreanimation;

/*
 * UI 复刻自 moreanimation（MIT License，Copyright (c) 2025 TartaricAcid），发包逻辑替换为雕像 NBT 写入。
 * 原文件：com.github.JumDa5he.moreanimation.client.gui.ExpressionScreen
 */

import com.tlmstatueanimation.network.NetworkHandler;
import com.tlmstatueanimation.network.message.C2SStatueExpressionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 雕像版表情/动作控制屏：UI 结构复刻 moreanimation 的 ExpressionScreen（出处见文件头注释），
 * 所有原发包点（ExpressionPacket / TerminalControlPacket）替换为本 mod 的
 * {@link C2SStatueExpressionPacket}——键值最终写入雕像女仆 NBT 的 ForgeData 区，
 * 渲染器每帧 entity.load(data) 灌进假女仆后由 moreanimation 的渲染层自动消费。
 * <p>
 * 与原版的必要差异：
 * <ul>
 * <li>初始状态不再向服务端发 "request"，直接读准星雕像客户端已同步的 maidNbt.ForgeData；</li>
 * <li>"立即播放"只发动作 key，start/until/priority 由服务端用 gameTime 与
 * moreanimation 的 MaidAnimationData.duration 补齐（锁移动对雕像恒 false）；</li>
 * <li>auto_pet / auto_hug / injured_auto / random_sleep_pose 这类 AI 开关对雕像
 * （装饰方块实体）无实际驱动，但照常持久化——雕像 NBT 若未来被还原为女仆实体可直接继承；</li>
 * <li>互动动作（pet_owner/pet_maid/hug_owner/hug_maid）在原版中需要真实女仆与目标实体组成双人会话，雕像无此概念，
 * 简化为播放该互动中女仆侧的单人可见动作（{@link StatueExpressionState#interactionSoloAction}）。</li>
 * </ul>
 * 本类只会在 moreanimation 已安装时被实例化（入口见 StatueExpressionInteractHandler），
 * 翻译键直接复用 moreanimation 的 gui.moreanimation.*。
 */
public class StatueExpressionScreen extends Screen {
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 228;
    private static final int BLUE = 0xFF4EC9F5;
    private static final int GREEN = 0xFF55D68B;
    private static final int RED = 0xFFEB6A73;

    private final BlockPos corePos;
    private final Map<String, List<String>> actions;
    private final Map<String, Integer> masks = new LinkedHashMap<>();
    private Tab tab = Tab.EXPRESSIONS;
    private boolean injuredAuto;
    private boolean autoPet;
    private boolean autoHug;
    private boolean randomSleepPose;
    private int formMode;

    public StatueExpressionScreen(BlockPos corePos, CompoundTag maidNbt) {
        super(Component.translatable("gui.moreanimation.terminal.title"));
        this.corePos = corePos;
        this.actions = MoreAnimationCompat.actions();
        CompoundTag forgeData = maidNbt.contains(MoreAnimationNbtKeys.FORGE_DATA, Tag.TAG_COMPOUND)
                ? maidNbt.getCompound(MoreAnimationNbtKeys.FORGE_DATA) : new CompoundTag();
        this.actions.forEach((category, catalog) -> this.masks.put(category, StatueExpressionState.maskOf(
                StatueExpressionState.enabledActions(forgeData, category, catalog,
                        MoreAnimationCompat.defaultEnabledActions(category)), catalog)));
        this.injuredAuto = StatueExpressionState.injuredAuto(forgeData);
        this.autoPet = StatueExpressionState.autoPet(forgeData, MoreAnimationCompat.defaultAutoPet());
        this.autoHug = StatueExpressionState.autoHug(forgeData, MoreAnimationCompat.defaultAutoHug());
        this.randomSleepPose = StatueExpressionState.randomSleepPose(forgeData);
        this.formMode = StatueExpressionState.formMode(forgeData);
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int left = width / 2 - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;
        int tabX = left + 14;
        int tabY = top + 38;
        int tabW = 58;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab value = Tab.values()[i];
            addRenderableWidget(new StatueTerminalButton(tabX + i * 62, tabY, tabW, 22,
                    Component.translatable(value.lang), () -> { tab = value; rebuild(); }, BLUE, tab == value));
        }
        int contentX = left + 18;
        int contentY = top + 72;
        if (tab == Tab.EXPRESSIONS) {
            buildExpressions(contentX, contentY);
        } else if (tab == Tab.OTHER) {
            buildOther(contentX, contentY);
        } else if (tab == Tab.INTERACTION) {
            buildInteractions(contentX, contentY);
        } else if (tab == Tab.SLEEP) {
            buildSleep(contentX, contentY);
        } else {
            buildCategory(contentX, contentY, tab.category);
        }
    }

    private void buildExpressions(int x, int y) {
        List<String> expressions = MoreAnimationCompat.expressions();
        for (int i = 0; i < expressions.size(); i++) {
            String expression = expressions.get(i);
            addAction(x + (i % 2) * 186, y + (i / 2) * 27, 178,
                    "gui.moreanimation.action." + expression, () -> sendExpression(expression));
        }
        addAction(x, y + 112, 364, "gui.moreanimation.stop", () -> sendExpression("stop"));
    }

    private void buildCategory(int x, int y, String category) {
        List<String> catalog = this.actions.getOrDefault(category, List.of());
        for (int i = 0; i < catalog.size(); i++) {
            String action = catalog.get(i);
            boolean enabled = (masks.getOrDefault(category, 0) & (1 << i)) != 0;
            int by = y + i * 32;
            addToggle(x, by, 258, Component.translatable(
                    enabled ? "gui.moreanimation.auto.on" : "gui.moreanimation.auto.off",
                    Component.translatable("gui.moreanimation.action." + safeKey(action))), enabled,
                    () -> toggleAction(category, catalog, action, !enabled));
            addAction(x + 266, by, 98, "gui.moreanimation.play_now", () -> playNow(action));
        }
    }

    private void buildSleep(int x, int y) {
        addToggle(x, y, 364, Component.translatable(randomSleepPose
                        ? "gui.moreanimation.random_sleep_pose.on"
                        : "gui.moreanimation.random_sleep_pose.off"), randomSleepPose,
                () -> {
                    // 雕像没有睡姿 AI，照常持久化（见类注释）
                    randomSleepPose = !randomSleepPose;
                    send(StatueExpressionState.boolSwitchKeys(MoreAnimationNbtKeys.RANDOM_SLEEP_POSE_SET,
                            MoreAnimationNbtKeys.RANDOM_SLEEP_POSE, randomSleepPose), List.of());
                    rebuild();
                });
        buildCategory(x, y + 32, "sleep");
    }

    private void buildInteractions(int x, int y) {
        // auto_pet/auto_hug 是女仆 AI 开关，对雕像无实际驱动，照常持久化（见类注释）
        addToggle(x, y, 178, Component.translatable(autoPet
                        ? "gui.moreanimation.auto_pet.on" : "gui.moreanimation.auto_pet.off"), autoPet,
                () -> {
                    autoPet = !autoPet;
                    send(StatueExpressionState.boolSwitchKeys(MoreAnimationNbtKeys.AUTO_PET_SET,
                            MoreAnimationNbtKeys.AUTO_PET, autoPet), List.of());
                    rebuild();
                });
        addToggle(x + 186, y, 178, Component.translatable(autoHug
                        ? "gui.moreanimation.auto_hug.on" : "gui.moreanimation.auto_hug.off"), autoHug,
                () -> {
                    autoHug = !autoHug;
                    send(StatueExpressionState.boolSwitchKeys(MoreAnimationNbtKeys.AUTO_HUG_SET,
                            MoreAnimationNbtKeys.AUTO_HUG, autoHug), List.of());
                    rebuild();
                });
        String[] interactions = {"pet_owner", "pet_maid", "hug_owner", "hug_maid"};
        for (int i = 0; i < interactions.length; i++) {
            String interaction = interactions[i];
            addAction(x + (i % 2) * 186, y + 38 + (i / 2) * 31, 178,
                    "gui.moreanimation.interaction." + interaction,
                    () -> playInteraction(interaction));
        }
        graphicsHintButton(x, y + 108);
    }

    private void graphicsHintButton(int x, int y) {
        StatueTerminalButton hint = new StatueTerminalButton(x, y, 364, 22,
                Component.translatable("gui.moreanimation.interaction.cooldown"), () -> {}, 0xFF64768B, false);
        hint.active = false;
        addRenderableWidget(hint);
    }

    private void buildOther(int x, int y) {
        // injured_auto 是受伤倒地 AI 开关，对雕像无实际驱动，照常持久化（见类注释）
        addToggle(x, y, 258, Component.translatable(injuredAuto
                        ? "gui.moreanimation.injured.on" : "gui.moreanimation.injured.off"), injuredAuto,
                () -> {
                    injuredAuto = !injuredAuto;
                    send(StatueExpressionState.boolSwitchKeys(MoreAnimationNbtKeys.INJURED_AUTO_SET,
                            MoreAnimationNbtKeys.INJURED_AUTO, injuredAuto), List.of());
                    rebuild();
                });
        addAction(x + 266, y, 98, "gui.moreanimation.play_now", () -> playNow("injured_kneel"));
        addAction(x, y + 36, 364, formModeKey(), () -> {
            formMode = (formMode + 1) % (MoreAnimationNbtKeys.FORM_FOX + 1);
            send(StatueExpressionState.formModeKeys(formMode), List.of());
            rebuild();
        });
    }

    private String formModeKey() {
        return switch (formMode) {
            case MoreAnimationNbtKeys.FORM_HUMAN -> "gui.moreanimation.form.human";
            case MoreAnimationNbtKeys.FORM_FOX -> "gui.moreanimation.form.fox";
            default -> "gui.moreanimation.form.auto";
        };
    }

    private void addAction(int x, int y, int w, String key, Runnable press) {
        addRenderableWidget(new StatueTerminalButton(x, y, w, 24, Component.translatable(key), press, BLUE, false));
    }

    private void addToggle(int x, int y, int w, Component text, boolean enabled, Runnable press) {
        addRenderableWidget(new StatueTerminalButton(x, y, w, 24, text, press, enabled ? GREEN : RED, enabled));
    }

    private String safeKey(String action) {
        return "!??!".equals(action) ? "question" : action;
    }

    // ---------- 分发：原版此处发 ExpressionPacket/TerminalControlPacket，这里改为本 mod 的 C2S 包 ----------

    private void send(CompoundTag setKeys, List<String> removeKeys) {
        NetworkHandler.sendToServer(new C2SStatueExpressionPacket(corePos, setKeys, removeKeys));
    }

    private void sendExpression(String expression) {
        if ("stop".equals(expression)) {
            // §8.18：雕像动作无限循环，"停止"按钮对雕像语义为表情+动作全停
            send(new CompoundTag(), StatueExpressionState.stopAllRemoveKeys());
        } else {
            send(StatueExpressionState.expressionKeys(expression), List.of());
        }
    }

    private void toggleAction(String category, List<String> catalog, String action, boolean enable) {
        List<String> current = StatueExpressionState.enabledFromMask(masks.getOrDefault(category, 0), catalog);
        CompoundTag keys = StatueExpressionState.categoryEnabledKeys(category, catalog, current, action, enable);
        if (keys == null) {
            return;
        }
        send(keys, List.of());
        // 原版靠服务端回包刷新掩码；雕像的状态就是这段 NBT，本地即时翻转即可
        int bit = 1 << catalog.indexOf(action);
        int mask = masks.getOrDefault(category, 0);
        masks.put(category, enable ? mask | bit : mask & ~bit);
        rebuild();
    }

    private void playNow(String action) {
        send(StatueExpressionState.playKeys(action), List.of());
    }

    private void playInteraction(String interaction) {
        String solo = StatueExpressionState.interactionSoloAction(interaction);
        if (!solo.isEmpty()) {
            playNow(solo);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = width / 2 - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;
        graphics.fill(left - 3, top - 3, left + PANEL_W + 3, top + PANEL_H + 3, 0x99000000);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE8121B27);
        graphics.fill(left, top, left + PANEL_W, top + 2, BLUE);
        graphics.fill(left + 12, top + 64, left + PANEL_W - 12, top + 65, 0xFF314559);
        graphics.drawCenteredString(font, title, width / 2, top + 15, 0xFFF4F7FB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        EXPRESSIONS("gui.moreanimation.tab.expressions", ""),
        STAND("gui.moreanimation.tab.stand", "stand"),
        SIT("gui.moreanimation.tab.sit", "sit"),
        SLEEP("gui.moreanimation.tab.sleep", "sleep"),
        INTERACTION("gui.moreanimation.tab.interaction", "interaction"),
        OTHER("gui.moreanimation.tab.other", "other");
        private final String lang;
        private final String category;
        Tab(String lang, String category) {
            this.lang = lang;
            this.category = category;
        }
    }
}
