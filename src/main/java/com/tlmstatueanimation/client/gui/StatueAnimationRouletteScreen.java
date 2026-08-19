package com.tlmstatueanimation.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tlmstatueanimation.client.StatueAnimationKeys;
import com.tlmstatueanimation.network.NetworkHandler;
import com.tlmstatueanimation.network.message.C2SPlayStatueAnimationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 雕像动作轮盘屏（复刻 YSM 2.x 轮盘视觉风格：几何、配色、交互对齐）。
 * 轮盘中心偏左（右侧放页码控件区），不暗化背景，轮盘浮在游戏画面上；
 * 左键点扇区=播放，点中心停止钮=停止，滚轮/页码按钮翻页，ESC/轮盘键关屏；
 * 点轮盘外空白与右键均不关屏。打开时游戏不暂停（isPauseScreen=false）。
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

    private static final int COLOR_SEGMENT = 0x90000000;
    private static final int COLOR_SEGMENT_HOVER = 0xF0FFB100;
    private static final int COLOR_LABEL = 0xF3EFE0;
    private static final int COLOR_FLAT_BUTTON = 0x90000000;
    private static final int COLOR_FLAT_BUTTON_HOVER = 0xC0404040;
    private static final int COLOR_PAGE_BAR = 0xCF000000;
    private static final int COLOR_PAGE_TEXT = 0x55FFFF;

    private final BlockPos corePos;
    private final String modelId;
    private final List<RouletteEntry> entries;
    private int page;

    public StatueAnimationRouletteScreen(BlockPos corePos, String modelId, List<RouletteEntry> entries) {
        super(Component.empty());
        this.corePos = corePos;
        this.modelId = modelId;
        this.entries = entries;
        this.page = 0;
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

    private List<RouletteEntry> currentPageEntries() {
        return RouletteLayout.pageEntries(this.entries, this.page);
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void playClickSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            // 右键不关屏（对齐 YSM）
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int centerX = centerX();
        int centerY = centerY();
        // 中心停止按钮
        if (inRect(mouseX, mouseY, centerX + STOP_X, centerY + STOP_Y, STOP_W, STOP_H)) {
            playClickSound();
            // animationKey 依约定传 "empty"，服务端只看 stop 标志
            NetworkHandler.sendToServer(new C2SPlayStatueAnimationPacket(this.corePos, "empty", true));
            this.onClose();
            return true;
        }
        // 页码按钮（多于 1 页才有点击区）
        int pages = RouletteLayout.pageCount(this.entries.size());
        if (pages > 1) {
            if (inRect(mouseX, mouseY, centerX + PREV_X, centerY + PAGE_BTN_Y, PAGE_BTN_SIZE, PAGE_BTN_SIZE)) {
                this.page = Math.floorMod(this.page - 1, pages);
                playClickSound();
                return true;
            }
            if (inRect(mouseX, mouseY, centerX + NEXT_X, centerY + PAGE_BTN_Y, PAGE_BTN_SIZE, PAGE_BTN_SIZE)) {
                this.page = Math.floorMod(this.page + 1, pages);
                playClickSound();
                return true;
            }
        }
        // 扇区选择：几何悬停下标需对应当前页真实条目
        int index = RouletteLayout.hoveredIndex(mouseX - centerX, mouseY - centerY);
        List<RouletteEntry> pageEntries = currentPageEntries();
        if (index >= 0 && index < pageEntries.size()) {
            RouletteEntry entry = pageEntries.get(index);
            playClickSound();
            NetworkHandler.sendToServer(new C2SPlayStatueAnimationPacket(this.corePos, entry.key(), false));
            this.onClose();
            return true;
        }
        // 点轮盘外空白不关屏（对齐 YSM）
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int pages = RouletteLayout.pageCount(this.entries.size());
        if (pages > 1 && mouseX < centerX() + SCROLL_X_LIMIT) {
            this.page = Math.floorMod(this.page + (delta < 0 ? 1 : -1), pages);
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
        renderRadialBackground(graphics, mouseX, mouseY, centerX, centerY);
        renderRadialButtons(graphics, centerX, centerY);
        renderStopButton(graphics, mouseX, mouseY, centerX, centerY);
        int pages = RouletteLayout.pageCount(this.entries.size());
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

    /** 扇段标签：圆心半径 65，自动换行，多行向上错开 */
    private void renderRadialButtons(GuiGraphics graphics, int centerX, int centerY) {
        List<RouletteEntry> pageEntries = currentPageEntries();
        for (int i = 0; i < pageEntries.size(); i++) {
            float angle = (float) RouletteLayout.sectorCenterAngle(i);
            int x = centerX + (int) (LABEL_RADIUS * Mth.cos(angle));
            int labelY = centerY + (int) (LABEL_RADIUS * Mth.sin(angle)) - this.font.lineHeight / 2;
            List<FormattedCharSequence> lines = this.font.split(Component.literal(pageEntries.get(i).displayName()), LABEL_WRAP_WIDTH);
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

    /** 右侧页码控件：< 按钮、页码信息条（AQUA "x/y"）、> 按钮，与停止按钮同一扁平风 */
    private void renderPageControls(GuiGraphics graphics, int mouseX, int mouseY, int centerX, int centerY, int pages) {
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
        graphics.drawCenteredString(this.font, (this.page + 1) + "/" + pages,
                centerX + (PAGE_BAR_LEFT + PAGE_BAR_RIGHT) / 2,
                centerY + (PAGE_BAR_TOP + PAGE_BAR_BOTTOM) / 2 - this.font.lineHeight / 2, COLOR_PAGE_TEXT);
    }
}
