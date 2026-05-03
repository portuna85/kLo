package com.kraft.lotto.support;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("BusinessException: {} - {}", code.name(), ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(ApiError.of(code, ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        var fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = (fieldError == null)
                ? ErrorCode.REQUEST_VALIDATION_ERROR.getDefaultMessage()
                : "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
        ErrorCode code = resolveFieldValidationCode(fieldError == null ? "" : fieldError.getField());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(code, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        ErrorCode code = resolveConstraintCode(ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(code, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.LOTTO_INVALID_NUMBER.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.LOTTO_INVALID_NUMBER, ex.getMessage()));
    }

    @ExceptionHandler({AuthenticationException.class, AuthenticationCredentialsNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(Exception ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED_ADMIN.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED_ADMIN));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED_ADMIN.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.UNAUTHORIZED_ADMIN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {} {} (query={})",
                req.getMethod(), req.getRequestURI(), req.getQueryString(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private static ErrorCode resolveFieldValidationCode(String field) {
        if ("count".equals(field)) {
            return ErrorCode.LOTTO_INVALID_COUNT;
        }
        if ("targetRound".equals(field)) {
            return ErrorCode.LOTTO_INVALID_TARGET_ROUND;
        }
        return ErrorCode.REQUEST_VALIDATION_ERROR;
    }

    private static ErrorCode resolveConstraintCode(String message) {
        if (message == null) {
            return ErrorCode.REQUEST_VALIDATION_ERROR;
        }
        if (message.contains("count")) {
            return ErrorCode.LOTTO_INVALID_COUNT;
        }
        if (message.contains("targetRound")) {
            return ErrorCode.LOTTO_INVALID_TARGET_ROUND;
        }
        if (message.contains("page") || message.contains("size")) {
            return ErrorCode.LOTTO_INVALID_PAGE_REQUEST;
        }
        return ErrorCode.REQUEST_VALIDATION_ERROR;
    }
}
