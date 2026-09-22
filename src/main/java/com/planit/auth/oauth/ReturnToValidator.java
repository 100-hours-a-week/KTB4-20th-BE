package com.planit.auth.oauth;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ReturnToValidator {

    private static final String ROOT_PATH = "/";

    private static final Pattern INVITATION_PATH =
            Pattern.compile(
                    "^/invitations/[A-Za-z0-9_-]{43}$"
            );

    public String validate(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return ROOT_PATH;
        }

        if (ROOT_PATH.equals(returnTo)) {
            return returnTo;
        }

        if (INVITATION_PATH.matcher(returnTo).matches()) {
            return returnTo;
        }

        throw new BusinessException(
                ErrorCode.INVALID_REQUEST
        );
    }
}
