package com.kraft.lotto.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("글로벌 예외 핸들러 테스트")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("읽을 수 없는 HTTP 메시지는 400 Bad Request로 처리한다")
    void handlesHttpMessageNotReadableAsBadRequest() {
        var ex = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));
        var response = handler.handleNotReadable(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("REQUEST_VALIDATION_ERROR");
    }

    @Test
    @DisplayName("타입 불일치 예외는 400 Bad Request로 처리한다")
    void handlesTypeMismatchAsBadRequest() {
        var ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "drwNo", null, new NumberFormatException());
        var response = handler.handleTypeMismatch(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("LOTTO_INVALID_TARGET_ROUND");
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메소드는 405 Method Not Allowed로 처리한다")
    void handlesMethodNotAllowedAs405() {
        var response = handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("GET"));
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("자원을 찾을 수 없는 경우 404 Not Found로 처리한다")
    void handlesNoResourceFoundAs404() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/missing", "");
        var response = handler.handleNoResource(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().error().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("IllegalArgumentException은 일반 요청 검증 오류로 처리한다")
    void handlesIllegalArgumentAsRequestValidationError() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("REQUEST_VALIDATION_ERROR");
    }
}
