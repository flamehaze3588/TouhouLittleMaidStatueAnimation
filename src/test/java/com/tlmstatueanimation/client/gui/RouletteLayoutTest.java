package com.tlmstatueanimation.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RouletteLayout 几何测试（YSM 2.x 约定：角度 0 在 3 点钟方向、随屏幕 y 向下顺时针递增；
 * 8 个固定扇段带 2° 间隙；悬停半径带 50~100；分页切片语义不变）。
 */
class RouletteLayoutTest {
    /** 测试用半径：落在悬停带 (50, 100) 内 */
    private static final double R = 60;
    /** 越过间隙带所需的最小角偏移余量 */
    private static final double EPS = 1e-3;

    /** 以极坐标（相对轮盘中心）求悬停扇段下标 */
    private static int indexAt(double angle, double radius) {
        return RouletteLayout.hoveredIndex(radius * Math.cos(angle), radius * Math.sin(angle));
    }

    @Test
    void sectorZeroStartsAtThreeOClock() {
        // 3 点钟方向（theta=0）越过间隙略偏顺时针（屏幕坐标即偏下）→ 扇区 0
        assertEquals(0, indexAt(RouletteLayout.SECTOR_GAP + EPS, R));
        // 扇区 0 中心角 = π/8（22.5°，3 点钟与 4:30 方向之间）
        assertEquals(Math.PI / 8, RouletteLayout.sectorCenterAngle(0), 1e-12);
        assertEquals(0, indexAt(Math.PI / 8, R));
    }

    @Test
    void eightSectorsCardinalDirections() {
        // 各正方向恰好落在间隙中线上；越过间隙略偏顺时针 → 扇区 0/2/4/6
        double past = RouletteLayout.SECTOR_GAP + EPS;
        assertEquals(0, indexAt(past, R));
        assertEquals(2, indexAt(Math.PI / 2 + past, R));
        assertEquals(4, indexAt(Math.PI + past, R));
        assertEquals(6, indexAt(Math.PI * 3 / 2 + past, R));
        // 各扇段中心角 → 各自下标
        for (int i = 0; i < 8; i++) {
            assertEquals(i, indexAt(RouletteLayout.sectorCenterAngle(i), R));
        }
    }

    @Test
    void gapBandDoesNotHover() {
        // 正方向/对角线方向恰在间隙中线 → 不悬停
        assertEquals(-1, indexAt(0, R));
        assertEquals(-1, indexAt(Math.PI / 4, R));
        assertEquals(-1, indexAt(Math.PI / 2, R));
        assertEquals(-1, indexAt(Math.PI, R));
        assertEquals(-1, indexAt(Math.PI * 3 / 2, R));
        // 边界线两侧 2° 以内（间隙带内）→ 不悬停
        assertEquals(-1, indexAt(Math.PI / 4 - RouletteLayout.SECTOR_GAP / 2, R));
        assertEquals(-1, indexAt(Math.PI / 4 + RouletteLayout.SECTOR_GAP / 2, R));
    }

    @Test
    void angleWrapAroundStaysInSectorSeven() {
        // 3 点钟方向越过间隙略偏逆时针（theta 为负）→ 最后一个扇区 7
        assertEquals(7, indexAt(-(RouletteLayout.SECTOR_GAP + EPS), R));
        // 3 点钟方向越过间隙略偏顺时针 → 扇区 0
        assertEquals(0, indexAt(RouletteLayout.SECTOR_GAP + EPS, R));
    }

    @Test
    void hoverRadiusBandLimits() {
        // dist = 40：内圈，不悬停
        assertEquals(-1, indexAt(Math.PI / 8, 40));
        assertEquals(-1, RouletteLayout.hoveredIndex(0, 0));
        assertEquals(-1, RouletteLayout.hoveredIndex(20, -20));
        // dist = 60：悬停带内 → 扇区 0
        assertEquals(0, indexAt(Math.PI / 8, 60));
        // dist = 110：外圈，不悬停
        assertEquals(-1, indexAt(Math.PI / 8, 110));
    }

    @Test
    void exactlyOnHoverBandBoundaryIsOutside() {
        // dist 恰等于下/上限 → 不悬停（坐标轴方向亦在角度间隙，双重拒绝）
        assertEquals(-1, RouletteLayout.hoveredIndex(50, 0));
        assertEquals(-1, RouletteLayout.hoveredIndex(100, 0));
        assertEquals(-1, RouletteLayout.hoveredIndex(-50, 0));
        assertEquals(-1, RouletteLayout.hoveredIndex(-100, 0));
    }

    @Test
    void sectorBoundaryNeighborhood() {
        // 扇区 0/1 边界在 π/4：越过间隙偏逆时针 → 扇区 0，偏顺时针 → 扇区 1
        double past = RouletteLayout.SECTOR_GAP + EPS;
        assertEquals(0, indexAt(Math.PI / 4 - past, R));
        assertEquals(1, indexAt(Math.PI / 4 + past, R));
    }

    @Test
    void sectorAngleRangesHaveTwoDegreeGaps() {
        // 扇段 0 自间隙起、扇段 7 至 2π-间隙止；相邻扇段间留 2 倍间隙（各内缩 2°）
        assertEquals(RouletteLayout.SECTOR_GAP, RouletteLayout.sectorStartAngle(0), 1e-12);
        assertEquals(Math.PI * 2 - RouletteLayout.SECTOR_GAP, RouletteLayout.sectorEndAngle(7), 1e-9);
        for (int i = 0; i < 8; i++) {
            assertEquals(Math.PI / 4 - 2 * RouletteLayout.SECTOR_GAP,
                    RouletteLayout.sectorEndAngle(i) - RouletteLayout.sectorStartAngle(i), 1e-12);
            double nextStart = i == 7
                    ? RouletteLayout.sectorStartAngle(0) + Math.PI * 2
                    : RouletteLayout.sectorStartAngle(i + 1);
            assertEquals(2 * RouletteLayout.SECTOR_GAP, nextStart - RouletteLayout.sectorEndAngle(i), 1e-9);
        }
    }

    @Test
    void pageCountCalculation() {
        assertEquals(1, RouletteLayout.pageCount(0));
        assertEquals(1, RouletteLayout.pageCount(1));
        assertEquals(1, RouletteLayout.pageCount(8));
        assertEquals(2, RouletteLayout.pageCount(9));
        assertEquals(3, RouletteLayout.pageCount(17));
    }

    @Test
    void pageEntriesSlicingAndClamping() {
        List<Integer> entries = IntStream.range(0, 17).boxed().toList();
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), RouletteLayout.pageEntries(entries, 0));
        assertEquals(List.of(8, 9, 10, 11, 12, 13, 14, 15), RouletteLayout.pageEntries(entries, 1));
        assertEquals(List.of(16), RouletteLayout.pageEntries(entries, 2));
        // 越界页钳制到最后一页
        assertEquals(List.of(16), RouletteLayout.pageEntries(entries, 99));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), RouletteLayout.pageEntries(entries, -1));
    }
}
