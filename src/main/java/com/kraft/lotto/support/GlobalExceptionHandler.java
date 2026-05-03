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
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> "%s: %s".formatted(fe.getField(), fe.getDefaultMessage()))
                .orElse(ErrorCode.LOTTO_INVALID_COUNT.getDefaultMessage());
        return ResponseEntity.status(ErrorCode.LOTTO_INVALID_COUNT.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.LOTTO_INVALID_COUNT, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.status(ErrorCode.LOTTO_INVALID_COUNT.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.LOTTO_INVALID_COUNT, ex.getMessage()));
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
}
