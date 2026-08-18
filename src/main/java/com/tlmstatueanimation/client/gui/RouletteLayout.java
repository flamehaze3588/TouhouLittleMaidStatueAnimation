package com.tlmstatueanimation.client.gui;

import java.util.List;

/**
 * 轮盘径向布局几何核心（D4，纯逻辑，无 MC 依赖，可单测）。
 * 坐标约定：屏幕坐标（x 向右、y 向下），扇区从 12 点钟方向起顺时针均分。
 */
public final class RouletteLayout {
    /** 每页扇区数（与 YSM 轮盘一致） */
    public static final int ITEMS_PER_PAGE = 8;

    private RouletteLayout() {
    }

    /**
     * 把鼠标相对轮盘中心的偏移映射到扇区下标。
     *
     * @param itemCount      当前页条目数（扇区数）
     * @param mouseDX        鼠标 x - 中心 x
     * @param mouseDY        鼠标 y - 中心 y
     * @param deadZoneRadius 中心死区半径（死区内返回 -1）
     * @return 扇区下标 [0, itemCount)；死区内或 itemCount&lt;=0 返回 -1
     */
    public static int angleToIndex(int itemCount, double mouseDX, double mouseDY, double deadZoneRadius) {
        if (itemCount <= 0) {
            return -1;
        }
        if (mouseDX * mouseDX + mouseDY * mouseDY < deadZoneRadius * deadZoneRadius) {
            return -1;
        }
        // 屏幕坐标系：正上方为 0，顺时针增长（atan2(dx, -dy)：上=0，右=π/2，下=π，左=3π/2）
        double angle = Math.atan2(mouseDX, -mouseDY);
        if (angle < 0) {
            angle += Math.PI * 2;
        }
        double sectorSize = Math.PI * 2 / itemCount;
        return (int) (angle / sectorSize) % itemCount;
    }

    /** 页数（0 个条目时按 1 页计，防御） */
    public static int pageCount(int itemCount) {
        if (itemCount <= 0) {
            return 1;
        }
        return (itemCount + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
    }

    /** 当前页条目子集；page 会被钳制到合法范围 */
    public static <T> List<T> pageEntries(List<T> entries, int page) {
        int pages = pageCount(entries.size());
        int clampedPage = Math.max(0, Math.min(page, pages - 1));
        int from = clampedPage * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, entries.size());
        return entries.subList(from, to);
    }
}
