package com.tpv.desktop.tpv.services;

import com.tpv.desktop.tpv.domain.model.TableSnapshot;

import java.util.List;

public interface TableService {
    List<TableSnapshot> tables();
}

