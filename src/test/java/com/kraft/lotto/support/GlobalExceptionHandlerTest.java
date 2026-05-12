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

    @DisplayName("tests for GlobalExceptionHandlerTest")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesHttpMessageNotReadableAsBadRequest() {
        var ex = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));
        var response = handler.handleNotReadable(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("REQUEST_VALIDATION_ERROR");
    }

    @Test
    void handlesTypeMismatchAsBadRequest() {
        var ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "drwNo", null, new NumberFormatException());
        var response = handler.handleTypeMismatch(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("LOTTO_INVALID_TARGET_ROUND");
    }

    @Test
    void handlesMethodNotAllowedAs405() {
        var response = handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("GET"));
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void handlesNoResourceFoundAs404() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/missing", "");
        var response = handler.handleNoResource(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().error().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }
}
