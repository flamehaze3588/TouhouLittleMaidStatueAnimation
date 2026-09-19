package com.tlmstatueanimation.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tlmstatueanimation.TlmStatueAnimation;
import com.tlmstatueanimation.client.model.ysmfile.YsmBinaryModelWalker;
import com.tlmstatueanimation.client.model.ysmfile.YsmFileDecryptor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * YSM 模型目录扫描器（D1/D2/§8.22，纯逻辑，无 MC 依赖，可单测）：
 * 扫描 config/yes_steve_model/{built,custom,auth} 下的文件夹模型（含 ysm.json）、
 * zip 包模型与 .ysm 加密单文件模型，解析 properties.extra_animation、
 * properties.extra_animation_classify 与 properties.extra_animation_buttons（molang 配置按钮），
 * 构建 modelId → 轮盘数据（根表 + 子菜单表 + 配置按钮）索引。
 * <p>
 * extra_animation 表中 value 以 '#' 开头的条目是配置按钮引用（'#staff' → extra_animation_buttons
 * 中 id=staff 的按钮），轮盘条目转为 '#config:<id>' 标记保留原位；未被任何表引用的按钮
 * 追加到根表末尾（YSM 二进制模型常见此形态）。key 以 '#' 开头的条目是子菜单入口，保留。
 * <p>
 * 已知限制（与文档 D1 一致）：
 * 服务器同步模型（cache/client 下为会话密钥加密，无法离线解析）。
 */
public final class YsmModelScanner {

    private YsmModelScanner() {
    }

