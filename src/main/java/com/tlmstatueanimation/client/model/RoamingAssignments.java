package com.tlmstatueanimation.client.model;

/**
 * YSM molang 漫游变量赋值串解析（§8.22，纯逻辑，无 MC 依赖，可单测）。
 * 配置按钮的 value 槽形态：
 * <ul>
 *   <li>checkbox："v.roaming.B"（变量名本体，勾选时拼 "=1"/"=0"）；</li>
 *   <li>radio 标签 / slider："v.roaming.bagemotion=2;"（完整赋值串，可能带尾分号）。</li>
 * </ul>
 * 变量名合法性镜像 YSM RoamingStruct 约束（最长 32，字母/下划线/数字）。
 */
public final class RoamingAssignments {
    public static final String PREFIX = "v.roaming.";
    /** YSM RoamingStruct.MAX_VAR_NAME_LENGTH */
    public static final int MAX_VAR_NAME_LENGTH = 32;

    /** 解析结果：变量名（不含 "v.roaming." 前缀）+ 浮点值 */
    public record Assignment(String varName, float value) {
    }

    private RoamingAssignments() {
    }

    /** checkbox 的 value 槽（"v.roaming.B"）→ 变量名；不合法返回 null */
    public static String checkboxVarName(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        String name = value.substring(PREFIX.length());
        return isValidVarName(name) ? name : null;
    }

    /** 完整赋值串（"v.roaming.x=2" / "v.roaming.x=2;"）→ 赋值对；不合法返回 null */
    public static Assignment parse(String assignment) {
        if (assignment == null) {
            return null;
        }
        String text = assignment.endsWith(";") ? assignment.substring(0, assignment.length() - 1) : assignment;
        int eq = text.indexOf('=');
        if (eq < 0) {
            return null;
        }
        String name = checkboxVarName(text.substring(0, eq));
        if (name == null) {
            return null;
        }
        float value;
        try {
            value = Float.parseFloat(text.substring(eq + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return Float.isFinite(value) ? new Assignment(name, value) : null;
    }

    /** 变量名合法性：字母/下划线开头，后续可含数字，总长 ≤32 */
    public static boolean isValidVarName(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]{0," + (MAX_VAR_NAME_LENGTH - 1) + "}");
    }
}
