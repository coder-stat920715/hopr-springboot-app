package com.hopr.exception;

import java.util.Map;

/** HTTP 423: the corporate profile is locked; the user must submit an appeal (or be reset by support). */
public class CompanyUpdateLockedException extends RuntimeException {

    private final transient Map<String, Object> prefilledAppeal;

    public CompanyUpdateLockedException(String message, Map<String, Object> prefilledAppeal) {
        super(message);
        this.prefilledAppeal = prefilledAppeal;
    }

    public Map<String, Object> getPrefilledAppeal() {
        return prefilledAppeal;
    }
}