    /**
     * @param key         动作 key（传给 TLM playRouletteAnim / 写 NBT 用；'#' 前缀 = 子菜单入口；
     *                    '#config:' 前缀 = 配置按钮入口）
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
     * @return modelId → 轮盘数据（保持 ysm.json 中的声明顺序），无动作且无配置按钮的模型不收录
     */
    public static Map<String, ModelAnimations> scan(List<Path> roots, String locale) {
        Map<String, ModelAnimations> result = new LinkedHashMap<>();
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
            // 压缩包/单文件模型：.zip 走 zip 文件系统找 ysm.json；.ysm 走 YsmCrypt 解密 + 二进制走读
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return Files.isRegularFile(path) && (name.endsWith(".zip") || name.endsWith(".ysm"));
                        })
                        .forEach(pack -> {
                            if (pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ysm")) {
                                parseYsmFile(root, pack, locale, result);
                            } else {
                                parsePack(root, pack, locale, result);
                            }
                        });
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
     * 注意：本方法仅处理 .zip；.ysm 加密单文件由 {@link #parseYsmFile} 处理。
     */
    private static void parsePack(Path root, Path pack, String locale, Map<String, ModelAnimations> result) {
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
                                    ModelAnimations animations = parseModel(ysmJson, locale);
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
            // 损坏 zip：跳过
            TlmStatueAnimation.LOGGER.debug("Skip ysm pack {}: {}", pack, e.toString());
        }
    }

    /**
     * 解析 .ysm 加密单文件模型：YsmCrypt 解密 → zstd 解压 → 二进制走读（format >= 16）。
     * modelId 以「相对扫描根的路径（含 .ysm 后缀，'/' 分隔）」为主注册——实测雕像 NBT 中
     * 这类模型的 modelId 即带后缀（如 "小女仆抱枕2.6.ysm"）；同时 putIfAbsent 一个
     * 去后缀变体兜底。显示名优先取内嵌语言表中当前 locale 的
     * "properties.extra_animation.&lt;key&gt;"，缺失回退默认显示名（子菜单表同规则）。
     * 配置按钮段（format > 9）由走读器捕获，同 JSON 路径共用装配逻辑。
     * 解密/走读失败（crypto 版本不支持、format < 4、文件损坏等）只跳过该文件。
     */
    private static void parseYsmFile(Path root, Path file, String locale, Map<String, ModelAnimations> result) {
        try {
            byte[] decrypted = YsmFileDecryptor.decryptYsmFile(Files.readAllBytes(file));
            YsmBinaryModelWalker.YsmModelData data = YsmBinaryModelWalker.walk(decrypted);
            Map<String, String> lang = data.languageFiles().get(locale);
            List<YsmBinaryModelWalker.YsmConfigButton> buttons = localizeButtons(data.configButtons(), null, lang);
            ModelAnimations animations = assemble(data.extraAnimations(), data.extraAnimationClassify(), buttons,
                    (key, fallback) -> localizeAnimationName(key, fallback, lang));
            if (animations.isEmpty()) {
                return;
            }
            String modelId = root.relativize(file).toString().replace('\\', '/');
            result.putIfAbsent(modelId, animations);
            String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".ysm")) {
                // 去后缀兜底变体（截断 modelId 末尾的 ".ysm"）
                result.putIfAbsent(modelId.substring(0, modelId.length() - 4), animations);
            }
        } catch (Exception e) {
            // 加密版本不支持/格式过旧/文件损坏：跳过该文件
            TlmStatueAnimation.LOGGER.debug("Skip ysm file {}: {}", file, e.toString());
        }
    }

    private static void parseOne(Path root, Path ysmJson, String locale, Map<String, ModelAnimations> result) {
        try {
            Path modelDir = ysmJson.getParent();
            // modelId = 目录相对扫描根的路径，统一 '/' 分隔（D1）
            String modelId = root.relativize(modelDir).toString().replace('\\', '/');
            ModelAnimations animations = parseModel(ysmJson, locale);
            if (!animations.isEmpty()) {
                result.putIfAbsent(modelId, animations);
            }
        } catch (Exception e) {
            // 单个模型解析失败只跳过该模型，不炸整个扫描
            TlmStatueAnimation.LOGGER.debug("Skip broken ysm model at {}: {}", ysmJson, e.toString());
        }
    }

    /**
     * 解析单个 ysm.json：properties.extra_animation（根表）、extra_animation_classify
     * （[{id, extra_animation: {...}}]，子表可再嵌套 '#id' 入口）与 extra_animation_buttons
     * （[{id, name, config_forms: [...]}]），再尝试用同目录 lang/&lt;locale&gt;.json 覆盖显示名。
     */
    static ModelAnimations parseModel(Path ysmJson, String locale) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(ysmJson, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!root.has("properties") || !root.get("properties").isJsonObject()) {
            return ModelAnimations.EMPTY;
        }
        JsonObject properties = root.getAsJsonObject("properties");
        JsonObject lang = readLang(ysmJson.getParent(), locale);

        List<YsmBinaryModelWalker.YsmConfigButton> buttons = localizeButtons(
                parseButtonsJson(properties), lang, null);
        return assemble(
                properties.has("extra_animation") && properties.get("extra_animation").isJsonObject()
                        ? toStringMap(properties.getAsJsonObject("extra_animation")) : null,
                parseClassifyJson(properties), buttons,
                (key, fallback) -> localizeAnimationName(key, fallback, lang));
    }

    private static Map<String, String> toStringMap(JsonObject obj) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsString());
        }
        return map;
    }

    // ---------- 装配（JSON/二进制两路径共用） ----------

    @FunctionalInterface
    private interface NameLocalizer {
        /** 动作显示名本地化：lang 表命中用本地化值，否则回退默认名 */
        String localize(String key, String fallback);
    }

    /** 动作表 → 轮盘条目：'#' 开头的 value 是配置按钮引用，转为 '#config:<id>' 标记（按钮不存在则丢弃） */
    private static ModelAnimations assemble(Map<String, String> rootTable, Map<String, ? extends Map<String, String>> classifyTables,
                                            List<YsmBinaryModelWalker.YsmConfigButton> buttons,
                                            NameLocalizer nameLocalizer) {
        Set<String> referenced = new HashSet<>();
        List<AnimEntry> root = toEntries(rootTable, buttons, referenced, nameLocalizer);
        Map<String, List<AnimEntry>> submenus = new LinkedHashMap<>();
        classifyTables.forEach((id, table) -> submenus.put(id, toEntries(table, buttons, referenced, nameLocalizer)));
        // 未被任何表引用的按钮追加到根表末尾（二进制模型常见此形态：按钮段独立存在）
        for (YsmBinaryModelWalker.YsmConfigButton button : buttons) {
            if (!referenced.contains(button.id())) {
                root.add(new AnimEntry(ModelAnimations.CONFIG_KEY_PREFIX + button.id(), button.name()));
            }
        }
        return new ModelAnimations(root, submenus, buttons);
    }

    private static List<AnimEntry> toEntries(Map<String, String> table,
                                             List<YsmBinaryModelWalker.YsmConfigButton> buttons,
                                             Set<String> referenced, NameLocalizer nameLocalizer) {
        List<AnimEntry> entries = new ArrayList<>();
        if (table == null) {
            return entries;
        }
        int index = 0;
        for (Map.Entry<String, String> entry : table.entrySet()) {
            String key = entry.getKey();
            String displayName = entry.getValue();
            if (displayName.startsWith("#")) {
                // 配置按钮引用：标签仍走 properties.extra_animation.<key> 的 lang 覆盖，
                // 回退为按钮名（YSM renderRadialButtons 同款逻辑，§8.22 修正）
                String refId = displayName.substring(1);
                YsmBinaryModelWalker.YsmConfigButton button = findButton(buttons, refId);
                if (button != null) {
                    referenced.add(refId);
                    String fallback = button.name().isBlank() ? String.valueOf(index) : button.name();
                    entries.add(new AnimEntry(ModelAnimations.CONFIG_KEY_PREFIX + refId,
                            nameLocalizer.localize(key, fallback)));
                }
                index++;
                continue;
            }
            // 空显示名回退到槽位序号（YSM 同款：extra7 空名 → 显示 "7"）
            String fallback = displayName.isBlank() ? String.valueOf(index) : displayName;
            entries.add(new AnimEntry(key, nameLocalizer.localize(key, fallback)));
            index++;
        }
        return entries;
    }

    private static YsmBinaryModelWalker.YsmConfigButton findButton(List<YsmBinaryModelWalker.YsmConfigButton> buttons, String id) {
        for (YsmBinaryModelWalker.YsmConfigButton button : buttons) {
            if (button.id().equals(id)) {
                return button;
            }
        }
        return null;
    }

    /** 动作显示名本地化（二进制内嵌 lang 表） */
    private static String localizeAnimationName(String key, String fallback, Map<String, String> lang) {
        String langKey = "properties.extra_animation." + key;
        if (lang != null && lang.containsKey(langKey)) {
            return lang.get(langKey);
        }
        return fallback;
    }

    /** 动作显示名本地化（文件夹/zip 模型的 lang/&lt;locale&gt;.json） */
    private static String localizeAnimationName(String key, String fallback, JsonObject lang) {
        String langKey = "properties.extra_animation." + key;
        if (lang != null && lang.has(langKey) && lang.get(langKey).isJsonPrimitive()) {
            return lang.get(langKey).getAsString();
        }
        return fallback;
    }

    // ---------- 配置按钮解析与本地化 ----------

    /** JSON 形态：properties.extra_animation_buttons → 原始按钮记录（未本地化） */
    private static List<YsmBinaryModelWalker.YsmConfigButton> parseButtonsJson(JsonObject properties) {
        List<YsmBinaryModelWalker.YsmConfigButton> buttons = new ArrayList<>();
        JsonElement element = properties.get("extra_animation_buttons");
        if (element == null || !element.isJsonArray()) {
            return buttons;
        }
        for (JsonElement btnElement : element.getAsJsonArray()) {
            if (!btnElement.isJsonObject()) {
                continue;
            }
            JsonObject btnObj = btnElement.getAsJsonObject();
            List<YsmBinaryModelWalker.ConfigForm> forms = new ArrayList<>();
            JsonElement formsElement = btnObj.get("config_forms");
            if (formsElement != null && formsElement.isJsonArray()) {
                for (JsonElement formElement : formsElement.getAsJsonArray()) {
                    if (!formElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject formObj = formElement.getAsJsonObject();
                    LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                    JsonElement labelsElement = formObj.get("labels");
                    if (labelsElement != null && labelsElement.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> label : labelsElement.getAsJsonObject().entrySet()) {
                            labels.put(label.getKey(), label.getValue().getAsString());
                        }
                    }
                    forms.add(new YsmBinaryModelWalker.ConfigForm(
                            getStr(formObj, "type"), getStr(formObj, "title"), getStr(formObj, "description"),
                            getStr(formObj, "value"), getFloat(formObj, "step"), getFloat(formObj, "min"),
                            getFloat(formObj, "max"), labels));
                }
            }
            buttons.add(new YsmBinaryModelWalker.YsmConfigButton(
                    getStr(btnObj, "id"), getStr(btnObj, "name"), forms));
        }
        return buttons;
    }

    /**
     * 按钮本地化（YSM lang 键格式，与 AnimationRouletteScreen 一致）：
     * 按钮名 properties.extra_animation_buttons.&lt;id&gt;.name；
     * 表单标题/描述 ...config_forms.&lt;i&gt;.title / .description；
     * 单选标签 ...config_forms.&lt;i&gt;.labels.&lt;j&gt;（按声明序）。缺失一律回退原文。
     * lang 的两种来源（JSON 文件 / 二进制内嵌表）各传一个。
     */
    private static List<YsmBinaryModelWalker.YsmConfigButton> localizeButtons(
            List<YsmBinaryModelWalker.YsmConfigButton> buttons, JsonObject langJson, Map<String, String> langMap) {
        List<YsmBinaryModelWalker.YsmConfigButton> result = new ArrayList<>(buttons.size());
        for (YsmBinaryModelWalker.YsmConfigButton button : buttons) {
            String prefix = "properties.extra_animation_buttons." + button.id();
            String name = langText(langJson, langMap, prefix + ".name", button.name());
            List<YsmBinaryModelWalker.ConfigForm> forms = new ArrayList<>(button.forms().size());
            for (int i = 0; i < button.forms().size(); i++) {
                YsmBinaryModelWalker.ConfigForm form = button.forms().get(i);
                String formPrefix = prefix + ".config_forms." + i;
                LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                int labelIndex = 0;
                for (Map.Entry<String, String> label : form.labels().entrySet()) {
                    labels.put(langText(langJson, langMap, formPrefix + ".labels." + labelIndex, label.getKey()),
                            label.getValue());
                    labelIndex++;
                }
                forms.add(new YsmBinaryModelWalker.ConfigForm(form.type(),
                        langText(langJson, langMap, formPrefix + ".title", form.title()),
                        langText(langJson, langMap, formPrefix + ".description", form.description()),
                        form.value(), form.step(), form.min(), form.max(), labels));
            }
            result.add(new YsmBinaryModelWalker.YsmConfigButton(button.id(), name, forms));
        }
        return result;
    }

    private static String langText(JsonObject langJson, Map<String, String> langMap, String key, String fallback) {
        if (langJson != null && langJson.has(key) && langJson.get(key).isJsonPrimitive()) {
            return langJson.get(key).getAsString();
        }
        if (langMap != null && langMap.containsKey(key)) {
            return langMap.get(key);
        }
        return fallback;
    }

    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
    }

    private static float getFloat(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsFloat() : 0.0f;
    }

    // ---------- 原有动作表/子菜单解析 ----------

    /** extra_animation_classify：[{id, extra_animation: {...}}]；格式不符的元素跳过 */
    private static Map<String, Map<String, String>> parseClassifyJson(JsonObject properties) {
        Map<String, Map<String, String>> submenus = new LinkedHashMap<>();
        JsonElement classify = properties.get("extra_animation_classify");
        if (classify == null || !classify.isJsonArray()) {
            return submenus;
        }
        for (JsonElement element : classify.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject classifyEntry = element.getAsJsonObject();
            if (!classifyEntry.has("id") || !classifyEntry.get("id").isJsonPrimitive()) {
                continue;
            }
            String id = classifyEntry.get("id").getAsString();
            Map<String, String> table = new LinkedHashMap<>();
            if (classifyEntry.has("extra_animation") && classifyEntry.get("extra_animation").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : classifyEntry.getAsJsonObject("extra_animation").entrySet()) {
                    table.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            submenus.put(id, table);
        }
        return submenus;
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
