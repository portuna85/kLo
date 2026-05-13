package com.kraft.lotto.support;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        String message;
        if (fieldError == null) {
            message = ErrorCode.REQUEST_VALIDATION_ERROR.getDefaultMessage();
        } else {
            String fieldMsg = Objects.requireNonNullElse(fieldError.getDefaultMessage(), "유효하지 않은 값");
            message = "%s: %s".formatted(fieldError.getField(), fieldMsg);
        }
        ErrorCode code = resolveFieldValidationCode(fieldError == null ? "" : fieldError.getField());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(code, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String propertyPath = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath().toString())
                .orElse("");
        ErrorCode code = resolveConstraintCode(propertyPath);
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(code, ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ErrorCode.REQUEST_VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.REQUEST_VALIDATION_ERROR));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorCode code = resolveConstraintCode(ex.getName());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(code));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.LOTTO_INVALID_NUMBER.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.LOTTO_INVALID_NUMBER, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        String safeQuery = maskSensitiveQuery(req.getQueryString());
        log.error("Unhandled exception at {} {} (query={})",
                req.getMethod(), req.getRequestURI(), safeQuery, ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    static String maskSensitiveQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        return queryString.replaceAll("(?i)(token|secret|key)=([^&]*)", "$1=***");
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

    private static ErrorCode resolveConstraintCode(String propertyPath) {
        if (propertyPath == null) {
            return ErrorCode.REQUEST_VALIDATION_ERROR;
        }
        if (propertyPath.endsWith("count")) {
            return ErrorCode.LOTTO_INVALID_COUNT;
        }
        if (propertyPath.endsWith("targetRound")
                || propertyPath.endsWith("round")
                || propertyPath.endsWith("drwNo")
                || propertyPath.endsWith("from")
                || propertyPath.endsWith("to")) {
            return ErrorCode.LOTTO_INVALID_TARGET_ROUND;
        }
        if (propertyPath.endsWith("page") || propertyPath.endsWith("size")) {
            return ErrorCode.LOTTO_INVALID_PAGE_REQUEST;
        }
        return ErrorCode.REQUEST_VALIDATION_ERROR;
    }
}
