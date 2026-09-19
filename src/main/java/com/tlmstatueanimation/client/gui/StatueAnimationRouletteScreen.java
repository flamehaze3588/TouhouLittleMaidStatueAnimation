package com.tlmstatueanimation.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tlmstatueanimation.client.StatueAnimationKeys;
import com.tlmstatueanimation.MaidNbtTags;
import com.tlmstatueanimation.client.StatueRoamingDriver;
import com.tlmstatueanimation.client.model.ModelAnimations;
import com.tlmstatueanimation.client.model.RoamingAssignments;
import com.tlmstatueanimation.client.model.YsmModelScanner;
import com.tlmstatueanimation.client.model.ysmfile.YsmBinaryModelWalker;
import com.tlmstatueanimation.network.NetworkHandler;
import com.tlmstatueanimation.network.message.C2SPlayStatueAnimationPacket;
import com.tlmstatueanimation.network.message.C2SStatueRoamingVarPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 雕像动作轮盘屏（复刻 YSM 2.x 轮盘视觉风格：几何、配色、交互对齐）。
 * 轮盘中心偏左（右侧放页码控件区），不暗化背景，轮盘浮在游戏画面上；
 * 左键点扇区=播放，点中心停止钮=停止，滚轮/页码按钮翻页，ESC/轮盘键关屏；
 * 点轮盘外空白与右键均不关屏。打开时游戏不暂停（isPauseScreen=false）。
 * <p>
 * classify 子菜单（对齐 YSM 2.6.5 navigationStack）：'#' 前缀键条目为子菜单入口
 *（标签红色渲染），点击进入并压栈（页码归 0，最深 5 级）；'#return' 键条目与右侧
 * "返回"按钮弹栈回上一层（栈底再返回 = 关屏）；子菜单内右上画面包屑 "a > b"（id 原文）；
 * 每层页码独立记忆。导航状态机在 {@link RouletteNavigation}（纯逻辑，可单测）。
 */
public class StatueAnimationRouletteScreen extends Screen {
    /** 轮盘中心相对屏幕中心的偏移（偏左，右侧留给页码控件） */
    private static final int CENTER_OFFSET_X = -70;
    private static final int CENTER_OFFSET_Y = -8;

    /** 扇段内/外半径；悬停时外半径扩大 */
    private static final int INNER_RADIUS = 25;
    private static final int OUTER_RADIUS = 105;
    private static final int OUTER_RADIUS_HOVER = 115;

    /** 标签圆心半径与自动换行宽度 */
    private static final int LABEL_RADIUS = 65;
    private static final int LABEL_WRAP_WIDTH = 50;

    /** 中心停止按钮（相对轮盘中心：x-20, y-10, 40x20） */
    private static final int STOP_X = -20;
    private static final int STOP_Y = -10;
    private static final int STOP_W = 40;
    private static final int STOP_H = 20;

    /** 页码控件（相对轮盘中心）：< 按钮、> 按钮、页码信息条 */
    private static final int PREV_X = 125;
    private static final int NEXT_X = 240;
    private static final int PAGE_BTN_Y = -102;
    private static final int PAGE_BTN_SIZE = 30;
    private static final int PAGE_BAR_LEFT = 157;
    private static final int PAGE_BAR_RIGHT = 238;
    private static final int PAGE_BAR_TOP = -87;
    private static final int PAGE_BAR_BOTTOM = -72;
    /** 滚轮翻页仅在此 x 界限左侧生效（相对轮盘中心） */
    private static final int SCROLL_X_LIMIT = 110;

    /** 返回按钮（相对轮盘中心：x+125, y-70, 145x22）；面包屑文字（相对轮盘中心：x+195, y-100） */
    private static final int RETURN_X = 125;
    private static final int RETURN_Y = -70;
    private static final int RETURN_W = 145;
    private static final int RETURN_H = 22;
    private static final int BREADCRUMB_X = 195;
    private static final int BREADCRUMB_Y = -100;

    /** 配置面板（相对轮盘中心）：左上 (125,-95)，宽 250，行高 18 */
    private static final int CONFIG_X = 125;
    private static final int CONFIG_Y = -95;
    private static final int CONFIG_W = 250;
    private static final int CONFIG_ROW_H = 18;

