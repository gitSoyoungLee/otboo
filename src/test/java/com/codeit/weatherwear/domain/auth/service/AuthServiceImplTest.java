package com.codeit.weatherwear.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.weatherwear.domain.auth.dto.ResetPasswordRequest;
import com.codeit.weatherwear.domain.auth.event.TempPasswordIssuedEvent;
import com.codeit.weatherwear.domain.user.entity.User;
import com.codeit.weatherwear.domain.user.exception.UserNotFoundException;
import com.codeit.weatherwear.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @InjectMocks
  private AuthServiceImpl authService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private PasswordResetRateLimiter rateLimiter;
  @Spy
  private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  @Spy
  private SecureRandom secureRandom = new SecureRandom();

  UUID userId;
  User user;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder()
        .id(userId)
        .name("testname")
        .email("test@test.mail")
        .password(passwordEncoder.encode("testpw"))
        .build();
  }

  @Test
  void 비밀번호_초기화_성공() {
    // given
    ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest("test@test.mail");
    when(userRepository.findByEmail("test@test.mail")).thenReturn(Optional.of(user));

    // when
    authService.resetPassword(resetPasswordRequest, "127.0.0.1");

    // then: 메일을 직접 보내지 않고, 커밋 후 발송용 이벤트를 발행한다
    ArgumentCaptor<TempPasswordIssuedEvent> captor =
        ArgumentCaptor.forClass(TempPasswordIssuedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    TempPasswordIssuedEvent event = captor.getValue();
    assertEquals("test@test.mail", event.email());
    // 이벤트에 담긴 임시 비밀번호가 DB에 저장된 임시 비밀번호와 일치해야 메일로 받은 비밀번호로 로그인할 수 있다
    assertTrue(passwordEncoder.matches(event.tempPassword(), user.getTempPassword()));
    // 기존 비밀번호는 덮어쓰지 않는다 (A안)
    assertTrue(passwordEncoder.matches("testpw", user.getPassword()));
    verify(rateLimiter).check("test@test.mail", "127.0.0.1");
    assertNotNull(user.getTempPasswordExpirationTime());
  }

  @Test
  void 비밀번호_초기화_실패() {
    // given
    ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest("test@test.mail");
    when(userRepository.findByEmail("test@test.mail")).thenReturn(Optional.empty());

    // when & then
    assertThrows(UserNotFoundException.class,
        () -> authService.resetPassword(resetPasswordRequest, "127.0.0.1"));
  }
}