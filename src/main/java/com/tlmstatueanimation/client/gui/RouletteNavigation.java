package com.tlmstatueanimation.client.gui;

import com.tlmstatueanimation.client.model.ModelAnimations;
import com.tlmstatueanimation.client.model.YsmModelScanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 轮盘子菜单导航状态机（对齐 YSM 2.6.5 navigationStack，纯逻辑，无 MC 依赖，可单测）：
 * 栈条目 = 子菜单 id + 该层记忆的页码；栈空 = 根层（页码单独记忆）。
 * 进入子菜单页码归 0；返回上一级恢复该层页码；最深 5 级（栈满拒绝进入）；
 * '#id' 指向不存在的子菜单时拒绝进入（调用方据此忽略点击，不发包不导航）。
 */
public final class RouletteNavigation {
    /** 最大嵌套深度（对齐 YSM navigationStack 上限） */
    public static final int MAX_DEPTH = 5;

    /** 导航栈一层：子菜单 id + 该层记忆的页码 */
    public record Level(String submenuId, int page) {
    }

    private final ModelAnimations animations;
    private final Deque<Level> stack = new ArrayDeque<>();
    private int rootPage;

    public RouletteNavigation(ModelAnimations animations) {
        this.animations = animations;
    }

    /** 当前是否根层 */
    public boolean isRoot() {
        return stack.isEmpty();
    }

    /** 当前嵌套深度（栈内子菜单层数） */
    public int depth() {
        return stack.size();
    }

    /** 当前层页码 */
    public int currentPage() {
        Level top = stack.peek();
        return top == null ? rootPage : top.page();
    }

    /** 记忆当前层页码（由调用方先取模钳制） */
    public void setPage(int page) {
        Level top = stack.poll();
        if (top == null) {
            rootPage = page;
        } else {
            stack.push(new Level(top.submenuId(), page));
        }
    }

    /** 当前层条目：根层取根表，否则取栈顶子菜单表；栈顶 id 失效时防御回退根表 */
    public List<YsmModelScanner.AnimEntry> currentEntries() {
        Level top = stack.peek();
        if (top == null) {
            return animations.root();
        }
        List<YsmModelScanner.AnimEntry> sub = animations.submenus().get(top.submenuId());
        return sub != null ? sub : animations.root();
    }

    /**
     * 进入子菜单（页码归 0）。
     *
     * @return false = 拒绝（id 不存在或已达最深），调用方应忽略本次点击
     */
    public boolean enter(String submenuId) {
        if (!animations.submenus().containsKey(submenuId)) {
            return false;
        }
        if (stack.size() >= MAX_DEPTH) {
            return false;
        }
        stack.push(new Level(submenuId, 0));
        return true;
    }

    /**
     * 返回上一级并恢复该层页码。
     *
     * @return false = 已在根层（调用方关屏）
     */
    public boolean back() {
        return stack.poll() != null;
    }

    /** 面包屑路径（根 → 当前层的子菜单 id 序列；根层为空表） */
    public List<String> breadcrumb() {
        List<String> path = new ArrayList<>(stack.size());
        stack.descendingIterator().forEachRemaining(level -> path.add(level.submenuId()));
        return path;
    }
}
