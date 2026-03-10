package com.hwpparser.sdk;

public class HwpParseError extends RuntimeException {
    public HwpParseError(String message) {
        super(message);
    }

    public HwpParseError(String message, Throwable cause) {
        super(message, cause);
    }
}
