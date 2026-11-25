package com.codeit.weatherwear.domain.auth.service;

import com.codeit.weatherwear.domain.auth.exception.PasswordResetRateLimitExceededException;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 초기화 요청 횟수 제한 (고정 윈도우 방식, Redis INCR + EXPIRE).
 *
 * <ul>
 *   <li>이메일 기준: 한 사람에게 메일 폭탄을 보내거나 반복 요청하는 것을 막는다.</li>
 *   <li>IP 기준: 한 곳에서 여러 이메일로 요청을 뿌리는 것을 막는다. (존재하지 않는 이메일도 센다)</li>
 * </ul>
 *
 * <p>Redis 장애 시에는 제한을 건너뛰고 요청을 허용한다(fail-open). 횟수 제한은 보조 방어 수단이라,
 * Redis 장애 때문에 비밀번호 초기화 자체가 막히지 않게 하려는 선택이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetRateLimiter {

  private static final String EMAIL_KEY_PREFIX = "pwreset:email:";
  private static final String IP_KEY_PREFIX = "pwreset:ip:";

  private final StringRedisTemplate redisTemplate;

  @Value("${resetpassword.rate-limit.email.max-requests:3}")
  private long emailMaxRequests;
  @Value("${resetpassword.rate-limit.email.window-seconds:600}")
  private long emailWindowSeconds;
  @Value("${resetpassword.rate-limit.ip.max-requests:20}")
  private long ipMaxRequests;
  @Value("${resetpassword.rate-limit.ip.window-seconds:600}")
  private long ipWindowSeconds;

  public void check(String email, String clientIp) {
    incrementAndCheck(IP_KEY_PREFIX + clientIp, ipMaxRequests, ipWindowSeconds, "ip");
    incrementAndCheck(EMAIL_KEY_PREFIX + email.toLowerCase(Locale.ROOT), emailMaxRequests,
        emailWindowSeconds, "email");
  }

  private void incrementAndCheck(String key, long maxRequests, long windowSeconds, String type) {
    Long count;
    try {
      count = redisTemplate.opsForValue().increment(key);
      // 윈도우의 첫 요청일 때만 만료 시간을 건다 → windowSeconds 뒤 카운터가 사라진다
      if (count != null && count == 1L) {
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
      }
    } catch (DataAccessException e) {
      log.warn("[Password Reset Rate Limit] Redis unavailable, skip limit. type={}", type, e);
      return;
    }
    if (count != null && count > maxRequests) {
      log.warn("[Password Reset Rate Limit] Exceeded. type={}, count={}, max={}", type, count,
          maxRequests);
      throw new PasswordResetRateLimitExceededException(type, windowSeconds);
    }
  }
}
