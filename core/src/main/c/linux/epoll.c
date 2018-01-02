/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2018 Appsicle
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

#include "epoll.h"
#include <sys/epoll.h>
#include <stddef.h>


JNIEXPORT jlong JNICALL Java_com_questdb_net_Epoll_epollCreate
        (JNIEnv *e, jclass cl) {
    return epoll_create1(0);
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_epollCtl
        (JNIEnv *e, jclass cl, jlong epfd, jint op, jlong fd, jlong event) {
    return epoll_ctl((int) epfd, op, (int) fd, (struct epoll_event *) event);
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_epollWait
        (JNIEnv *e, jclass cl, jlong epfd, jlong eventPtr, jint eventCount, jint timeout) {
    return epoll_wait((int) epfd, (struct epoll_event *) eventPtr, eventCount, timeout);
}


JNIEXPORT jshort JNICALL Java_com_questdb_net_Epoll_getDataOffset
        (JNIEnv *e, jclass cl) {
    return offsetof(struct epoll_event, data);
}

JNIEXPORT jshort JNICALL Java_com_questdb_net_Epoll_getEventsOffset
        (JNIEnv *e, jclass cl) {
    return offsetof(struct epoll_event, events);
}

JNIEXPORT jshort JNICALL Java_com_questdb_net_Epoll_getEventSize
        (JNIEnv *e, jclass cl) {
    return sizeof(struct epoll_event);
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_getEPOLLIN
        (JNIEnv *e, jclass cl) {
    return EPOLLIN;
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_getEPOLLET
        (JNIEnv *e, jclass cl) {
    return EPOLLET;
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_getEPOLLOUT
        (JNIEnv *e, jclass cl) {
    return EPOLLOUT;
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_getEPOLLONESHOT
        (JNIEnv *e, jclass cl) {
    return EPOLLONESHOT;
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_getCtlAdd
        (JNIEnv *e, jclass cl) {
    return EPOLL_CTL_ADD;
}

JNIEXPORT jint JNICALL Java_com_questdb_net_Epoll_getCtlMod
        (JNIEnv *e, jclass cl) {
    return EPOLL_CTL_MOD;
}
