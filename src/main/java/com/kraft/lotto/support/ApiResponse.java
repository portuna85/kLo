package com.kraft.lotto.support;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(ApiError.of(errorCode));
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return failure(ApiError.of(errorCode, message));
    }
}
