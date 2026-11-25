package com.codeit.weatherwear.domain.auth.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender javaMailSender;

  private final TemplateEngine templateEngine;

  @Value("${resetpassword.validity-seconds}")
  private long passwordValiditySeconds;

  // 비동기 실행은 호출 측(TempPasswordMailListener의 @Async)이 담당한다.
  // 여기서 @Async를 또 붙이면 스레드를 한 번 더 갈아타서, 발송 성공/실패를 리스너가 알 수 없게 된다.
  public void sendTempPasswordEmail(String toEmail, String tempPassword) {
    try {
      // Thymeleaf context 설정
      Context context = new Context();
      context.setVariable("tempPassword", tempPassword);
      context.setVariable("validMinutes", passwordValiditySeconds / 60);

      String htmlContent = templateEngine.process("email/reset-password", context);

      MimeMessage mimeMessage = javaMailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
      helper.setTo(toEmail);
      helper.setSubject("[옷장을 부탁해] 임시 비밀번호 안내");
      helper.setText(htmlContent, true); // HTML 본문

      javaMailSender.send(mimeMessage);
    } catch (Exception e) {
      // 기존: log.error("...", e.getMessage()) → 메시지에 {}가 없어 인자가 버려지고 원인이 남지 않았다.
      // 예외 객체를 마지막 인자로 넘겨야 SLF4J가 예외 종류·메시지·스택트레이스를 함께 기록한다.
      log.error("Fail to send reset-password email. to={}", maskEmail(toEmail), e);
    }
  }

  // 로그에 이메일 전체가 남지 않도록 앞 2글자만 노출 (예: vi***@mail.com)
  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return "***";
    }
    int at = email.indexOf('@');
    return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
  }
}