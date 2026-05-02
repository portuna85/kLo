package com.kraft.lotto.support;

public record ApiError(String code, String message) {

    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.name(), errorCode.getDefaultMessage());
    }

    public static ApiError of(ErrorCode errorCode, String message) {
        String resolved = (message == null || message.isBlank()) ? errorCode.getDefaultMessage() : message;
        return new ApiError(errorCode.name(), resolved);
    }
}
