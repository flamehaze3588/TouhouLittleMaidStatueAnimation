package com.tlmstatueanimation.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RouletteLayout 几何测试（D4 扇区映射与分页）。
 */
class RouletteLayoutTest {

    @Test
    void singleItemAlwaysIndexZeroOutsideDeadZone() {
        assertEquals(0, RouletteLayout.angleToIndex(1, 0, -50, 10));
        assertEquals(0, RouletteLayout.angleToIndex(1, 50, 50, 10));
    }

    @Test
    void eightSectorsCardinalDirections() {
        // n=8：上=0、右上=1、右=2、右下=3、下=4、左下=5、左=6、左上=7
        assertEquals(0, RouletteLayout.angleToIndex(8, 0, -50, 10));
        assertEquals(2, RouletteLayout.angleToIndex(8, 50, 0, 10));
        assertEquals(4, RouletteLayout.angleToIndex(8, 0, 50, 10));
        assertEquals(6, RouletteLayout.angleToIndex(8, -50, 0, 10));
        assertEquals(1, RouletteLayout.angleToIndex(8, 35, -35, 10));
        assertEquals(7, RouletteLayout.angleToIndex(8, -35, -35, 10));
    }

    @Test
    void angleWrapAroundStaysInSectorSeven() {
        // 略小于 2π（12 点钟方向偏左一点）→ 最后一个扇区
        assertEquals(7, RouletteLayout.angleToIndex(8, -1, -50, 10));
        // 12 点钟方向偏右一点 → 扇区 0
        assertEquals(0, RouletteLayout.angleToIndex(8, 1, -50, 10));
    }

    @Test
    void deadZoneReturnsMinusOne() {
        assertEquals(-1, RouletteLayout.angleToIndex(8, 0, 0, 10));
        assertEquals(-1, RouletteLayout.angleToIndex(8, 5, 5, 10));
        assertEquals(-1, RouletteLayout.angleToIndex(8, 9, 0, 10));
    }

    @Test
    void exactlyOnDeadZoneBoundaryIsOutside() {
        // 距离恰等于死区半径 → 不在死区内（边界归属扇区）
        assertEquals(0, RouletteLayout.angleToIndex(8, 0, -10, 10));
    }

    @Test
    void sectorBoundaryNeighborhood() {
        // 扇区边界（12 点钟偏右 22.5°=扇区0/1 边界）的浮点归属不做强约定，
        // 但边界两侧微小偏移必须单调：边界略偏上（逆时针）→ 扇区 0，略偏下（顺时针）→ 扇区 1
        double sector = Math.PI * 2 / 8;
        double r = 50;
        double epsilon = 1e-3;
        assertEquals(0, RouletteLayout.angleToIndex(8, r * Math.sin(sector - epsilon), -r * Math.cos(sector - epsilon), 10));
        assertEquals(1, RouletteLayout.angleToIndex(8, r * Math.sin(sector + epsilon), -r * Math.cos(sector + epsilon), 10));
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
