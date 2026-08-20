package com.tlmstatueanimation.client.model.ysmfile;

import java.nio.charset.StandardCharsets;

/**
 * 移植自 OpenYSM（https://github.com/OpenYSM/OpenYSM，YSM 2.6.5 反编译重构），仅供兼容互操作。
 * YSMByteBuf 读取侧语义的小端二进制游标：纯 Java（byte[] + 游标），不依赖 netty，可单测。
 */
public final class YsmByteReader {
    private final byte[] data;
    private int offset;

    public YsmByteReader(byte[] data) {
        this.data = data;
        this.offset = 0;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int remaining() {
        return data.length - offset;
    }

    private void require(int n) {
        if (n < 0 || offset + n > data.length) {
            throw new IndexOutOfBoundsException(
                    "YSM binary underflow: need " + n + " bytes at offset " + offset + ", length " + data.length);
        }
    }

    public byte readByte() {
        require(1);
        return data[offset++];
    }

    /** 小端 float */
    public float readFloat() {
        require(4);
        int bits = (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
        offset += 4;
        return Float.intBitsToFloat(bits);
    }

    /** 小端 unsigned int，返回 long */
    public long readDword() {
        require(4);
        long value = (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
        offset += 4;
        return value;
    }

    public int readVarInt() {
        int value = 0;
        int position = 0;
        while (true) {
            byte currentByte = readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 64) throw new IllegalStateException("VarInt too big");
        }
        return value;
    }

    public long readVarLong() {
        long value = 0L;
        int position = 0;
        while (true) {
            byte currentByte = readByte();
            value |= (long) (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 64) throw new IllegalStateException("VarLong too big");
        }
        return value;
    }

    public byte[] readByteArray() {
        int len = readVarInt();
        if (len == 0) return new byte[0];
        require(len);
        byte[] bytes = new byte[len];
        System.arraycopy(data, offset, bytes, 0, len);
        offset += len;
        return bytes;
    }

    /** VarInt 长度 + UTF-8 */
    public String readString() {
        int len = readVarInt();
        if (len == 0) return "";
        require(len);
        String s = new String(data, offset, len, StandardCharsets.UTF_8);
        offset += len;
        return s;
    }

    public void skipBytes(int n) {
        require(n);
        offset += n;
    }
}
