package com.codeit.weatherwear.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codeit.weatherwear.domain.user.entity.Role;
import com.codeit.weatherwear.domain.user.entity.User;
import com.codeit.weatherwear.domain.user.repository.UserRepository;
import com.codeit.weatherwear.global.config.ContainerInitializer;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 비밀번호 초기화 요청 횟수 제한(C안)을 검증한다.
 *
 * <p>테스트 안에서 제한 값을 직접 지정해, 설정 파일 값이 바뀌어도 테스트 의도가 흔들리지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-security")
@ContextConfiguration(initializers = ContainerInitializer.class)
class PasswordResetRateLimitTest {

  private static final int EMAIL_LIMIT = 3;
  private static final int IP_LIMIT = 5;
  private static final String EMAIL = "rate-target@mail.com";

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
      .withPerMethodLifecycle(true);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> "localhost");
    registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
    registry.add("spring.mail.username", () -> "");
    registry.add("spring.mail.password", () -> "");
    registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");

    registry.add("resetpassword.rate-limit.email.max-requests", () -> EMAIL_LIMIT);
    registry.add("resetpassword.rate-limit.email.window-seconds", () -> 600);
    registry.add("resetpassword.rate-limit.ip.max-requests", () -> IP_LIMIT);
    registry.add("resetpassword.rate-limit.ip.window-seconds", () -> 600);
  }

  @Autowired
  private TestRestTemplate restTemplate;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    // 테스트 간 카운터가 이어지지 않도록 Redis를 비운다
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    userRepository.save(User.builder()
        .email(EMAIL)
        .password(passwordEncoder.encode("original-password1!"))
        .name("rate-target")
        .role(Role.USER)
        .build());
  }

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("[제한-1] 같은 이메일로 제한 횟수를 넘겨 요청하면 429를 받고, 메일은 제한 횟수만큼만 발송된다")
  void sameEmailOverLimitShouldBeRejected() {
    for (int i = 1; i <= EMAIL_LIMIT; i++) {
      assertThat(requestPasswordReset(EMAIL).getStatusCode())
          .as("%d번째 요청", i).isEqualTo(HttpStatus.NO_CONTENT);
    }

    assertThat(requestPasswordReset(EMAIL).getStatusCode())
        .as("제한(%d회) 초과 요청", EMAIL_LIMIT)
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

    await().atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(EMAIL_LIMIT));
  }

  @Test
  @DisplayName("[제한-2] 같은 IP에서 서로 다른 이메일로 제한 횟수를 넘겨 요청하면 429를 받는다")
  void sameIpOverLimitShouldBeRejected() {
    // 이메일당 제한에 걸리지 않도록 매번 다른 이메일을 쓴다 (존재하지 않는 이메일이어도 횟수는 센다)
    for (int i = 1; i <= IP_LIMIT; i++) {
      assertThat(requestPasswordReset("spray-" + i + "@mail.com").getStatusCode())
          .as("%d번째 요청", i).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    assertThat(requestPasswordReset("spray-last@mail.com").getStatusCode())
        .as("IP 제한(%d회) 초과 요청", IP_LIMIT)
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  private ResponseEntity<String> requestPasswordReset(String email) {
    String xsrf = fetchCsrfToken();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf);
    headers.add("X-XSRF-TOKEN", xsrf);
    return restTemplate.exchange("/api/auth/reset-password", HttpMethod.POST,
        new HttpEntity<>(Map.of("email", email), headers), String.class);
  }

  private String fetchCsrfToken() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/auth/csrf-token", String.class);
    List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    assertThat(cookies).as("CSRF 쿠키").isNotNull();
    return cookies.stream()
        .filter(c -> c.startsWith("XSRF-TOKEN="))
        .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
        .findFirst()
        .orElseThrow();
  }
}
