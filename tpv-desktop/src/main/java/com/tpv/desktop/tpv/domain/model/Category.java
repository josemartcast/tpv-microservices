package com.tpv.desktop.tpv.domain.model;

public record Category(long id, String name) {
    @Override
    public String toString() {
        return name;
    }
}

