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

package com.questdb.cairo;

import com.questdb.std.FilesFacade;
import com.questdb.std.FilesFacadeImpl;
import com.questdb.std.microtime.MicrosecondClock;
import com.questdb.std.microtime.MicrosecondClockImpl;
import com.questdb.std.str.ImmutableCharSequence;

public class DefaultCairoConfiguration implements CairoConfiguration {

    private final CharSequence root;

    public DefaultCairoConfiguration(CharSequence root) {
        this.root = ImmutableCharSequence.of(root);
    }

    @Override
    public int getFileOperationRetryCount() {
        return 30;
    }

    @Override
    public FilesFacade getFilesFacade() {
        return FilesFacadeImpl.INSTANCE;
    }

    @Override
    public long getIdleCheckInterval() {
        return 100;
    }

    @Override
    public long getInactiveReaderTTL() {
        return -10000;
    }

    @Override
    public long getInactiveWriterTTL() {
        return -10000;
    }

    @Override
    public int getMkDirMode() {
        return 509;
    }

    @Override
    public int getReaderPoolSegments() {
        return 2;
    }

    @Override
    public CharSequence getRoot() {
        return root;
    }

    @Override
    public MicrosecondClock getClock() {
        return MicrosecondClockImpl.INSTANCE;
    }

    @Override
    public long getSpinLockTimeoutUs() {
        return 1000;
    }
}