    private static final int COLOR_SEGMENT = 0x90000000;
    private static final int COLOR_SEGMENT_HOVER = 0xF0FFB100;
    private static final int COLOR_LABEL = 0xF3EFE0;
    private static final int COLOR_FLAT_BUTTON = 0x90000000;
    private static final int COLOR_FLAT_BUTTON_HOVER = 0xC0404040;
    private static final int COLOR_PAGE_BAR = 0xCF000000;
    private static final int COLOR_PAGE_TEXT = 0x55FFFF;

    private final BlockPos corePos;
    private final String modelId;
    private final RouletteNavigation nav;
    private final ModelAnimations animations;
    /** 配置项当前值：初值取自雕像 NBT 的 YsmRoamingVars，勾选后本地即时覆盖（§8.22） */
    private final Map<String, Float> localVars = new HashMap<>();
    /** 打开中的配置按钮（null = 轮盘模式；非 null = 配置面板模式） */
    private YsmBinaryModelWalker.YsmConfigButton activeConfig;

    public StatueAnimationRouletteScreen(BlockPos corePos, String modelId, ModelAnimations animations, CompoundTag maidNbt) {
        super(Component.empty());
        this.corePos = corePos;
        this.modelId = modelId;
        this.nav = new RouletteNavigation(animations);
        this.animations = animations;
        CompoundTag vars = maidNbt.getCompound(MaidNbtTags.YSM_ROAMING_VARS);
        for (String key : vars.getAllKeys()) {
            this.localVars.put(key, vars.getFloat(key));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int centerX() {
        return this.width / 2 + CENTER_OFFSET_X;
    }

    private int centerY() {
        return this.height / 2 + CENTER_OFFSET_Y;
    }

    private List<YsmModelScanner.AnimEntry> currentPageEntries() {
        return RouletteLayout.pageEntries(this.nav.currentEntries(), this.nav.currentPage());
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void playClickSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** 返回上一级；配置面板打开时先关面板；已在根层则关屏（栈底再返回 = 关屏，对齐 YSM） */
    private void navigateBack() {
        playClickSound();
        if (this.activeConfig != null) {
            this.activeConfig = null;
            return;
        }
        if (!this.nav.back()) {
            this.onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            // 右键不关屏（对齐 YSM）
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int centerX = centerX();
        int centerY = centerY();
        // 右侧返回按钮（任何层都可点；配置面板打开时按钮在面板下方，点击 = 关面板；根层点击 = 关屏）
        if (inRect(mouseX, mouseY, centerX + RETURN_X, returnButtonY(centerY), RETURN_W, RETURN_H)) {
            navigateBack();
            return true;
        }
        // 配置面板模式：只处理面板行点击
        if (this.activeConfig != null) {
            handleConfigClick(mouseX, mouseY, centerX, centerY);
            return true;
        }
        // 中心停止按钮
        if (inRect(mouseX, mouseY, centerX + STOP_X, centerY + STOP_Y, STOP_W, STOP_H)) {
            playClickSound();
            // animationKey 依约定传 "empty"，服务端只看 stop 标志
            NetworkHandler.sendToServer(new C2SPlayStatueAnimationPacket(this.corePos, "empty", true));
            this.onClose();
            return true;
        }
        // 页码按钮（多于 1 页才有点击区）
        int pages = RouletteLayout.pageCount(this.nav.currentEntries().size());
        if (pages > 1) {
            if (inRect(mouseX, mouseY, centerX + PREV_X, centerY + PAGE_BTN_Y, PAGE_BTN_SIZE, PAGE_BTN_SIZE)) {
                this.nav.setPage(Math.floorMod(this.nav.currentPage() - 1, pages));
                playClickSound();
                return true;
            }
            if (inRect(mouseX, mouseY, centerX + NEXT_X, centerY + PAGE_BTN_Y, PAGE_BTN_SIZE, PAGE_BTN_SIZE)) {
                this.nav.setPage(Math.floorMod(this.nav.currentPage() + 1, pages));
                playClickSound();
                return true;
            }
        }
        // 扇区选择：几何悬停下标需对应当前页真实条目
        int index = RouletteLayout.hoveredIndex(mouseX - centerX, mouseY - centerY);
        List<YsmModelScanner.AnimEntry> pageEntries = currentPageEntries();
        if (index >= 0 && index < pageEntries.size()) {
            YsmModelScanner.AnimEntry entry = pageEntries.get(index);
            String key = entry.key();
            if ("#return".equals(key)) {
                // 模型作者自加的返回条目：返回上一级（栈底再返回 = 关屏）
                navigateBack();
                return true;
            }
            if (key.startsWith(ModelAnimations.CONFIG_KEY_PREFIX)) {
                // 配置按钮入口（§8.22）：打开配置面板（按钮不存在则忽略）
                YsmBinaryModelWalker.YsmConfigButton config = this.animations.findConfigButton(
                        key.substring(ModelAnimations.CONFIG_KEY_PREFIX.length()));
                if (config != null) {
                    this.activeConfig = config;
                    playClickSound();
                }
                return true;
            }
            if (key.startsWith("#")) {
                // 子菜单入口：id 有效且未超深才压栈进入，否则忽略（不发包不导航不播音）
                if (this.nav.enter(key.substring(1))) {
                    playClickSound();
                }
                return true;
            }
            playClickSound();
            NetworkHandler.sendToServer(new C2SPlayStatueAnimationPacket(this.corePos, key, false));
            this.onClose();
            return true;
        }
        // 点轮盘外空白不关屏（对齐 YSM）
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int pages = RouletteLayout.pageCount(this.nav.currentEntries().size());
        if (pages > 1 && mouseX < centerX() + SCROLL_X_LIMIT) {
            this.nav.setPage(Math.floorMod(this.nav.currentPage() + (delta < 0 ? 1 : -1), pages));
            playClickSound();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 再按一次轮盘键关屏；ESC 走 Screen 默认
        if (StatueAnimationKeys.OPEN_STATUE_ROULETTE.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调用 renderBackground：轮盘浮在游戏画面上
        int centerX = centerX();
        int centerY = centerY();
        if (this.activeConfig != null) {
            // 配置面板模式（§8.22）：不画轮盘，只画面板 + 返回按钮
            renderConfigPanel(graphics, mouseX, mouseY, centerX, centerY);
            renderReturnButton(graphics, mouseX, mouseY, centerX, centerY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        renderRadialBackground(graphics, mouseX, mouseY, centerX, centerY);
        renderRadialButtons(graphics, centerX, centerY);
        renderStopButton(graphics, mouseX, mouseY, centerX, centerY);
        renderReturnButton(graphics, mouseX, mouseY, centerX, centerY);
        renderBreadcrumb(graphics, centerX, centerY);
        int pages = RouletteLayout.pageCount(this.nav.currentEntries().size());
        if (pages > 1) {
            renderPageControls(graphics, mouseX, mouseY, centerX, centerY, pages);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 8 个环形扇段：POSITION_COLOR QUADS，悬停段琥珀色且外半径扩大 */
    private void renderRadialBackground(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int hovered = RouletteLayout.hoveredIndex(mouseX - centerX, mouseY - centerY);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix4f = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < RouletteLayout.ITEMS_PER_PAGE; i++) {
            float startAngle = (float) RouletteLayout.sectorStartAngle(i);
            float endAngle = (float) RouletteLayout.sectorEndAngle(i);
            boolean hover = i == hovered;
            int color = hover ? COLOR_SEGMENT_HOVER : COLOR_SEGMENT;
            float outerR = hover ? OUTER_RADIUS_HOVER : OUTER_RADIUS;
            drawRadialSegment(bufferBuilder, matrix4f, centerX, centerY, startAngle, endAngle, INNER_RADIUS, outerR, color);
        }
        tesselator.end();
        RenderSystem.disableBlend();
    }

    /** 四顶点环段（外/外、内/内、内/内、外/外），角度 0 在 3 点钟方向、随 y 向下顺时针递增 */
    private static void drawRadialSegment(BufferBuilder bufferBuilder, Matrix4f matrix4f, float centerX, float centerY,
                                          float startAngle, float endAngle, float innerR, float outerR, int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int a = color >>> 24;
        bufferBuilder.vertex(matrix4f, centerX + outerR * Mth.cos(startAngle), centerY + outerR * Mth.sin(startAngle), 0).color(r, g, b, a).endVertex();
        bufferBuilder.vertex(matrix4f, centerX + innerR * Mth.cos(startAngle), centerY + innerR * Mth.sin(startAngle), 0).color(r, g, b, a).endVertex();
        bufferBuilder.vertex(matrix4f, centerX + innerR * Mth.cos(endAngle), centerY + innerR * Mth.sin(endAngle), 0).color(r, g, b, a).endVertex();
        bufferBuilder.vertex(matrix4f, centerX + outerR * Mth.cos(endAngle), centerY + outerR * Mth.sin(endAngle), 0).color(r, g, b, a).endVertex();
    }

    /** 扇段标签：圆心半径 65，自动换行，多行向上错开；'#' 前缀的子菜单条目红色渲染（对齐 YSM） */
    private void renderRadialButtons(GuiGraphics graphics, int centerX, int centerY) {
        List<YsmModelScanner.AnimEntry> pageEntries = currentPageEntries();
        for (int i = 0; i < pageEntries.size(); i++) {
            float angle = (float) RouletteLayout.sectorCenterAngle(i);
            int x = centerX + (int) (LABEL_RADIUS * Mth.cos(angle));
            int labelY = centerY + (int) (LABEL_RADIUS * Mth.sin(angle)) - this.font.lineHeight / 2;
            YsmModelScanner.AnimEntry entry = pageEntries.get(i);
            Component label = Component.literal(entry.displayName());
            if (entry.key().startsWith("#")) {
                // 子菜单/配置按钮条目红色渲染（对齐 YSM withStyle(ChatFormatting.RED)；§ 颜色码照常生效）
                label = Component.literal(entry.displayName()).withStyle(ChatFormatting.RED);
            }
            List<FormattedCharSequence> lines = this.font.split(label, LABEL_WRAP_WIDTH);
            if (lines.size() == 1) {
                graphics.drawCenteredString(this.font, lines.get(0), x, labelY, COLOR_LABEL);
            } else {
                int lineY = labelY - lines.size() * this.font.lineHeight + 2;
                for (FormattedCharSequence line : lines) {
                    graphics.drawCenteredString(this.font, line, x, lineY, COLOR_LABEL);
                    lineY += this.font.lineHeight;
                }
            }
        }
    }

    /** 中心停止按钮：YSM FlatColorButton 扁平风，悬停变亮 */
    private void renderStopButton(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        boolean hover = inRect(mouseX, mouseY, centerX + STOP_X, centerY + STOP_Y, STOP_W, STOP_H);
        graphics.fill(centerX + STOP_X, centerY + STOP_Y,
                centerX + STOP_X + STOP_W, centerY + STOP_Y + STOP_H,
                hover ? COLOR_FLAT_BUTTON_HOVER : COLOR_FLAT_BUTTON);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tlm_statue_animation.roulette.stop"),
                centerX, centerY - this.font.lineHeight / 2, COLOR_LABEL);
    }

    /** 返回按钮 y：配置面板打开时挪到面板下沿，避免与配置行重叠 */
    private int returnButtonY(int centerY) {
        if (this.activeConfig != null) {
            return centerY + CONFIG_Y + this.activeConfig.forms().size() * CONFIG_ROW_H + 10;
        }
        return centerY + RETURN_Y;
    }

    /** 右侧返回按钮：145x22 扁平风（与停止/页码按钮同款），栈底点击 = 关屏 */
    private void renderReturnButton(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int returnY = returnButtonY(centerY);
        boolean hover = inRect(mouseX, mouseY, centerX + RETURN_X, returnY, RETURN_W, RETURN_H);
        graphics.fill(centerX + RETURN_X, returnY,
                centerX + RETURN_X + RETURN_W, returnY + RETURN_H,
                hover ? COLOR_FLAT_BUTTON_HOVER : COLOR_FLAT_BUTTON);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tlm_statue_animation.roulette.return"),
                centerX + RETURN_X + RETURN_W / 2, returnY + (RETURN_H - this.font.lineHeight) / 2, COLOR_LABEL);
    }

    /** 子菜单面包屑："id1 > id2"（id 原文，根 → 当前层）；根层（无上级）不画 */
    private void renderBreadcrumb(GuiGraphics graphics, int centerX, int centerY) {
        if (this.nav.isRoot()) {
            return;
        }
        graphics.drawCenteredString(this.font, String.join(" > ", this.nav.breadcrumb()),
                centerX + BREADCRUMB_X, centerY + BREADCRUMB_Y, COLOR_LABEL);
    }

    // ---------- 配置面板（§8.22：YSM 模型配置项，如显示/隐藏法印、九尾切换） ----------

    /** 配置面板行点击：checkbox 翻转 0/1；radio 循环下一个选项；不支持的类型忽略 */
    private void handleConfigClick(double mouseX, double mouseY, int centerX, int centerY) {
        int rowY = centerY + CONFIG_Y;
        for (YsmBinaryModelWalker.ConfigForm form : this.activeConfig.forms()) {
            if (inRect(mouseX, mouseY, centerX + CONFIG_X, rowY, CONFIG_W, CONFIG_ROW_H)) {
                handleFormClick(form);
                return;
            }
            rowY += CONFIG_ROW_H;
        }
    }

    private void handleFormClick(YsmBinaryModelWalker.ConfigForm form) {
        if ("checkbox".equals(form.type())) {
            String varName = RoamingAssignments.checkboxVarName(form.value());
            if (varName == null) {
                return;
            }
            float newValue = currentVar(varName) > 0.0f ? 0.0f : 1.0f;
            this.localVars.put(varName, newValue);
            playClickSound();
            NetworkHandler.sendToServer(new C2SStatueRoamingVarPacket(this.corePos, varName, newValue));
            StatueRoamingDriver.applyFromScreen(this.corePos, varName, newValue); // 即时生效（渲染帧路径负责持久覆盖）
            return;
        }
        if ("radio".equals(form.type()) && !form.labels().isEmpty()) {
            // radio 标签：显示名 → 完整赋值串（"v.roaming.x=2;"）；点击循环到下一项
            List<Map.Entry<String, String>> options = List.copyOf(form.labels().entrySet());
            int current = -1;
            for (int i = 0; i < options.size(); i++) {
                RoamingAssignments.Assignment assignment = RoamingAssignments.parse(options.get(i).getValue());
                if (assignment != null && currentVar(assignment.varName()) == assignment.value()) {
                    current = i;
                    break;
                }
            }
            RoamingAssignments.Assignment next = RoamingAssignments.parse(options.get((current + 1) % options.size()).getValue());
            if (next == null) {
                return;
            }
            this.localVars.put(next.varName(), next.value());
            playClickSound();
            NetworkHandler.sendToServer(new C2SStatueRoamingVarPacket(this.corePos, next.varName(), next.value()));
            StatueRoamingDriver.applyFromScreen(this.corePos, next.varName(), next.value()); // 即时生效
        }
    }

    /** 配置项当前值（本地覆盖优先，初始值来自雕像 NBT 的 YsmRoamingVars） */
    private float currentVar(String varName) {
        return this.localVars.getOrDefault(varName, 0.0f);
    }

    /** 配置面板：扁平深色面板 + 按钮标题 + 表单行（checkbox=[x] 前缀，radio=当前选项，其余灰显） */
    private void renderConfigPanel(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY) {
        int left = centerX + CONFIG_X;
        int top = centerY + CONFIG_Y;
        // 对齐 YSM：配置面板无面板级标题（按钮名无意义，如 "0"），直接渲染各表单项标题
        int rowsHeight = this.activeConfig.forms().size() * CONFIG_ROW_H;
        graphics.fill(left - 4, top - 4, left + CONFIG_W + 4, top + rowsHeight + 4, COLOR_FLAT_BUTTON);
        int rowY = top;
        Component hoveredTooltip = null;
        for (YsmBinaryModelWalker.ConfigForm form : this.activeConfig.forms()) {
            boolean hover = inRect(mouseX, mouseY, left, rowY, CONFIG_W, CONFIG_ROW_H);
            graphics.fill(left, rowY, left + CONFIG_W, rowY + CONFIG_ROW_H,
                    hover ? COLOR_FLAT_BUTTON_HOVER : COLOR_SEGMENT);
            boolean supported = "checkbox".equals(form.type()) || "radio".equals(form.type());
            int textColor = supported ? COLOR_LABEL : 0xFF777777;
            graphics.drawString(this.font, Component.literal(rowLabel(form)), left + 6,
                    rowY + (CONFIG_ROW_H - this.font.lineHeight) / 2, textColor);
            if (hover && !form.description().isEmpty()) {
                hoveredTooltip = Component.literal(form.description());
            }
            rowY += CONFIG_ROW_H;
        }
        if (hoveredTooltip != null) {
            graphics.renderTooltip(this.font, hoveredTooltip, mouseX, mouseY);
        }
    }

    /** 表单行文本：checkbox "[x] 标题"；radio "标题：当前选项"；其余 "标题（暂不支持该类型）" */
    private String rowLabel(YsmBinaryModelWalker.ConfigForm form) {
        if ("checkbox".equals(form.type())) {
            String varName = RoamingAssignments.checkboxVarName(form.value());
            boolean checked = varName != null && currentVar(varName) > 0.0f;
            return (checked ? "[x] " : "[  ] ") + form.title();
        }
        if ("radio".equals(form.type())) {
            String current = null;
            for (Map.Entry<String, String> option : form.labels().entrySet()) {
                RoamingAssignments.Assignment assignment = RoamingAssignments.parse(option.getValue());
                if (assignment != null && currentVar(assignment.varName()) == assignment.value()) {
                    current = option.getKey();
                    break;
                }
            }
            return form.title() + ": " + (current != null ? current : "-");
        }
        return form.title() + " (" + Component.translatable("gui.tlm_statue_animation.roulette.config.unsupported").getString() + ")";
    }

    /** 右侧页码控件：< 按钮、页码信息条（AQUA "x/y"）、> 按钮，与停止按钮同一扁平风 */    private void renderPageControls(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY, int pages) {
        boolean prevHover = inRect(mouseX, mouseY, centerX + PREV_X, centerY + PAGE_BTN_Y, PAGE_BTN_SIZE, PAGE_BTN_SIZE);
        boolean nextHover = inRect(mouseX, mouseY, centerX + NEXT_X, centerY + PAGE_BTN_Y, PAGE_BTN_SIZE, PAGE_BTN_SIZE);
        graphics.fill(centerX + PREV_X, centerY + PAGE_BTN_Y,
                centerX + PREV_X + PAGE_BTN_SIZE, centerY + PAGE_BTN_Y + PAGE_BTN_SIZE,
                prevHover ? COLOR_FLAT_BUTTON_HOVER : COLOR_FLAT_BUTTON);
        graphics.fill(centerX + NEXT_X, centerY + PAGE_BTN_Y,
                centerX + NEXT_X + PAGE_BTN_SIZE, centerY + PAGE_BTN_Y + PAGE_BTN_SIZE,
                nextHover ? COLOR_FLAT_BUTTON_HOVER : COLOR_FLAT_BUTTON);
        graphics.drawCenteredString(this.font, "<", centerX + PREV_X + PAGE_BTN_SIZE / 2,
                centerY + PAGE_BTN_Y + PAGE_BTN_SIZE / 2 - this.font.lineHeight / 2, COLOR_LABEL);
        graphics.drawCenteredString(this.font, ">", centerX + NEXT_X + PAGE_BTN_SIZE / 2,
                centerY + PAGE_BTN_Y + PAGE_BTN_SIZE / 2 - this.font.lineHeight / 2, COLOR_LABEL);
        graphics.fill(centerX + PAGE_BAR_LEFT, centerY + PAGE_BAR_TOP,
                centerX + PAGE_BAR_RIGHT, centerY + PAGE_BAR_BOTTOM, COLOR_PAGE_BAR);
        graphics.drawCenteredString(this.font, (this.nav.currentPage() + 1) + "/" + pages,
                centerX + (PAGE_BAR_LEFT + PAGE_BAR_RIGHT) / 2,
                centerY + (PAGE_BAR_TOP + PAGE_BAR_BOTTOM) / 2 - this.font.lineHeight / 2, COLOR_PAGE_TEXT);
    }
}
