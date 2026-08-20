package com.tlmstatueanimation.client.model.ysmfile;

import com.tlmstatueanimation.client.model.YsmModelScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * .ysm 加密单文件模型端到端测试：真实 fixture（src/test/resources/test-model.ysm，
 * 用户实测所用格式）走 解密 → zstd 解压 → 二进制走读 → 扫描器接入 全链路。
 * 该文件为第三方作者的模型，仅作测试 fixture，禁止外传。
 */
class YsmFileEndToEndTest {

    private static byte[] readFixture() throws IOException {
        try (InputStream in = YsmFileEndToEndTest.class.getResourceAsStream("/test-model.ysm")) {
            assertNotNull(in, "test fixture test-model.ysm missing from test resources");
            return in.readAllBytes();
        }
    }

    @Test
    void decryptsAndWalksRealYsmFile() throws IOException {
        byte[] fileData = readFixture();

        byte[] decrypted = YsmFileDecryptor.decryptYsmFile(fileData);
        assertTrue(decrypted.length > 0, "decrypted payload must not be empty");

        YsmBinaryModelWalker.YsmModelData data = YsmBinaryModelWalker.walk(decrypted);

        assertFalse(data.extraAnimations().isEmpty(), "extraAnimations must not be empty");
        data.extraAnimations().forEach((key, displayName) -> {
            assertFalse(key.isBlank(), "animation key must not be blank");
            assertNotNull(displayName);
        });
    }

    @Test
    void scannerIndexesYsmFileWithSuffixedModelId(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("custom");
        Files.createDirectories(root);
        Files.write(root.resolve("test-model.ysm"), readFixture());

        Map<String, List<YsmModelScanner.AnimEntry>> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        // 主注册：带 .ysm 后缀的相对路径（实测雕像 NBT 中的 modelId 形态）
        List<YsmModelScanner.AnimEntry> anims = result.get("test-model.ysm");
        assertNotNull(anims, "modelId with .ysm suffix must be indexed");
        assertFalse(anims.isEmpty());
        anims.forEach(entry -> assertFalse(entry.key().isBlank()));
        // 兜底：去后缀变体
        assertNotNull(result.get("test-model"), "suffix-stripped fallback variant must be indexed");
    }
}
