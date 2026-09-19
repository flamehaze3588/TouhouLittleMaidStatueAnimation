package com.tlmstatueanimation.client.gui;

import com.tlmstatueanimation.client.model.ModelAnimations;
import com.tlmstatueanimation.client.model.YsmModelScanner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouletteNavigation 导航状态机测试（对齐 YSM 2.6.5 navigationStack 语义）：
 * 压栈进入（页码归 0）/ 弹栈返回（恢复上层页码）/ 每层页码独立记忆 /
 * 最深 5 级拒绝 / '#id' 指向不存在子菜单拒绝 / 面包屑顺序。
 */
class RouletteNavigationTest {

    /** 两级样例：根表含 '#一级' 入口；一级表含 '#二级' 嵌套入口与 '#return' 条目 */
    private static ModelAnimations twoLevelAnimations() {
        List<YsmModelScanner.AnimEntry> root = List.of(
                new YsmModelScanner.AnimEntry("extra0", "变身"),
                new YsmModelScanner.AnimEntry("#一级", "一级菜单"));
        Map<String, List<YsmModelScanner.AnimEntry>> submenus = new LinkedHashMap<>();
        submenus.put("一级", List.of(
                new YsmModelScanner.AnimEntry("dance0", "舞蹈"),
                new YsmModelScanner.AnimEntry("#二级", "二级菜单"),
                new YsmModelScanner.AnimEntry("#return", "返回")));
        submenus.put("二级", List.of(new YsmModelScanner.AnimEntry("dance1", "深层舞蹈")));
        return new ModelAnimations(root, submenus, java.util.List.of());
    }

    /** depth 层链式样例：根 → "#L1" → "#L2" → …（每层子表含下一层入口，最后一层只有动作） */
    private static ModelAnimations chainedAnimations(int depth) {
        List<YsmModelScanner.AnimEntry> root = new ArrayList<>();
        root.add(new YsmModelScanner.AnimEntry("#L1", "L1 菜单"));
        Map<String, List<YsmModelScanner.AnimEntry>> submenus = new LinkedHashMap<>();
        for (int i = 1; i <= depth; i++) {
            List<YsmModelScanner.AnimEntry> table = new ArrayList<>();
            table.add(new YsmModelScanner.AnimEntry("act" + i, "动作" + i));
            if (i < depth) {
                table.add(new YsmModelScanner.AnimEntry("#L" + (i + 1), "L" + (i + 1) + " 菜单"));
            }
            submenus.put("L" + i, table);
        }
        return new ModelAnimations(root, submenus, java.util.List.of());
    }

    @Test
    void startsAtRoot() {
        RouletteNavigation nav = new RouletteNavigation(twoLevelAnimations());

        assertTrue(nav.isRoot());
        assertEquals(0, nav.depth());
        assertEquals(0, nav.currentPage());
        assertTrue(nav.breadcrumb().isEmpty());
        assertEquals(List.of("extra0", "#一级"),
                nav.currentEntries().stream().map(YsmModelScanner.AnimEntry::key).toList());
    }

    @Test
    void enterPushesLevelAndResetsPage() {
        RouletteNavigation nav = new RouletteNavigation(twoLevelAnimations());
        nav.setPage(1);

        assertTrue(nav.enter("一级"));

        assertFalse(nav.isRoot());
        assertEquals(1, nav.depth());
        // 进入新层页码归 0，当前层切到子菜单表
        assertEquals(0, nav.currentPage());
        assertEquals(List.of("dance0", "#二级", "#return"),
                nav.currentEntries().stream().map(YsmModelScanner.AnimEntry::key).toList());
    }

    @Test
    void backPopsAndRestoresParentPage() {
        RouletteNavigation nav = new RouletteNavigation(twoLevelAnimations());
        nav.setPage(1); // 根层翻到第 1 页
        nav.enter("一级");
        nav.setPage(2); // 一级层翻到第 2 页
        nav.enter("二级");

        // 弹栈回一级：恢复一级记忆的页码 2
        assertTrue(nav.back());
        assertEquals(1, nav.depth());
        assertEquals(2, nav.currentPage());
        assertEquals(List.of("dance0", "#二级", "#return"),
                nav.currentEntries().stream().map(YsmModelScanner.AnimEntry::key).toList());

        // 再弹回根层：恢复根层记忆的页码 1
        assertTrue(nav.back());
        assertTrue(nav.isRoot());
        assertEquals(1, nav.currentPage());
        assertEquals(List.of("extra0", "#一级"),
                nav.currentEntries().stream().map(YsmModelScanner.AnimEntry::key).toList());
    }

    @Test
    void backAtRootReturnsFalseSoCallerClosesScreen() {
        RouletteNavigation nav = new RouletteNavigation(twoLevelAnimations());
        assertFalse(nav.back());
        // 空栈反复弹栈保持根层状态，不炸
        assertFalse(nav.back());
        assertTrue(nav.isRoot());
    }

    @Test
    void enterUnknownSubmenuIdIsRejected() {
        RouletteNavigation nav = new RouletteNavigation(twoLevelAnimations());
        nav.setPage(1);

        assertFalse(nav.enter("不存在"));

        // 状态不变：不导航
        assertTrue(nav.isRoot());
        assertEquals(1, nav.currentPage());
    }

    @Test
    void enterBeyondMaxDepthIsRejected() {
        RouletteNavigation nav = new RouletteNavigation(chainedAnimations(RouletteNavigation.MAX_DEPTH + 1));
        // 逐层进入到最深：栈满 5 层
        for (int i = 1; i <= RouletteNavigation.MAX_DEPTH; i++) {
            assertTrue(nav.enter("L" + i), "enter L" + i + " should succeed");
        }
        assertEquals(RouletteNavigation.MAX_DEPTH, nav.depth());

        // 第 6 层存在但拒绝进入（超深）
        assertFalse(nav.enter("L" + (RouletteNavigation.MAX_DEPTH + 1)));
        assertEquals(RouletteNavigation.MAX_DEPTH, nav.depth());
        assertEquals(List.of("L1", "L2", "L3", "L4", "L5"), nav.breadcrumb());
    }

    @Test
    void pageMemoryIsIndependentPerLevel() {
        RouletteNavigation nav = new RouletteNavigation(chainedAnimations(3));
        nav.setPage(3); // 根层页码
        nav.enter("L1");
        nav.setPage(1); // L1 层页码
        nav.enter("L2");
        nav.setPage(2); // L2 层页码
        nav.enter("L3");
        // L3 页码保持 0（未翻过页）
        assertEquals(0, nav.currentPage());

        nav.back();
        assertEquals(2, nav.currentPage());
        nav.back();
        assertEquals(1, nav.currentPage());
        nav.back();
        assertEquals(3, nav.currentPage());
    }

    @Test
    void breadcrumbIsRootToCurrentOrder() {
        RouletteNavigation nav = new RouletteNavigation(chainedAnimations(3));
        nav.enter("L1");
        nav.enter("L2");
        nav.enter("L3");

        // 根 → 当前层（栈底到栈顶）
        assertEquals(List.of("L1", "L2", "L3"), nav.breadcrumb());

        nav.back();
        assertEquals(List.of("L1", "L2"), nav.breadcrumb());
    }
}
