package com.planit.global.error;

import com.planit.global.response.ValidationErrorReason;

import java.util.Set;

final class ValidationErrorReasonMapper {

    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of(
            "NotNull", "NotBlank", "NotEmpty"
    );
    private static final Set<String> FORMAT_CONSTRAINTS = Set.of(
            "Pattern", "Email", "URL"
    );
    private static final Set<String> LENGTH_CONSTRAINTS = Set.of(
            "Size", "Length"
    );
    private static final Set<String> RANGE_CONSTRAINTS = Set.of(
            "Min", "Max", "DecimalMin", "DecimalMax",
            "Positive", "PositiveOrZero", "Negative", "NegativeOrZero",
            "Past", "PastOrPresent", "Future", "FutureOrPresent"
    );

    private ValidationErrorReasonMapper() {
    }

    static ValidationErrorReason from(String constraintName) {
        if (REQUIRED_CONSTRAINTS.contains(constraintName)) {
            return ValidationErrorReason.REQUIRED;
        }
        if (FORMAT_CONSTRAINTS.contains(constraintName)) {
            return ValidationErrorReason.INVALID_FORMAT;
        }
        if (LENGTH_CONSTRAINTS.contains(constraintName)) {
            return ValidationErrorReason.INVALID_LENGTH;
        }
        if (RANGE_CONSTRAINTS.contains(constraintName)) {
            return ValidationErrorReason.OUT_OF_RANGE;
        }
        return ValidationErrorReason.INVALID_VALUE;
    }
}
