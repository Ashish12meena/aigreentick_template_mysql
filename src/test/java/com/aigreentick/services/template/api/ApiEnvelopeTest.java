package com.aigreentick.services.template.api;

import com.aigreentick.services.template.api.response.common.ApiEnvelope;
import com.aigreentick.services.template.common.error.ErrorCode;
import com.aigreentick.services.template.common.error.FieldErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiEnvelopeTest {

    @Test
    void successCarriesSuccessCodeAndEmptyErrors() {
        ApiEnvelope<String> env = ApiEnvelope.success(HttpStatus.CREATED, "Created", "x");
        assertThat(env.success()).isTrue();
        assertThat(env.status()).isEqualTo(201);
        assertThat(env.code()).isEqualTo("SUCCESS");
        assertThat(env.errors()).isEmpty();
        assertThat(env.meta().path()).isNull();
    }

    @Test
    void wrapperRefusesMismatchedStatus() {
        assertThatThrownBy(() -> ApiEnvelope.success(HttpStatus.BAD_REQUEST, "no", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiEnvelope.error(HttpStatus.OK, "X", "no", List.of(), "/p"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyErrorCodeUsesAnErrorStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.httpStatus().isError()).as(code.name()).isTrue();
        }
    }

    @Test
    void constraintNamesMapToStandardFieldCodes() {
        assertThat(FieldErrorCode.fromConstraint("NotBlank")).isEqualTo(FieldErrorCode.REQUIRED);
        assertThat(FieldErrorCode.fromConstraint("Max")).isEqualTo(FieldErrorCode.OUT_OF_RANGE);
        assertThat(FieldErrorCode.fromConstraint("Size")).isEqualTo(FieldErrorCode.TOO_LONG);
        assertThat(FieldErrorCode.fromConstraint("Pattern")).isEqualTo(FieldErrorCode.INVALID_FORMAT);
        assertThat(FieldErrorCode.fromConstraint("Whatever")).isEqualTo(FieldErrorCode.INVALID_VALUE);
        assertThat(FieldErrorCode.fromCodes(new String[]{"Min.list.size", "Min.int", "Min"}))
                .isEqualTo(FieldErrorCode.OUT_OF_RANGE);
    }
}
