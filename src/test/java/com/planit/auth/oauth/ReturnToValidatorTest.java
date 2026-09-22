package com.planit.auth.oauth;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnToValidatorTest {

    private final ReturnToValidator validator =
            new ReturnToValidator();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void usesRootWhenReturnToIsEmpty(String returnTo) {
        assertThat(validator.validate(returnTo))
                .isEqualTo("/");
    }

    @Test
    void allowsRootPath() {
        assertThat(validator.validate("/"))
                .isEqualTo("/");
    }

    @Test
    void allowsInvitationPath() {
        String invitationToken = "a".repeat(43);
        String returnTo =
                "/invitations/" + invitationToken;

        assertThat(validator.validate(returnTo))
                .isEqualTo(returnTo);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example",
            "//evil.example",
            "\\evil.example",
            "/other",
            "/invitations/short-token",
            "/invitations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa?x=1"
    })
    void rejectsNotAllowedReturnTo(String returnTo) {
        assertThatThrownBy(
                () -> validator.validate(returnTo)
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(ErrorCode.INVALID_REQUEST)
                );
    }
}
