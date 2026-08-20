package com.tlmstatueanimation.client.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YsmModelScanner 测试（D1 磁盘扫描解析 + D2 classify 子菜单）。
 */
class YsmModelScannerTest {

    @TempDir
    Path tempDir;

    private Path createModel(Path root, String relativeDir, String extraAnimationJson) throws IOException {
        return createModelWithProperties(root, relativeDir,
                "{ \"extra_animation\": " + extraAnimationJson + " }");
    }

    private Path createModelWithProperties(Path root, String relativeDir, String propertiesJson) throws IOException {
        Path modelDir = root.resolve(relativeDir);
        Files.createDirectories(modelDir);
        String ysmJson = """
                {
                  "spec": 2,
                  "properties": %s
                }
                """.formatted(propertiesJson);
        Files.writeString(modelDir.resolve("ysm.json"), ysmJson, StandardCharsets.UTF_8);
        return modelDir;
    }

    @Test
    void scansModelIdAsRelativePath() throws IOException {
        Path root = tempDir.resolve("built");
        createModel(root, "wine_fox/12_little", """
                { "extra0": "变身", "extra1": "打招呼" }
                """);

        Map<String, ModelAnimations> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        assertTrue(result.containsKey("wine_fox/12_little"));
        assertFalse(result.containsKey("wine_fox\\12_little"));
    }

    @Test
    void keepsDeclarationOrderAndDefaultNames() throws IOException {
        Path root = tempDir.resolve("custom");
        createModel(root, "my_model", """
                { "extra2": "鼓掌", "extra0": "变身", "extra7": "哭哭" }
                """);

        List<YsmModelScanner.AnimEntry> anims = YsmModelScanner.scan(List.of(root), "zh_cn").get("my_model").root();

        assertEquals(List.of("extra2", "extra0", "extra7"),
                anims.stream().map(YsmModelScanner.AnimEntry::key).toList());
        assertEquals("鼓掌", anims.get(0).displayName());
    }

    @Test
    void localeLangOverridesDisplayName() throws IOException {
        Path root = tempDir.resolve("custom");
        Path modelDir = createModel(root, "fox", """
                { "extra0": "Default Name", "extra1": "Another" }
                """);
        Files.createDirectories(modelDir.resolve("lang"));
        Files.writeString(modelDir.resolve("lang").resolve("zh_cn.json"),
                "{ \"properties.extra_animation.extra0\": \"变身\" }", StandardCharsets.UTF_8);

        List<YsmModelScanner.AnimEntry> anims = YsmModelScanner.scan(List.of(root), "zh_cn").get("fox").root();

        assertEquals("变身", anims.get(0).displayName());
        // lang 中缺失的 key 回退默认显示名
        assertEquals("Another", anims.get(1).displayName());
    }

    @Test
    void missingLangFileFallsBackToDefault() throws IOException {
        Path root = tempDir.resolve("custom");
        createModel(root, "fox", """
                { "extra0": "Default Name" }
                """);

        List<YsmModelScanner.AnimEntry> anims = YsmModelScanner.scan(List.of(root), "en_us").get("fox").root();

        assertEquals("Default Name", anims.get(0).displayName());
    }

    @Test
    void brokenJsonIsSkipped() throws IOException {
        Path root = tempDir.resolve("custom");
        Path brokenDir = root.resolve("broken");
        Files.createDirectories(brokenDir);
        Files.writeString(brokenDir.resolve("ysm.json"), "{ not valid json !!!", StandardCharsets.UTF_8);
        createModel(root, "good", """
                { "extra0": "好" }
                """);

        Map<String, ModelAnimations> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        assertFalse(result.containsKey("broken"));
        assertTrue(result.containsKey("good"));
    }

    @Test
    void modelWithoutExtraAnimationIsNotIndexed() throws IOException {
        Path root = tempDir.resolve("custom");
        Path modelDir = root.resolve("no_anim");
        Files.createDirectories(modelDir);
        Files.writeString(modelDir.resolve("ysm.json"),
                "{ \"spec\": 2, \"properties\": { \"free\": true } }", StandardCharsets.UTF_8);

        Map<String, ModelAnimations> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        assertTrue(result.isEmpty());
    }

    @Test
    void nonExistentRootIsSkipped() {
        Map<String, ModelAnimations> result = YsmModelScanner.scan(
                List.of(tempDir.resolve("does_not_exist")), "zh_cn");
        assertTrue(result.isEmpty());
    }

    @Test
    void earlierRootWinsOnDuplicateModelId() throws IOException {
        Path custom = tempDir.resolve("custom");
        Path built = tempDir.resolve("built");
        createModel(custom, "fox", """
                { "extra0": "自定义版" }
                """);
        createModel(built, "fox", """
                { "extra0": "内置版", "extra1": "内置动作二" }
                """);

        Map<String, ModelAnimations> result = YsmModelScanner.scan(List.of(custom, built), "zh_cn");

        assertEquals(1, result.get("fox").root().size());
        assertEquals("自定义版", result.get("fox").root().get(0).displayName());
    }

