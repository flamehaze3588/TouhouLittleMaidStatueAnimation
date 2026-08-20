package com.tlmstatueanimation.client.model.ysmfile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * 移植自 OpenYSM（https://github.com/OpenYSM/OpenYSM，YSM 2.6.5 反编译重构），仅供兼容互操作。
 * YsmCrypt.decryptYsmFile 等价物：文件哈希校验 → 定制 XChaCha20 解密 → MT19937 XOR →
 * 跳过随机 padding → YsmZstd 解压。纯 Java，无 MC/netty 依赖，可单测。
 */
public final class YsmFileDecryptor {

    public static final long SEED_FILE_VERIFICATION = 0x9E5599DB80C67C29L;
    public static final long SEED_RES_VERIFICATION = 0xA62B1A2C43842BC3L;
    public static final long SEED_KEY_DERIVATION = 0xD017CBBA7B5D3581L;

    private YsmFileDecryptor() {
    }

    /**
     * 解密 .ysm 单文件模型，返回解压后的 YSM 二进制模型数据。
     *
     * @throws IOException 文件过短 / 哈希校验失败 / crypto 版本不支持 / zstd 解压失败
     */
    public static byte[] decryptYsmFile(byte[] fileData) throws IOException {
        if (fileData.length < 8 + 24 + 32 + 8) {
            throw new IOException("Invalid YSM file: File too short.");
        }

        int headerLength = 0;
        while (headerLength < fileData.length && fileData[headerLength] != 0x00) {
            headerLength++;
        }

        int tailOffset = fileData.length - 64;
        byte[] key = Arrays.copyOfRange(fileData, tailOffset, tailOffset + 32);
        byte[] iv = Arrays.copyOfRange(fileData, tailOffset + 32, tailOffset + 56);
        long fileHash = ByteBuffer.wrap(fileData, tailOffset + 56, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();

        CityHash ch = new CityHash();
        long calculatedHash = ch.hash64WithSeed(Arrays.copyOfRange(fileData, 0, fileData.length - 8), SEED_FILE_VERIFICATION);
        if (calculatedHash != fileHash) {
            throw new IOException("Corrupted YSM file: File hash mismatch.");
        }

        int ptrBinaryData = headerLength + 1;
        int crypto = ByteBuffer.wrap(fileData, ptrBinaryData, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (crypto != 3) {
            throw new IOException("Invalid YSM file: Crypto version is not 3.");
        }
        ptrBinaryData += 4;

        byte[] encryptedBinaryData = Arrays.copyOfRange(fileData, ptrBinaryData, tailOffset);
        byte[] chachaDecrypted = modifiedChaChaDecrypt(encryptedBinaryData, key, iv, SEED_RES_VERIFICATION);

        byte[] keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);
        byte[] xorredData = mt19937Xor(chachaDecrypted, keyIv, SEED_KEY_DERIVATION);

        // uint16 n = data[0] | (data[1] << 8); n &= 0x3ff —— 随机 padding 长度
        int n = ((xorredData[0] & 0xFF) | ((xorredData[1] & 0xFF) << 8)) & 0x3FF;
        int zstdOffset = 2 + n;
        byte[] bytes = Arrays.copyOfRange(xorredData, zstdOffset, xorredData.length);

        return YsmZstd.decompress(bytes);
    }

    private static byte[] modifiedChaChaDecrypt(byte[] data, byte[] key, byte[] iv, long seed) throws IOException {
        byte[] keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);

        CityHash ch = new CityHash();
        long hash2 = ch.hash64WithSeed(keyIv, seed);

        // ((hash2 & 0x3f) | 0x40) << 6
        int nextRoundSize = (int) (((hash2 & 0x3FL) | 0x40L) << 6);
        int rounds = (int) (10 * Long.remainderUnsigned(hash2, 3) + 10);

        XChaCha20 ctx;
        try {
            ctx = new XChaCha20(key, iv, rounds);
        } catch (Exception e) {
            throw new IOException("Failed to init XChaCha20", e);
        }

        byte[] result = new byte[data.length];
        int blockPointer = 0;

        while (blockPointer < data.length) {
            if (blockPointer + nextRoundSize > data.length) {
                nextRoundSize = data.length - blockPointer;
            }
            byte[] decChunk = ctx.processBytes(data, blockPointer, nextRoundSize);
            System.arraycopy(decChunk, 0, result, blockPointer, nextRoundSize);
            blockPointer += nextRoundSize;

            if (blockPointer < data.length) {
                long resHash = ch.hash64WithSeed(decChunk, seed);
                nextRoundSize = ctx.updateStateYSM(resHash);
            }
        }

        return result;
    }

    private static byte[] mt19937Xor(byte[] data, byte[] currentKeyIv, long seedDerivation) {
        long mtSeed = new CityHash().hash64WithSeed(currentKeyIv, seedDerivation);
        MT19937 mt = new MT19937(mtSeed);
        byte[] result = new byte[data.length];

        int i = 0;
        while (i < data.length) {
            long rnd = mt.extract_number();
            for (int j = 0; j < 8 && i < data.length; ++j) {
                byte keystreamByte = (byte) ((rnd >>> (j * 8)) & 0xFF);
                result[i] = (byte) (data[i] ^ keystreamByte);
                i++;
            }
        }
        return result;
    }
}
