/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2017 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.std;

import com.questdb.cairo.CairoException;
import com.questdb.std.str.LPSZ;
import com.questdb.std.str.Path;

public class FilesFacadeImpl implements FilesFacade {

    public static final FilesFacade INSTANCE = new FilesFacadeImpl();

    @Override
    public long append(long fd, long buf, int len) {
        return Files.append(fd, buf, len);
    }

    @Override
    public boolean close(long fd) {
        return Files.close(fd) == 0;
    }

    @Override
    public int errno() {
        return Os.errno();
    }

    @Override
    public boolean exists(LPSZ path) {
        return Files.exists(path);
    }

    @Override
    public void findClose(long findPtr) {
        Files.findClose(findPtr);
    }

    @Override
    public long findFirst(LPSZ path) {
        long ptr = Files.findFirst(path);
        if (ptr == -1) {
            throw CairoException.instance(Os.errno()).put("findFirst failed on ").put(path);
        }
        return ptr;
    }

    @Override
    public long findName(long findPtr) {
        return Files.findName(findPtr);
    }

    @Override
    public int findNext(long findPtr) {
        int r = Files.findNext(findPtr);
        if (r == -1) {
            throw CairoException.instance(Os.errno()).put("findNext failed");
        }
        return r;
    }

    @Override
    public int findType(long findPtr) {
        return Files.findType(findPtr);
    }

    @Override
    public long getOpenFileCount() {
        return Files.getOpenFileCount();
    }

    @Override
    public long getPageSize() {
        return Files.PAGE_SIZE;
    }

    public void iterateDir(LPSZ path, FindVisitor func) {
        long p = findFirst(path);
        if (p > 0) {
            try {
                do {
                    func.onFind(findName(p), findType(p));
                } while (findNext(p) > 0);
            } finally {
                findClose(p);
            }
        }
    }

    @Override
    public long length(long fd) {
        return Files.length(fd);
    }

    @Override
    public long length(LPSZ name) {
        return Files.length(name);
    }

    @Override
    public int mkdirs(LPSZ path, int mode) {
        return Files.mkdirs(path, mode);
    }

    @Override
    public long mmap(long fd, long len, long offset, int mode) {
        return Files.mmap(fd, len, offset, mode);
    }

    @Override
    public void munmap(long address, long size) {
        Files.munmap(address, size);
    }

    @Override
    public long openAppend(LPSZ name) {
        return Files.openAppend(name);
    }

    @Override
    public long openRO(LPSZ name) {
        return Files.openRO(name);
    }

    @Override
    public long openRW(LPSZ name) {
        return Files.openRW(name);
    }

    @Override
    public long read(long fd, long buf, int len, long offset) {
        return Files.read(fd, buf, len, offset);
    }

    @Override
    public boolean remove(LPSZ name) {
        return Files.remove(name);
    }

    @Override
    public boolean rename(LPSZ from, LPSZ to) {
        return Files.rename(from, to);
    }

    @Override
    public boolean rmdir(Path name) {
        return Files.rmdir(name);
    }

    @Override
    public boolean truncate(long fd, long size) {
        return Files.truncate(fd, size);
    }

    @Override
    public long write(long fd, long address, long len, long offset) {
        return Files.write(fd, address, len, offset);
    }

    @Override
    public boolean exists(long fd) {
        return Files.exists(fd);
    }

    @Override
    public boolean supportsTruncateMappedFiles() {
        return Os.type != Os.WINDOWS;
    }

    @Override
    public int lock(long fd) {
        return Files.lock(fd);
    }
}