    @Test
    void zipPackModelIsScannedWithCandidateIds() throws IOException {
        Path custom = tempDir.resolve("custom");
        Files.createDirectories(custom);
        // zip 内嵌一层目录的模型
        Path zipFile = custom.resolve("testmaid.zip");
        try (FileSystem zipFs = FileSystems.newFileSystem(zipFile, Map.of("create", "true"))) {
            Path inner = zipFs.getPath("/testmaid");
            Files.createDirectories(inner);
            Files.writeString(inner.resolve("ysm.json"), """
                    { "spec": 2, "properties": { "extra_animation": { "extra0": "跳舞" } } }
                    """, StandardCharsets.UTF_8);
        }

        Map<String, ModelAnimations> result = YsmModelScanner.scan(List.of(custom), "zh_cn");

        // 包内相对路径与 "文件名/包内路径" 两种候选都可命中
        assertTrue(result.containsKey("testmaid"));
        assertTrue(result.containsKey("testmaid/testmaid"));
        assertEquals("跳舞", result.get("testmaid").root().get(0).displayName());
    }

    @Test
    void parsesClassifySubmenusWithNestingAndFiltersConfigButtons() throws IOException {
        Path root = tempDir.resolve("builtin");
        // 根表：普通动作 + '#一级' 子菜单入口 + 配置按钮（value 以 '#' 开头，应被过滤）；
        // classify 两层嵌套：一级表内含 '#return' 返回条目与 '#二级' 入口，二级表含深层动作
        Path modelDir = createModelWithProperties(root, "wine_fox/08_sta", """
                {
                  "extra_animation": {
                    "extra0": "变身",
                    "#一级": "§9舞蹈/动作",
                    "部署阔剑拌雷": "#kuojian"
                  },
                  "extra_animation_classify": [
                    {
                      "id": "一级",
                      "extra_animation": {
                        "dance0": "舞蹈一",
                        "#二级": "二级菜单",
                        "#return": "返回按钮",
                        "配置项": "#config_form"
                      }
                    },
                    {
                      "id": "二级",
                      "extra_animation": {
                        "dance1": "深层舞蹈"
                      }
                    }
                  ]
                }
                """);

        ModelAnimations animations = YsmModelScanner.scan(List.of(root), "zh_cn").get("wine_fox/08_sta");
        assertNotNull(animations);

        // 根表：配置按钮（value 以 '#' 开头）整条丢弃；'#一级' 子菜单入口保留且顺序不变
        assertEquals(List.of("extra0", "#一级"),
                animations.root().stream().map(YsmModelScanner.AnimEntry::key).toList());
        assertEquals("§9舞蹈/动作", animations.root().get(1).displayName());

        // 一级子表：正常条目 + 嵌套 '#二级' 入口 + '#return' 返回条目；子表内配置按钮同样被过滤
        List<YsmModelScanner.AnimEntry> level1 = animations.submenus().get("一级");
        assertNotNull(level1);
        assertEquals(List.of("dance0", "#二级", "#return"),
                level1.stream().map(YsmModelScanner.AnimEntry::key).toList());
        assertEquals("返回按钮", level1.get(2).displayName());

        // 二级嵌套子表存在
        List<YsmModelScanner.AnimEntry> level2 = animations.submenus().get("二级");
        assertNotNull(level2);
        assertEquals(List.of("dance1"), level2.stream().map(YsmModelScanner.AnimEntry::key).toList());
    }

    @Test
    void classifyEntriesUseSameLangTable() throws IOException {
        Path root = tempDir.resolve("builtin");
        Path modelDir = createModelWithProperties(root, "fox", """
                {
                  "extra_animation": { "#menu": "Default Menu" },
                  "extra_animation_classify": [
                    { "id": "menu", "extra_animation": { "act0": "Default Act", "#return": "Default Return" } }
                  ]
                }
                """);
        Files.createDirectories(modelDir.resolve("lang"));
        Files.writeString(modelDir.resolve("lang").resolve("zh_cn.json"), """
                {
                  "properties.extra_animation.#menu": "本地化菜单",
                  "properties.extra_animation.act0": "本地化动作",
                  "properties.extra_animation.#return": "返回"
                }
                """, StandardCharsets.UTF_8);

        ModelAnimations animations = YsmModelScanner.scan(List.of(root), "zh_cn").get("fox");

        // 根表 '#menu' 入口与子表条目都走同一 lang 表覆盖
        assertEquals("本地化菜单", animations.root().get(0).displayName());
        List<YsmModelScanner.AnimEntry> sub = animations.submenus().get("menu");
        assertEquals("本地化动作", sub.get(0).displayName());
        assertEquals("返回", sub.get(1).displayName());
    }
}
