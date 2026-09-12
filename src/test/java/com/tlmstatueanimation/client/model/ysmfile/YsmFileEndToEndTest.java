package com.tlmstatueanimation.client.model.ysmfile;

import com.tlmstatueanimation.client.model.ModelAnimations;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * .ysm 加密单文件模型端到端测试：真实 fixture（src/test/resources/test-model.ysm，
 * 用户实测所用格式）走 解密 → zstd 解压 → 二进制走读 → 扫描器接入 全链路。
 * 该文件为第三方作者的付费模型，仅作本机测试 fixture，不入库、禁止外传；
 * 因此 fixture 缺失时本测试类整体跳过（assumeTrue），不影响 CI/公开仓库构建。
 * <p>
 * fixture 的 extraAnimations 根表为 [huhupeizhi, 找不到狐找不到狐, #H_d_c, 招手]，
 * 其中 "#H_d_c" 为 classify 子菜单入口。
 */
class YsmFileEndToEndTest {

    private static byte[] readFixture() throws IOException {
        try (InputStream in = YsmFileEndToEndTest.class.getResourceAsStream("/test-model.ysm")) {
            assumeTrue(in != null, "local-only paid model fixture test-model.ysm absent; skipping");
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
        // classify 子菜单段：walker 必须保留；"H_d_c" 子表内容可读
        assertFalse(data.extraAnimationClassify().isEmpty(), "extraAnimationClassify must not be empty");
        Map<String, String> hdc = data.extraAnimationClassify().get("H_d_c");
        assertNotNull(hdc, "classify submenu \"H_d_c\" must exist");
        assertFalse(hdc.isEmpty(), "classify submenu \"H_d_c\" must have entries");
        hdc.forEach((key, displayName) -> {
            assertFalse(key.isBlank(), "submenu entry key must not be blank");
            assertNotNull(displayName);
        });
    }

    @Test
    void scannerIndexesYsmFileWithSuffixedModelId(@org.junit.jupiter.api.io.TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("custom");
        Files.createDirectories(root);
        Files.write(root.resolve("test-model.ysm"), readFixture());

        Map<String, ModelAnimations> result = YsmModelScanner.scan(List.of(root), "zh_cn");

        // 主注册：带 .ysm 后缀的相对路径（实测雕像 NBT 中的 modelId 形态）
        ModelAnimations animations = result.get("test-model.ysm");
        assertNotNull(animations, "modelId with .ysm suffix must be indexed");
        assertFalse(animations.root().isEmpty());
        animations.root().forEach(entry -> assertFalse(entry.key().isBlank()));
        // 兜底：去后缀变体
        assertNotNull(result.get("test-model"), "suffix-stripped fallback variant must be indexed");

        // 根表含 "#H_d_c" 子菜单入口；子菜单表非空且内容可读
        assertTrue(animations.root().stream().anyMatch(e -> e.key().equals("#H_d_c")),
                "root table must contain \"#H_d_c\" submenu entry");
        List<YsmModelScanner.AnimEntry> hdc = animations.submenus().get("H_d_c");
        assertNotNull(hdc, "submenu \"H_d_c\" must be indexed");
        assertFalse(hdc.isEmpty(), "submenu \"H_d_c\" must have readable entries");
        hdc.forEach(entry -> {
            assertFalse(entry.key().isBlank());
            assertNotNull(entry.displayName());
        });
    }
}
