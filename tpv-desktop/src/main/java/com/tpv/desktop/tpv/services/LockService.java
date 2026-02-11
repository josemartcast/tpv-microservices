package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.TableLock;

public interface LockService {
    TableLock lock(int tableId);
    void unlock(int tableId);
    TableLock heartbeat(int tableId);
    TableLock activeLock(int tableId);
}

