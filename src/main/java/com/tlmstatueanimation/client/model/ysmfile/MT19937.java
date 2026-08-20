package com.tlmstatueanimation.client.model.ysmfile;

/**
 * 移植自 OpenYSM（https://github.com/OpenYSM/OpenYSM，YSM 2.6.5 反编译重构），仅供兼容互操作。
 * Mersenne Twister de 64 bits (MT19937-64)。
 */
public class MT19937 {

    private static final int w = 64, n = 312, m = 156, r = 31;
    private static final long a = 0xB5026F5AA96619E9L;
    private static final int u = 29, s = 17, t = 37;
    private static final long d = 0x5555555555555555L, b = 0x71d67fffeda60000L, c = 0xfff7eee000000000L;
    private static final int l = 43;
    private static final long f = 6364136223846793005L;
    private static final long lower_mask = 0x7FFFFFFFL, upper_mask = 0xFFFFFFFF80000000L;
    public static final int default_seed = 5489;

    private final long[] MT;
    private int index;

    public MT19937() {
        this.MT = new long[n];
        this.setSeed(System.currentTimeMillis());
    }

    public MT19937(long seed) {
        this.MT = new long[n];
        this.setSeed(seed);
    }

    private void setSeed(long seed) {
        this.MT[0] = seed;
        this.index = n;
        for (int i = 1; i < n; i++) {
            this.MT[i] = (f * (this.MT[i - 1] ^ (this.MT[i - 1] >>> (w - 2))) + i);
        }
    }

    private void twist() {
        for (int i = 0; i < n; i++) {
            long x = (this.MT[i] & upper_mask) | (this.MT[(i + 1) % n] & lower_mask);
            long xA = x >>> 1;
            if ((x & 1L) != 0L) {
                xA ^= a;
            }
            this.MT[i] = this.MT[(i + m) % n] ^ xA;
        }
        this.index = 0;
    }

    public long extract_number() {
        if (this.index >= n) {
            this.twist();
        }
        long y = this.MT[this.index++];

        y ^= (y >>> u) & d;
        y ^= (y << s) & b;
        y ^= (y << t) & c;
        y ^= (y >>> l);

        return y;
    }

    public double random() {
        return (this.extract_number() >> 1) / (double) Long.MAX_VALUE;
    }

    public int randint(int x) {
        if (x <= 0) {
            throw new IllegalArgumentException("n debe ser mayor a 0");
        }
        int bits, val;
        do {
            bits = (int) (extract_number() >> (w - r));
            val = bits % x;
        } while (bits - val + (x - 1) < 0);
        return val;
    }

    public float randfloat(float x) {
        if (x <= 0.0f) {
            throw new IllegalArgumentException("x debe ser mayor a 0.0");
        }
        return (float) this.random() * x;
    }

    public double randdouble(double x) {
        if (x <= 0.0d) {
            throw new IllegalArgumentException("x debe ser mayor a 0.0");
        }
        return this.random() * x;
    }

    public int randrange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("min debe ser menor que max");
        }
        return this.randint(max - min) + min;
    }

    public long getSeed() {
        return this.MT[0];
    }
}
