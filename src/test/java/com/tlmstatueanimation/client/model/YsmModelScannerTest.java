package com.tlmstatueanimation.client.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YsmModelScanner 测试（D1 磁盘扫描解析）。
 */
class YsmModelScannerTest {

    @TempDir
    Path tempDir;

    private Path createModel(Path root, String relativeDir, String extraAnimationJson) throws IOException {
        Path modelDir = root.resolve(relativeDir);
        Files.createDirectories(modelDir);
        String ysmJson = """
                {
                  "spec": 2,
                  "properties": {
                    "extra_animation": %s
                  }
                }
                """.formatted(extraAnimationJson);
        Files.writeString(modelDir.resolve("ysm.json"), ysmJson, StandardCharsets.UTF_8);
        return modelDir;
    }

    @Test
    void scansModelIdAsRelativePath() throws IOException {
        Path root = tempDir.resolve("built");
        createModel(root, "wine_fox/12_little", """
                { "extra0": "变身", "extra1": "打招呼" }
                """);

        Map<String, List<YsmModelScanner.AnimEntry>> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        assertTrue(result.containsKey("wine_fox/12_little"));
        assertFalse(result.containsKey("wine_fox\\12_little"));
    }

    @Test
    void keepsDeclarationOrderAndDefaultNames() throws IOException {
        Path root = tempDir.resolve("custom");
        createModel(root, "my_model", """
                { "extra2": "鼓掌", "extra0": "变身", "extra7": "哭哭" }
                """);

        List<YsmModelScanner.AnimEntry> anims = YsmModelScanner.scan(List.of(root), "zh_cn").get("my_model");

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

        List<YsmModelScanner.AnimEntry> anims = YsmModelScanner.scan(List.of(root), "zh_cn").get("fox");

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

        List<YsmModelScanner.AnimEntry> anims = YsmModelScanner.scan(List.of(root), "en_us").get("fox");

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

        Map<String, List<YsmModelScanner.AnimEntry>> result = YsmModelScanner.scan(List.of(root), "zh_cn");

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

        Map<String, List<YsmModelScanner.AnimEntry>> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        assertTrue(result.isEmpty());
    }

    @Test
    void nonExistentRootIsSkipped() {
        Map<String, List<YsmModelScanner.AnimEntry>> result = YsmModelScanner.scan(
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

        Map<String, List<YsmModelScanner.AnimEntry>> result = YsmModelScanner.scan(List.of(custom, built), "zh_cn");

        assertEquals(1, result.get("fox").size());
        assertEquals("自定义版", result.get("fox").get(0).displayName());
    }
}
