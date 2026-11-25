package com.codeit.weatherwear.domain.auth.exception;

import com.codeit.weatherwear.global.exception.CustomException;
import com.codeit.weatherwear.global.exception.ErrorCode;
import java.util.Map;

public class PasswordResetRateLimitExceededException extends CustomException {

  public PasswordResetRateLimitExceededException(String limitType, long retryAfterSeconds) {
    super(ErrorCode.PASSWORD_RESET_TOO_MANY_REQUESTS,
        Map.of("limitType", limitType, "retryAfterSeconds", retryAfterSeconds));
  }
}
