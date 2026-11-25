package com.codeit.weatherwear.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.codeit.weatherwear.domain.auth.dto.ResetPasswordRequest;
import com.codeit.weatherwear.domain.auth.service.AuthService;
import com.codeit.weatherwear.domain.auth.service.EmailService;
import com.codeit.weatherwear.domain.user.entity.Role;
import com.codeit.weatherwear.domain.user.entity.User;
import com.codeit.weatherwear.domain.user.repository.UserRepository;
import com.codeit.weatherwear.global.config.ContainerInitializer;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장애 1: "DB는 롤백됐는데 임시 비밀번호 메일은 이미 나간" 상황을 막는지 검증한다.
 *
 * <p>실제 메일 대신 GreenMail(가짜 SMTP 서버)로 수신 여부를 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test-security")
@ContextConfiguration(initializers = ContainerInitializer.class)
class PasswordResetMailConsistencyTest {

  private static final String EMAIL = "reset-target@mail.com";
  private static final String ORIGINAL_PASSWORD = "original-password1!";

  // 메일이 "오지 않았음"을 확인할 때 지켜볼 시간.
  // 현재 구현에서는 메일이 수십 ms 안에 도착하므로 이 시간 안에 반드시 드러난다.
  private static final Duration NO_MAIL_OBSERVATION_WINDOW = Duration.ofSeconds(3);

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
  private AuthService authService;
  @Autowired
  private EmailService emailService;
  @Autowired
  private UserRepository userRepository;
  @Autowired
  private PasswordEncoder passwordEncoder;
  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() throws Exception {
    warmUpMailSending();
    userRepository.save(User.builder()
        .email(EMAIL)
        .password(passwordEncoder.encode(ORIGINAL_PASSWORD))
        .name("reset-target")
        .role(Role.USER)
        .build());
  }

  @AfterEach
  void tearDown() {
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("커밋이 실패해 롤백되면 임시 비밀번호 메일이 발송되지 않는다")
  void rollbackShouldNotSendMail() {
    // when: 메일 발송 호출 이후, 커밋 직전에 DB 장애가 나서 롤백되는 상황
    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void beforeCommit(boolean readOnly) {
          throw new IllegalStateException("커밋 시점 DB 장애 시뮬레이션");
        }
      });
      authService.resetPassword(new ResetPasswordRequest(EMAIL), "consistency-test");
    })).isInstanceOf(IllegalStateException.class);

    // then 1: DB는 롤백되어 기존 비밀번호가 그대로다
    User user = userRepository.findByEmail(EMAIL).orElseThrow();
    assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, user.getPassword())).isTrue();
    assertThat(user.getTempPassword()).isNull();
    assertThat(user.getTempPasswordExpirationTime()).isNull();

    // then 2: 메일은 한 통도 오지 않는다 (관찰 시간 동안 계속 0통이어야 함)
    await().during(NO_MAIL_OBSERVATION_WINDOW)
        .atMost(NO_MAIL_OBSERVATION_WINDOW.plusSeconds(2))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).isEmpty());
  }

  @Test
  @DisplayName("정상 커밋되면 메일이 발송되고, 메일 속 임시 비밀번호로 DB 비밀번호가 일치한다")
  void commitShouldSendMailWithUsablePassword() throws Exception {
    // when
    authService.resetPassword(new ResetPasswordRequest(EMAIL), "consistency-test");

    // then: 메일이 도착할 때까지 기다린다
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));

    MimeMessage mail = greenMail.getReceivedMessages()[0];
    assertThat(GreenMailUtil.getAddressList(mail.getAllRecipients())).isEqualTo(EMAIL);

    String tempPassword = extractTempPassword(GreenMailUtil.getBody(mail));
    User user = userRepository.findByEmail(EMAIL).orElseThrow();
    assertThat(passwordEncoder.matches(tempPassword, user.getTempPassword())).isTrue();
  }

  /**
   * 첫 메일 발송은 템플릿 엔진·JavaMail 초기화 때문에 수 초가 걸릴 수 있다. 이 지연이 "메일이 오지 않음" 관찰 시간보다 길면
   * 장애가 있어도 테스트가 거짓으로 통과한다(첫 시도에서 실제로 겪음). 그래서 같은 발송 경로로 미리 한 통 보내 데운 뒤 비운다.
   */
  private void warmUpMailSending() throws Exception {
    long start = System.nanoTime();
    emailService.sendTempPasswordEmail("warm-up@mail.com", "WARMUP00");
    await().atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(greenMail.getReceivedMessages()).hasSize(1));
    System.out.printf("[warm-up] 메일 발송 소요: %d ms%n", (System.nanoTime() - start) / 1_000_000);
    greenMail.purgeEmailFromAllMailboxes();
  }

  // 템플릿의 <div th:text="${tempPassword}" ...>XXXXXXXX</div> 에서 8자리 임시 비밀번호 추출
  private String extractTempPassword(String body) {
    String decoded = body.replace("=\r\n", "").replace("=3D", "=");
    Matcher matcher = Pattern.compile(">\\s*([A-Za-z0-9]{8})\\s*</div>").matcher(decoded);
    assertThat(matcher.find()).as("메일 본문에서 임시 비밀번호를 찾지 못함").isTrue();
    return matcher.group(1);
  }
}
