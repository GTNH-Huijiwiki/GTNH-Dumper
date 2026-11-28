package com.iouter.gtnhdumper.common.utils;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;

import com.google.gson.Gson;

/**
 * 附属可能在配方 metadata 里放运行时对象：互相引用的会无限递归，共享引用很多的会指数级展开。
 * Gson 先建内存树时这两种都可能在栈溢出之前耗尽堆内存，所以先用限长的流式写入试一遍。
 */
public final class BoundedJson {

    /** 单个值的上限，远大于正常 metadata，也小于维基单页的 2 MB。 */
    private static final int MAX_CHARS = 1 << 20;

    private BoundedJson() {}

    /** 能在上限内写完就原样返回；否则返回只记类名的占位对象。 */
    public static Object exportable(Gson gson, Object value) {
        if (value == null || fits(gson, value, MAX_CHARS)) return value;
        return Collections.singletonMap(
            "unserializable",
            value.getClass()
                .getName());
    }

    static boolean fits(Gson gson, Object value, int maxChars) {
        if (value instanceof Number || value instanceof Boolean || value instanceof String) return true;
        try {
            gson.toJson(value, new LimitedWriter(maxChars));
            return true;
        } catch (RuntimeException | StackOverflowError error) {
            return false;
        }
    }

    private static final class LimitedWriter extends Writer {

        private int remaining;

        LimitedWriter(int limit) {
            remaining = limit;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            remaining -= length;
            if (remaining < 0) throw new IOException("JSON value exceeds the size limit");
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
