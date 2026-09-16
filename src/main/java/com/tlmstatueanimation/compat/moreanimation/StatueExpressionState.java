package com.tlmstatueanimation.compat.moreanimation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 雕像表情/动作控制屏的纯逻辑层：ForgeData 状态读取与 C2S 包键值拼装。
 * 语义逐项镜像 moreanimation 的 MaidAnimationData / TerminalControlPacket，
 * 但只操作 {@link CompoundTag} 并参数化目录/默认值，不引用任何 moreanimation 类，可单测。
 */
public final class StatueExpressionState {

    private StatueExpressionState() {
    }

    // ---------- ForgeData 读取（镜像 MaidAnimationData 各 getter 的缺省语义） ----------

    /**
     * 分类动作启用列表（镜像 MaidAnimationData.enabledActions）：
     * 键缺失 → 返回配置默认值副本；键存在 → 读 ListTag 并过滤掉目录中不存在的动作。
     * 与原版差异：不回写默认值（雕像 NBT 由服务端持有，客户端读副本，待用户切换时整体写入）。
     */
    public static List<String> enabledActions(CompoundTag forgeData, String category,
                                              List<String> catalog, List<String> defaults) {
        String key = MoreAnimationNbtKeys.ENABLED_PREFIX + category;
        if (!forgeData.contains(key, Tag.TAG_LIST)) {
            return new ArrayList<>(defaults);
        }
        List<String> result = new ArrayList<>();
        ListTag list = forgeData.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String action = list.getString(i);
            if (catalog.contains(action)) {
                result.add(action);
            }
        }
        return result;
    }

    /** 启用列表 → 位掩码（位序 = 目录顺序，与 moreanimation TerminalDataPacket 一致）。 */
    public static int maskOf(List<String> enabled, List<String> catalog) {
        int mask = 0;
        for (int i = 0; i < catalog.size(); i++) {
            if (enabled.contains(catalog.get(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /** 位掩码 → 启用列表（目录顺序）。 */
    public static List<String> enabledFromMask(int mask, List<String> catalog) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                result.add(catalog.get(i));
            }
        }
        return result;
    }

    /** 镜像 MaidAnimationData.injuredAuto：未显式设置过 → true。 */
    public static boolean injuredAuto(CompoundTag forgeData) {
        return !forgeData.getBoolean(MoreAnimationNbtKeys.INJURED_AUTO_SET)
                || forgeData.getBoolean(MoreAnimationNbtKeys.INJURED_AUTO);
    }

    /** 镜像 MaidAnimationData.autoPet：显式设置优先，否则用配置默认值。 */
    public static boolean autoPet(CompoundTag forgeData, boolean configDefault) {
        return flagOrDefault(forgeData, MoreAnimationNbtKeys.AUTO_PET_SET,
                MoreAnimationNbtKeys.AUTO_PET, configDefault);
    }

    /** 镜像 MaidAnimationData.autoHug：显式设置优先，否则用配置默认值。 */
    public static boolean autoHug(CompoundTag forgeData, boolean configDefault) {
        return flagOrDefault(forgeData, MoreAnimationNbtKeys.AUTO_HUG_SET,
                MoreAnimationNbtKeys.AUTO_HUG, configDefault);
    }

    /** 镜像 MaidAnimationData.randomSleepPose：未显式设置过 → true。 */
    public static boolean randomSleepPose(CompoundTag forgeData) {
        return !forgeData.getBoolean(MoreAnimationNbtKeys.RANDOM_SLEEP_POSE_SET)
                || forgeData.getBoolean(MoreAnimationNbtKeys.RANDOM_SLEEP_POSE);
    }

    private static boolean flagOrDefault(CompoundTag forgeData, String setKey, String valueKey,
                                         boolean configDefault) {
        return forgeData.getBoolean(setKey) ? forgeData.getBoolean(valueKey) : configDefault;
    }

    /** 镜像 MaidAnimationData.formMode：读取时越界回退 FORM_AUTO（写入侧的夹取见 {@link #formModeKeys}）。 */
    public static int formMode(CompoundTag forgeData) {
        int mode = forgeData.getInt(MoreAnimationNbtKeys.FORM_MODE);
        return mode >= MoreAnimationNbtKeys.FORM_AUTO && mode <= MoreAnimationNbtKeys.FORM_FOX
                ? mode : MoreAnimationNbtKeys.FORM_AUTO;
    }

    // ---------- 发包键值拼装（镜像 ExpressionScreen 各 sendControl/sendExpression 写点） ----------

    /** 表情：{@code moreanimation_expression = expressionId}。 */
    public static CompoundTag expressionKeys(String expressionId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(MoreAnimationNbtKeys.EXPRESSION, expressionId);
        return tag;
    }

    /** 表情停止：删除 moreanimation_expression（原版 ExpressionPacket "stop" 语义）。 */
    public static List<String> expressionStopRemoveKeys() {
        return List.of(MoreAnimationNbtKeys.EXPRESSION);
    }

    /**
     * 雕像"停止"按钮（§8.18）：表情 + 动作全停。雕像是装饰——其动作 UNTIL=MAX 无限循环，
     * 不会像真女仆一样自然过期，故停止必须同时清空 ACTIVE* 键。
     * 注意不动 STATUE_BASE_POSE（记忆标记）：坐下状态停止动作后由 StatueActionPhaseTicker
     * 恢复基础姿势（若有）。
     */
    public static List<String> stopAllRemoveKeys() {
        return List.of(MoreAnimationNbtKeys.EXPRESSION,
                MoreAnimationNbtKeys.ACTIVE, MoreAnimationNbtKeys.ACTIVE_START,
                MoreAnimationNbtKeys.ACTIVE_UNTIL, MoreAnimationNbtKeys.ACTIVE_PRIORITY,
                MoreAnimationNbtKeys.ACTIVE_LOCK_MOVEMENT);
    }

    /**
     * 立即播放一次性动作：只发动作 key；
     * start/until/priority/lock_movement 由服务端用 gameTime 与时长补齐（见 C2S 包 handler）。
     */
    public static CompoundTag playKeys(String action) {
        CompoundTag tag = new CompoundTag();
        tag.putString(MoreAnimationNbtKeys.ACTIVE, action);
        return tag;
    }

    /**
     * 分类动作启用开关（镜像 MaidAnimationData.setEnabled）：action 不在目录中返回 null（拒绝）；
     * 否则返回写有新列表的 setKeys。
     */
    @Nullable
    public static CompoundTag categoryEnabledKeys(String category, List<String> catalog,
                                                  List<String> currentEnabled, String action,
                                                  boolean enabled) {
        if (!catalog.contains(action)) {
            return null;
        }
        List<String> values = new ArrayList<>(currentEnabled);
        if (enabled && !values.contains(action)) {
            values.add(action);
        }
        if (!enabled) {
            values.remove(action);
        }
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        CompoundTag tag = new CompoundTag();
        tag.put(MoreAnimationNbtKeys.ENABLED_PREFIX + category, list);
        return tag;
    }

    /** 带"已显式设置"标记的布尔开关（镜像 setInjuredAuto/setAutoPet/setAutoHug/setRandomSleepPose）。 */
    public static CompoundTag boolSwitchKeys(String setKey, String valueKey, boolean value) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(setKey, true);
        tag.putBoolean(valueKey, value);
        return tag;
    }

    /** 形态模式（镜像 MaidAnimationData.setFormMode：夹取后写入 int）。 */
    public static CompoundTag formModeKeys(int mode) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(MoreAnimationNbtKeys.FORM_MODE, clampFormMode(mode));
        return tag;
    }

    /**
     * 互动动作 → 雕像侧单人可见动作的映射。
     * 原版的 pet_owner/pet_maid/hug_owner/hug_maid 需要真实女仆与目标实体组成双人会话
     *（MaidInteractionEvent），雕像（装饰方块实体）无此概念，这里简化为播放该互动中
     * 女仆侧的单人动作：摸头 → pet_other_head；拥抱 → hugtogether。未知互动返回空串（不发送）。
     */
    public static String interactionSoloAction(String interaction) {
        return switch (interaction) {
            case "pet_owner", "pet_maid" -> "pet_other_head";
            case "hug_owner", "hug_maid" -> "hugtogether";
            default -> "";
        };
    }

    private static int clampFormMode(int mode) {
        return Math.max(MoreAnimationNbtKeys.FORM_AUTO, Math.min(MoreAnimationNbtKeys.FORM_FOX, mode));
    }
}
