package com.questdb.cairo;

import com.questdb.misc.Numbers;
import com.questdb.std.BinarySequence;

public final class NullColumn implements ReadOnlyColumn {

    public static final NullColumn INSTANCE = new NullColumn();

    @Override
    public void close() {
    }

    @Override
    public BinarySequence getBin(long offset) {
        return null;
    }

    @Override
    public long getBinLen(long offset) {
        return -1;
    }

    @Override
    public boolean getBool(long offset) {
        return false;
    }

    @Override
    public byte getByte(long offset) {
        return 0;
    }

    @Override
    public double getDouble(long offset) {
        return Double.NaN;
    }

    @Override
    public float getFloat(long offset) {
        return Float.NaN;
    }

    @Override
    public int getInt(long offset) {
        return Numbers.INT_NaN;
    }

    @Override
    public long getLong(long offset) {
        return Numbers.LONG_NaN;
    }

    @Override
    public short getShort(long offset) {
        return 0;
    }

    @Override
    public CharSequence getStr(long offset) {
        return null;
    }

    @Override
    public CharSequence getStr2(long offset) {
        return null;
    }

    @Override
    public int getStrLen(long offset) {
        return -1;
    }

    @Override
    public void trackFileSize() {
    }

    @Override
    public long getFd() {
        return -1;
    }
}
