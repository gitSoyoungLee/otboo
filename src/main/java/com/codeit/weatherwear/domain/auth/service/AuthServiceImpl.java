package com.codeit.weatherwear.domain.auth.service;

import com.codeit.weatherwear.domain.auth.dto.ResetPasswordRequest;
import com.codeit.weatherwear.domain.auth.event.TempPasswordIssuedEvent;
import com.codeit.weatherwear.domain.user.entity.User;
import com.codeit.weatherwear.domain.user.exception.UserNotFoundException;
import com.codeit.weatherwear.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher eventPublisher;
  private final PasswordResetRateLimiter rateLimiter;
  private final SecureRandom secureRandom = new SecureRandom();
  private final static String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz012345678";

  @Value("${resetpassword.validity-seconds}")
  private long PASSWORD_VALIDITY_SECONDS;

  // 비밀번호 초기화
  @Transactional
  @Override
  public void resetPassword(ResetPasswordRequest resetPasswordRequest, String clientIp) {
    String email = resetPasswordRequest.email();

    // 사용자 조회보다 먼저 검사한다: 존재하지 않는 이메일로 요청을 뿌리는 것도 횟수에 포함
    rateLimiter.check(email, clientIp);

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UserNotFoundException());

    // 임시 비밀번호 발급
    String tempPassword = generateTempPassword();

    // DB 업데이트
    String encodedTempPassword = passwordEncoder.encode(tempPassword);
    user.setTempPassword(encodedTempPassword, Instant.now().plusSeconds(PASSWORD_VALIDITY_SECONDS));

    // 메일은 여기서 보내지 않고 이벤트만 발행한다.
    // 실제 발송은 TempPasswordMailListener가 커밋 이후(AFTER_COMMIT)에만 수행한다.
    eventPublisher.publishEvent(new TempPasswordIssuedEvent(email, tempPassword));
  }

  // 임시 비밀번호 생성
  // 8자리, 영문, 숫자
  private String generateTempPassword() {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      // 무작위 인덱스 반환
      int index = secureRandom.nextInt(CHARS.length());
      stringBuilder.append(CHARS.charAt(index));
    }
    return stringBuilder.toString();
  }


}
