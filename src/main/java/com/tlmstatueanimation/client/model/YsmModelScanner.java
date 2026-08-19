package com.tlmstatueanimation.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tlmstatueanimation.TlmStatueAnimation;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * YSM 模型目录扫描器（D1，纯逻辑，无 MC 依赖，可单测）：
 * 扫描 config/yes_steve_model/{built,custom,auth} 下的文件夹模型（含 ysm.json），
 * 解析 properties.extra_animation 构建 modelId → 轮盘动作列表 索引。
 * <p>
 * 已知限制（与文档 D1 一致，首版不支持）：
 * .ysm 加密单文件、properties.extra_animation_classify 子菜单、
 * 服务器同步模型（cache/client 下为会话密钥加密，无法离线解析）。
 */
public final class YsmModelScanner {

    private YsmModelScanner() {
    }

    /**
     * @param key         动作 key（传给 TLM playRouletteAnim / 写 NBT 用）
     * @param displayName 显示名（本地化后）
     */
    public record AnimEntry(String key, String displayName) {
    }

    /**
     * 扫描全部根目录。同一 modelId 在多个根中出现时，排在前面的根优先（putIfAbsent）。
     * 调用方应按期望的覆盖顺序传 roots（如 custom、auth、built——玩家自定义优先于内置）。
     *
     * @param roots  扫描根（不存在的根跳过）
     * @param locale 本地化语言代码（如 zh_cn），用于读取 lang/&lt;locale&gt;.json 覆盖显示名
     * @return modelId → 动作列表（保持 ysm.json 中的声明顺序），无动作的模型不收录
     */
    public static Map<String, List<AnimEntry>> scan(List<Path> roots, String locale) {
        Map<String, List<AnimEntry>> result = new LinkedHashMap<>();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> path.getFileName().toString().equals("ysm.json"))
                        .forEach(ysmJson -> parseOne(root, ysmJson, locale, result));
            } catch (IOException e) {
                TlmStatueAnimation.LOGGER.debug("Skip ysm model root {}: {}", root, e.toString());
            }
            // 压缩包模型（生产环境 custom 目录实测存在 .zip 模型）：打开 zip 文件系统找 ysm.json
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return Files.isRegularFile(path) && (name.endsWith(".zip") || name.endsWith(".ysm"));
                        })
                        .forEach(pack -> parsePack(root, pack, locale, result));
            } catch (IOException e) {
                TlmStatueAnimation.LOGGER.debug("Skip ysm pack scan in {}: {}", root, e.toString());
            }
        }
        return result;
    }

    /**
     * 解析压缩包模型：以 zip 文件系统打开，找其中的 ysm.json。
     * modelId 候选注册多个变体（YSM 官方对压缩包 modelId 的推导规则未见公开证据，故宽进）：
     * 包内相对目录路径、"压缩包文件名(去扩展名)/包内路径"、以及包内路径为空时的文件名变体。
     * 注意：.ysm 单文件若为 YsmCrypt 加密格式则解析会失败并被跳过（已知限制，见文档 D1）。
     */
    private static void parsePack(Path root, Path pack, String locale, Map<String, List<AnimEntry>> result) {
        String fileName = pack.getFileName().toString();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        try (FileSystem zipFs = FileSystems.newFileSystem(pack, (ClassLoader) null)) {
            for (Path zipRoot : zipFs.getRootDirectories()) {
                try (Stream<Path> walk = Files.walk(zipRoot)) {
                    // zip 根目录 "/" 的 getFileName() 为 null，判空过滤
                    walk.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("ysm.json"))
                            .forEach(ysmJson -> {
                                try {
                                    String innerDir = zipRoot.relativize(ysmJson.getParent()).toString()
                                            .replace('\\', '/');
                                    List<AnimEntry> animations = parseModel(ysmJson, locale);
                                    if (animations.isEmpty()) {
                                        return;
                                    }
                                    if (!innerDir.isEmpty()) {
                                        result.putIfAbsent(innerDir, animations);
                                        result.putIfAbsent(baseName + "/" + innerDir, animations);
                                    } else {
                                        result.putIfAbsent(baseName, animations);
                                        result.putIfAbsent(fileName, animations);
                                    }
                                } catch (Exception e) {
                                    TlmStatueAnimation.LOGGER.debug("Skip broken ysm model in pack {}: {}", pack, e.toString());
                                }
                            });
                }
            }
        } catch (Exception e) {
            // 加密 .ysm 或损坏 zip：跳过
            TlmStatueAnimation.LOGGER.debug("Skip ysm pack {}: {}", pack, e.toString());
        }
    }

    private static void parseOne(Path root, Path ysmJson, String locale, Map<String, List<AnimEntry>> result) {
        try {
            Path modelDir = ysmJson.getParent();
            // modelId = 目录相对扫描根的路径，统一 '/' 分隔（D1）
            String modelId = root.relativize(modelDir).toString().replace('\\', '/');
            List<AnimEntry> animations = parseModel(ysmJson, locale);
            if (!animations.isEmpty()) {
                result.putIfAbsent(modelId, animations);
            }
        } catch (Exception e) {
            // 单个模型解析失败只跳过该模型，不炸整个扫描
            TlmStatueAnimation.LOGGER.debug("Skip broken ysm model at {}: {}", ysmJson, e.toString());
        }
    }

    /**
     * 解析单个 ysm.json：properties.extra_animation（key → 默认显示名，保持声明顺序），
     * 再尝试用同目录 lang/&lt;locale&gt;.json 的 "properties.extra_animation.&lt;key&gt;" 覆盖显示名。
     */
    static List<AnimEntry> parseModel(Path ysmJson, String locale) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(ysmJson, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!root.has("properties") || !root.get("properties").isJsonObject()) {
            return List.of();
        }
        JsonObject properties = root.getAsJsonObject("properties");
        if (!properties.has("extra_animation") || !properties.get("extra_animation").isJsonObject()) {
            return List.of();
        }
        JsonObject extraAnimation = properties.getAsJsonObject("extra_animation");
        JsonObject lang = readLang(ysmJson.getParent(), locale);

        List<AnimEntry> animations = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : extraAnimation.entrySet()) {
            String key = entry.getKey();
            String displayName = entry.getValue().getAsString();
            String langKey = "properties.extra_animation." + key;
            if (lang != null && lang.has(langKey) && lang.get(langKey).isJsonPrimitive()) {
                displayName = lang.get(langKey).getAsString();
            }
            animations.add(new AnimEntry(key, displayName));
        }
        return animations;
    }

    private static JsonObject readLang(Path modelDir, String locale) {
        Path langFile = modelDir.resolve("lang").resolve(locale + ".json");
        if (!Files.isRegularFile(langFile)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
