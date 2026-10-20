package com.planit.trip.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

@Component
public class TripListCursorCodec {

    private static final String DELIMITER = "|";

    public String encode(Cursor cursor) {
        String value = String.join(
                DELIMITER,
                cursor.referenceDate().toString(),
                Integer.toString(cursor.sectionOrder()),
                cursor.sortDate().toString(),
                Long.toString(cursor.tripId())
        );

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String encodedCursor) {
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encodedCursor),
                    StandardCharsets.UTF_8
            );
            String[] parts = value.split("\\|", -1);

            if (parts.length != 4) {
                throw new IllegalArgumentException("잘못된 cursor 형식");
            }

            return new Cursor(
                    LocalDate.parse(parts[0]),
                    Integer.parseInt(parts[1]),
                    LocalDate.parse(parts[2]),
                    Long.parseLong(parts[3])
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_CURSOR,
                    exception
            );
        }
    }

    public record Cursor(
            LocalDate referenceDate,
            int sectionOrder,
            LocalDate sortDate,
            Long tripId
    ) {
    }
}
