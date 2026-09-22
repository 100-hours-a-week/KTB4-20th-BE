package com.planit.chat.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ChatTextNormalizer {

    private static final int MAX_CODE_POINTS = 1_000;
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile(
            "[\\p{Zs}\\t\\f\\u000B]+"
    );

    public String normalize(String text) {
        if (text == null) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_MESSAGE_PAYLOAD);
        }
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        normalized = HORIZONTAL_WHITESPACE.matcher(normalized).replaceAll(" ");
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_MESSAGE_PAYLOAD);
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }
        return normalized;
    }
}
