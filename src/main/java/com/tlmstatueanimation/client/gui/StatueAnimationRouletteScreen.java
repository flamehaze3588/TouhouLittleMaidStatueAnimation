package com.tlmstatueanimation.client.gui;

import com.tlmstatueanimation.network.NetworkHandler;
import com.tlmstatueanimation.network.message.C2SPlayStatueAnimationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 雕像动作轮盘屏（D4：自建径向菜单，不复用 YSM 轮盘）。
 * 条目沿圆周排布，鼠标悬停高亮；左键点扇区=播放，点中心=停止，右键/ESC/点外圈=取消。
 * 打开时游戏不暂停（isPauseScreen=false），雕像在轮盘后继续动画。
 */
public class StatueAnimationRouletteScreen extends Screen {
    /** 中心停止区半径 */
    private static final int DEAD_ZONE_RADIUS = 26;
    /** 条目盒中心所在圆周半径的下限/上限 */
    private static final int MIN_RING_RADIUS = 64;
    private static final int MAX_RING_RADIUS = 120;
    /** 悬停识别的外圈上限（相对环半径的倍数），超出视为点外圈取消 */
    private static final double OUTER_LIMIT_FACTOR = 1.7;
    private static final int BOX_WIDTH = 88;
    private static final int BOX_HEIGHT = 20;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TITLE = 0xA0E8FF;
    private static final int COLOR_BOX = 0xA0202020;
    private static final int COLOR_BOX_HOVER = 0xE04070A0;
    private static final int COLOR_STOP = 0xA0602020;
    private static final int COLOR_STOP_HOVER = 0xE0A03030;

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
        return this.width / 2;
    }

    private int centerY() {
        return this.height / 2;
    }

    private int ringRadius() {
        return Mth.clamp(Math.min(this.width, this.height) / 4, MIN_RING_RADIUS, MAX_RING_RADIUS);
    }

    private List<RouletteEntry> currentPageEntries() {
        return RouletteLayout.pageEntries(this.entries, this.page);
    }

    /**
     * 当前悬停扇区下标（当前页条目列表内的下标）；死区/外圈外返回 -1。
     */
    private int hoveredIndex(double mouseX, double mouseY) {
        double dx = mouseX - centerX();
        double dy = mouseY - centerY();
        int index = RouletteLayout.angleToIndex(currentPageEntries().size(), dx, dy, DEAD_ZONE_RADIUS);
        if (index < 0) {
            return -1;
        }
        double distSq = dx * dx + dy * dy;
        double outer = ringRadius() * OUTER_LIMIT_FACTOR;
        if (distSq > outer * outer) {
            return -1;
        }
        return index;
    }

    private boolean inStopZone(double mouseX, double mouseY) {
        double dx = mouseX - centerX();
        double dy = mouseY - centerY();
        return dx * dx + dy * dy < DEAD_ZONE_RADIUS * DEAD_ZONE_RADIUS;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            // 右键：取消
            this.onClose();
            return true;
        }
        if (button == 0) {
            if (inStopZone(mouseX, mouseY)) {
                // 中心：停止动作（animationKey 依 D5 约定传 "empty"，服务端只看 stop 标志）
                NetworkHandler.sendToServer(new C2SPlayStatueAnimationPacket(this.corePos, "empty", true));
                this.onClose();
                return true;
            }
            int index = hoveredIndex(mouseX, mouseY);
            if (index >= 0) {
                RouletteEntry entry = currentPageEntries().get(index);
                NetworkHandler.sendToServer(new C2SPlayStatueAnimationPacket(this.corePos, entry.key(), false));
                this.onClose();
                return true;
            }
            // 左键点轮盘外空白：取消
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int pages = RouletteLayout.pageCount(this.entries.size());
        if (pages > 1) {
            this.page = Math.floorMod(this.page + (delta < 0 ? 1 : -1), pages);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 标题：屏名 + 模型 id
        Component title = Component.translatable("gui.tlm_statue_animation.roulette.title");
        graphics.drawCenteredString(this.font, title, centerX(), centerY() - ringRadius() - BOX_HEIGHT - 18, COLOR_TITLE);
        graphics.drawCenteredString(this.font, this.modelId, centerX(), centerY() - ringRadius() - BOX_HEIGHT - 8, COLOR_TEXT);

        List<RouletteEntry> pageEntries = currentPageEntries();
        int hovered = hoveredIndex(mouseX, mouseY);

        // 扇区条目盒：沿圆周均布
        int ring = ringRadius();
        for (int i = 0; i < pageEntries.size(); i++) {
            double angle = Math.PI * 2 * i / pageEntries.size();
            // 与 angleToIndex 同一约定：0 在 12 点钟，顺时针
            int boxCenterX = centerX() + (int) Math.round(ring * Math.sin(angle));
            int boxCenterY = centerY() - (int) Math.round(ring * Math.cos(angle));
            int x0 = boxCenterX - BOX_WIDTH / 2;
            int y0 = boxCenterY - BOX_HEIGHT / 2;
            graphics.fill(x0, y0, x0 + BOX_WIDTH, y0 + BOX_HEIGHT, i == hovered ? COLOR_BOX_HOVER : COLOR_BOX);
            graphics.drawCenteredString(this.font, fit(pageEntries.get(i).displayName()), boxCenterX, boxCenterY - 4, COLOR_TEXT);
        }

        // 中心停止区
        int stopColor = inStopZone(mouseX, mouseY) ? COLOR_STOP_HOVER : COLOR_STOP;
        graphics.fill(centerX() - DEAD_ZONE_RADIUS, centerY() - DEAD_ZONE_RADIUS / 2,
                centerX() + DEAD_ZONE_RADIUS, centerY() + DEAD_ZONE_RADIUS / 2, stopColor);
        graphics.drawCenteredString(this.font, Component.translatable("gui.tlm_statue_animation.roulette.stop"),
                centerX(), centerY() - 4, COLOR_TEXT);

        // 页码指示（多于一页时）
        int pages = RouletteLayout.pageCount(this.entries.size());
        if (pages > 1) {
            Component pageText = Component.translatable("gui.tlm_statue_animation.roulette.page", this.page + 1, pages);
            graphics.drawCenteredString(this.font, pageText, centerX(), centerY() + ringRadius() + BOX_HEIGHT, COLOR_TEXT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 显示名过长时截断，保证不出条目盒 */
    private String fit(String displayName) {
        int maxWidth = BOX_WIDTH - 8;
        if (this.font.width(displayName) <= maxWidth) {
            return displayName;
        }
        return this.font.plainSubstrByWidth(displayName, maxWidth - this.font.width("…")) + "…";
    }
}
