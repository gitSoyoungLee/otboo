package com.codeit.weatherwear.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codeit.weatherwear.domain.user.entity.Role;
import com.codeit.weatherwear.domain.user.entity.User;
import com.codeit.weatherwear.domain.user.repository.UserRepository;
import com.codeit.weatherwear.global.config.ContainerInitializer;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * 장애 2: 비밀번호 초기화 이후 사용자가 계정을 잃는 상황을 재현한다.
 *
 * <p>모든 검증은 실제 HTTP 요청(CSRF 토큰 포함, 로그인 API)으로 "사용자 입장"에서 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-security")
@ContextConfiguration(initializers = ContainerInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetFailureTest {

  private static final String VICTIM_EMAIL = "victim@mail.com";
  private static final String ORIGINAL_PASSWORD = "original-password1!";
  private static final String MAIL_FAIL_LOG = "Fail to send reset-password email";

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
      .withPerMethodLifecycle(true);

  @DynamicPropertySource
  static void mailProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> "localhost");
    registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
    registry.add("spring.mail.username", () -> "");
    registry.add("spring.mail.password", () -> "");
    registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
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
    // 요청 횟수 제한 카운터가 테스트 간에 이어지지 않도록 비운다
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    userRepository.save(User.builder()
        .email(VICTIM_EMAIL)
        .password(passwordEncoder.encode(ORIGINAL_PASSWORD))
        .name("victim")
        .role(Role.USER)
        .build());
  }

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("[장애2-1] 메일 서버 장애로 임시 비밀번호 메일이 실패해도, 사용자는 기존 비밀번호로 로그인할 수 있다")
  void mailFailureShouldNotLockOutUser(CapturedOutput output) {
    // given: 메일 서버 다운
    greenMail.stop();

    // when: 사용자가 비밀번호 초기화를 요청
    assertThat(requestPasswordReset(VICTIM_EMAIL).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 비동기 메일 발송이 실패할 때까지 기다린다 (실패 로그가 찍히는 시점)
    await().atMost(Duration.ofSeconds(30)).until(() -> output.getAll().contains(MAIL_FAIL_LOG));

    // then: 메일을 받지 못했으니, 기존 비밀번호로는 여전히 로그인할 수 있어야 한다
    assertThat(signIn(VICTIM_EMAIL, ORIGINAL_PASSWORD).getStatusCode())
        .as("메일 발송 실패 후 기존 비밀번호 로그인")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("[장애2-2] 메일 발송이 실패하면 실패 원인(예외 종류와 메시지)이 로그에 남는다")
  void mailFailureShouldLogCause(CapturedOutput output) {
    // given: 메일 서버 다운
    greenMail.stop();

    // when
    requestPasswordReset(VICTIM_EMAIL);
    await().atMost(Duration.ofSeconds(30)).until(() -> output.getAll().contains(MAIL_FAIL_LOG));

    // then: 운영자가 원인을 알 수 있도록, 실패 로그에 예외 정보가 포함되어야 한다
    // (메일 서버 연결 실패 시 JavaMailSender는 MailSendException을 던진다)
    String failLogAndAfter = output.getAll().substring(output.getAll().indexOf(MAIL_FAIL_LOG));
    assertThat(failLogAndAfter)
        .as("메일 실패 로그 이후 출력에 예외 정보가 있어야 함")
        .contains("MailSendException");
  }

  @Test
  @DisplayName("[장애2-3] 로그인하지 않은 제3자가 남의 이메일로 초기화를 요청해도, 피해자는 기존 비밀번호로 로그인할 수 있다")
  void unauthenticatedResetShouldNotInvalidateVictimPassword() {
    // when: 인증 정보 없이(Authorization 헤더 없음) 피해자의 이메일로 초기화 요청
    ResponseEntity<Void> response = requestPasswordReset(VICTIM_EMAIL);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 메일 발송(피해자 메일함 도착)까지 기다려 흐름을 끝까지 진행시킨다
    await().atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));

    // then: 피해자는 아무것도 하지 않았으니 기존 비밀번호가 그대로 동작해야 한다
    assertThat(signIn(VICTIM_EMAIL, ORIGINAL_PASSWORD).getStatusCode())
        .as("제3자의 초기화 요청 이후 피해자의 기존 비밀번호 로그인")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("[A안 회귀] 메일로 받은 임시 비밀번호로도 로그인할 수 있다")
  void tempPasswordFromMailShouldWork() throws Exception {
    requestPasswordReset(VICTIM_EMAIL);
    await().atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));

    String tempPassword = extractTempPassword(
        GreenMailUtil.getBody(greenMail.getReceivedMessages()[0]));

    assertThat(signIn(VICTIM_EMAIL, tempPassword).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("[A안 회귀] 만료된 임시 비밀번호는 거부되지만, 기존 비밀번호 로그인은 막히지 않는다")
  void expiredTempPasswordShouldBeRejectedButOriginalStillWorks() {
    // given: 이미 만료된 임시 비밀번호가 저장된 상태 (기존 구현에서는 이 상태면 계정 전체가 로그인 불가였다)
    User user = userRepository.findByEmail(VICTIM_EMAIL).orElseThrow();
    user.setTempPassword(passwordEncoder.encode("EXPIRED1"), Instant.now().minusSeconds(1));
    userRepository.save(user);

    assertThat(signIn(VICTIM_EMAIL, "EXPIRED1").getStatusCode())
        .as("만료된 임시 비밀번호").isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(signIn(VICTIM_EMAIL, ORIGINAL_PASSWORD).getStatusCode())
        .as("기존 비밀번호").isEqualTo(HttpStatus.OK);
  }

  // 템플릿의 <div th:text="${tempPassword}" ...>XXXXXXXX</div> 에서 8자리 임시 비밀번호 추출
  private String extractTempPassword(String body) {
    String decoded = body.replace("=\r\n", "").replace("=3D", "=");
    Matcher matcher = Pattern.compile(">\\s*([A-Za-z0-9]{8})\\s*</div>").matcher(decoded);
    assertThat(matcher.find()).as("메일 본문에서 임시 비밀번호를 찾지 못함").isTrue();
    return matcher.group(1);
  }

  // --- HTTP helpers ---

  /** 인증 없이, CSRF 토큰만 받아서 비밀번호 초기화를 요청한다 (공격자도 똑같이 할 수 있는 요청). */
  private ResponseEntity<Void> requestPasswordReset(String email) {
    String xsrf = fetchCsrfToken();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf);
    headers.add("X-XSRF-TOKEN", xsrf);
    return restTemplate.exchange("/api/auth/reset-password", HttpMethod.POST,
        new HttpEntity<>(Map.of("email", email), headers), Void.class);
  }

  private ResponseEntity<String> signIn(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return restTemplate.postForEntity("/api/auth/sign-in",
        new HttpEntity<>(Map.of("email", email, "password", password), headers), String.class);
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
