package com.tpv.desktop.core;

public final class AppState {
    private AppState() {}

    private static Long resumeTicketId;

    public static Long getResumeTicketId() {
        return resumeTicketId;
    }

    public static void setResumeTicketId(Long id) {
        resumeTicketId = id;
    }

    public static void clearResumeTicketId() {
        resumeTicketId = null;
    }
}
