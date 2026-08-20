package com.tlmstatueanimation.client.model.ysmfile;

import io.airlift.compress.zstd.ZstdInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 移植自 OpenYSM（https://github.com/OpenYSM/OpenYSM，YSM 2.6.5 反编译重构），仅供兼容互操作。
 * YSM 混淆 zstd：wash() 把 YSM 变体帧头还原为标准 zstd 帧，随后用 airlift zstd 解压。
 */
public final class YsmZstd {

    private YsmZstd() {
    }

    public static byte[] decompress(byte[] rawData) throws IOException {
        byte[] data = wash(rawData);
        try (ZstdInputStream in = new ZstdInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }

    static byte[] wash(byte[] data) {
        if (data == null || data.length < 5) {
            throw new IllegalArgumentException("Invalid data length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int magic = buffer.getInt(0);
        if (magic != 0xFD2FB528) {
            throw new IllegalArgumentException("Not a standard ZSTD Magic Number. May be skippable frame or unknown.");
        }

        byte fhd = data[4];
        data[4] = (byte) (fhd & 0xFB);

        int frameHeaderSize = calculateFrameHeaderSize(fhd);
        int offset = 4 + frameHeaderSize;

        while (offset + 3 <= data.length) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;
            int lastBlock = (b0 >> 7) & 1;
            int blockTypeYSM = (b0 >> 5) & 3;

            int rawSize = ((b0 & 0x1F) << 16) | b1 | (b2 << 8);
            int cSize = rawSize ^ 0xD4E9;
            int blockTypeStd = switch (blockTypeYSM) {
                case 0 -> 2;
                case 1 -> 1;
                case 2 -> 3;
                case 3 -> 0;
                default -> throw new IllegalStateException("Unknown block type");
            };

            int stdHeader = lastBlock | (blockTypeStd << 1) | (cSize << 3);

            data[offset] = (byte) (stdHeader & 0xFF);
            data[offset + 1] = (byte) ((stdHeader >> 8) & 0xFF);
            data[offset + 2] = (byte) ((stdHeader >> 16) & 0xFF);

            int blockDataSize = (blockTypeStd == 1) ? 1 : cSize;
            offset += 3 + blockDataSize;

            if (lastBlock == 1) {
                break;
            }
        }
        return data;
    }

    private static int calculateFrameHeaderSize(byte fhd) {
        int size = 1;
        boolean singleSegment = ((fhd >> 5) & 1) == 1;

        int dictIdSize = 0;
        int dictIdBits = fhd & 3;
        if (dictIdBits == 1) dictIdSize = 1;
        else if (dictIdBits == 2) dictIdSize = 2;
        else if (dictIdBits == 3) dictIdSize = 4;

        int fcsSize = 0;
        int fcsBits = (fhd >> 6) & 3;
        if (fcsBits == 0) fcsSize = singleSegment ? 1 : 0;
        else if (fcsBits == 1) fcsSize = 2;
        else if (fcsBits == 2) fcsSize = 4;
        else if (fcsBits == 3) fcsSize = 8;

        int windowDescSize = singleSegment ? 0 : 1;

        return size + windowDescSize + dictIdSize + fcsSize;
    }
}
