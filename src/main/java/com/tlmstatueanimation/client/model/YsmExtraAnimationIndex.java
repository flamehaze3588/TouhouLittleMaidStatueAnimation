package com.tlmstatueanimation.client.model;

import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.gui.RouletteEntry;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YSM 模型 extra 动作表索引（D1）：对 T6 调用方暴露的稳定 API。
 * 真实扫描逻辑见 {@link YsmModelScanner}；本类仅为 MC 胶水层（游戏目录定位 + 懒加载缓存）。
 * <p>
 * 刷新时机：首次 lookup 时懒扫描。理由：轮盘只在玩家进入世界后才会被打开，
 * 此时 YSM 早已完成启动期的 built 目录重建，扫描结果必然完整；无需额外事件钩子。
 */
public final class YsmExtraAnimationIndex {
    private static volatile Map<String, List<RouletteEntry>> index = Map.of();
    private static volatile boolean scanned = false;

    private YsmExtraAnimationIndex() {
    }

    public static List<RouletteEntry> lookup(String modelId) {
        ensureScanned();
        return index.getOrDefault(modelId, List.of());
    }

    /** 强制重扫（预留：后续如需监听模型目录变化或重载事件时调用）。 */
    public static synchronized void refresh() {
        Path ysmDir = FMLPaths.GAMEDIR.get().resolve("config").resolve("yes_steve_model");
        String locale = Minecraft.getInstance().options.languageCode;
        // 顺序即覆盖优先级：玩家自定义 > 授权模型 > 内置模型。
        // 注意：官方 2.6.5 生产环境的内置目录名实测为 "builtin"（OpenYSM 源码写作 "built"），两者都扫。
        List<Path> roots = List.of(ysmDir.resolve("custom"), ysmDir.resolve("auth"),
                ysmDir.resolve("builtin"), ysmDir.resolve("built"));
        Map<String, List<YsmModelScanner.AnimEntry>> scannedModels = YsmModelScanner.scan(roots, locale);
        Map<String, List<RouletteEntry>> newIndex = new LinkedHashMap<>();
        scannedModels.forEach((modelId, animations) -> newIndex.put(modelId,
                animations.stream().map(a -> new RouletteEntry(a.key(), a.displayName())).toList()));
        index = newIndex;
        scanned = true;
        TlmStatueAnimation.LOGGER.info("YSM extra animation index refreshed: {} models indexed", newIndex.size());
    }

    private static void ensureScanned() {
        if (!scanned) {
            refresh();
        }
    }
}
