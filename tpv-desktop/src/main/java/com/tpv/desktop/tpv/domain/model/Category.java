package com.tpv.desktop.tpv.domain.model;

public record Category(long id, String name, String printDestination) {
    public Category(long id, String name) {
        this(id, name, "COCINA");
    }

    @Override
    public String toString() {
        return name;
    }
}

