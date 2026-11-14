package com.hopr.exception;

/** Raised when a device that is already bound to another Hopr account is used for a new mobile number. */
public class DeviceAccountLimitException extends RuntimeException {
    public DeviceAccountLimitException() {
        super("Device account limit reached");
    }
}
