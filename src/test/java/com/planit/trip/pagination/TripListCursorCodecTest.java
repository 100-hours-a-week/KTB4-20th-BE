package com.planit.trip.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.trip.pagination.TripListCursorCodec.Cursor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripListCursorCodecTest {

    private final TripListCursorCodec codec =
            new TripListCursorCodec();

    @Test
    void restoresEncodedCursor() {
        Cursor cursor = new Cursor(
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 26)
        );

        String encoded = codec.encode(cursor);

        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("invalid-cursor"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
