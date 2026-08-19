package com.tlmstatueanimation.client.gui;

import java.util.List;

/**
 * 轮盘径向布局几何核心（对齐 YSM 2.x 轮盘约定，纯逻辑，无 MC 依赖，可单测）。
 * 坐标约定：屏幕坐标（x 向右、y 向下）；角度 0 在 3 点钟方向，随 y 向下递增（即顺时针）。
 * 每页固定 8 个环形扇段，扇段 i 的角度区间为
 * [(2π/8)*i + GAP, (2π/8)*(i+1) - GAP]（相邻扇段间留 2° 间隙带，间隙带内不悬停）。
 * 悬停半径带为 50 &lt; dist &lt; 100（窄于视觉环：内半径 25、外半径 105）。
 */
public final class RouletteLayout {
    /** 每页扇区数（与 YSM 轮盘一致） */
    public static final int ITEMS_PER_PAGE = 8;
    /** 扇段间隙半宽（弧度），0.034906585f ≈ 2° */
    public static final double SECTOR_GAP = 0.034906585;
    /** 悬停半径带下限（含边界拒绝：dist &lt;= 50 不悬停） */
    public static final double HOVER_MIN_RADIUS = 50;
    /** 悬停半径带上限（含边界拒绝：dist &gt;= 100 不悬停） */
    public static final double HOVER_MAX_RADIUS = 100;

    private RouletteLayout() {
    }

    /** 扇段 i 的起始角（含间隙内缩），弧度 */
    public static double sectorStartAngle(int index) {
        return Math.PI * 2 / ITEMS_PER_PAGE * index + SECTOR_GAP;
    }

    /** 扇段 i 的结束角（含间隙内缩），弧度 */
    public static double sectorEndAngle(int index) {
        return Math.PI * 2 / ITEMS_PER_PAGE * (index + 1) - SECTOR_GAP;
    }

    /** 扇段 i 的中心角（弧度）：0.3926991 + i * 0.7853982，即 π/8 起每 π/4 */
    public static double sectorCenterAngle(int index) {
        return Math.PI / ITEMS_PER_PAGE + index * (Math.PI * 2 / ITEMS_PER_PAGE);
    }

    /**
     * 把鼠标相对轮盘中心的偏移映射到扇段下标。
     *
     * @param mouseDX 鼠标 x - 中心 x
     * @param mouseDY 鼠标 y - 中心 y
     * @return 扇段下标 [0, 8)；间隙带内或半径带（50, 100）外返回 -1
     */
    public static int hoveredIndex(double mouseDX, double mouseDY) {
        double dist = Math.sqrt(mouseDX * mouseDX + mouseDY * mouseDY);
        if (dist <= HOVER_MIN_RADIUS || dist >= HOVER_MAX_RADIUS) {
            return -1;
        }
        double theta = Math.atan2(mouseDY, mouseDX);
        if (theta < 0) {
            theta += Math.PI * 2;
        }
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (theta > sectorStartAngle(i) && theta < sectorEndAngle(i)) {
                return i;
            }
        }
        return -1;
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
