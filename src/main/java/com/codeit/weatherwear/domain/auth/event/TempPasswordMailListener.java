package com.codeit.weatherwear.domain.auth.event;

import com.codeit.weatherwear.domain.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 임시 비밀번호 메일 발송 리스너.
 *
 * <ul>
 *   <li>AFTER_COMMIT: 트랜잭션이 커밋된 뒤에만 실행된다. 롤백되면 이벤트는 버려지고 메일은 나가지 않는다.</li>
 *   <li>@Async: 커밋 후 메일 발송(SMTP 왕복)을 요청 스레드가 기다리지 않도록 별도 스레드에서 실행한다.
 *       기존 @Async 메일 발송이 실제로 쓰던 eventExecutor를 명시해, 실행 스레드풀은 바꾸지 않는다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TempPasswordMailListener {

  private final EmailService emailService;

  @Async("eventExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(TempPasswordIssuedEvent event) {
    emailService.sendTempPasswordEmail(event.email(), event.tempPassword());
  }
}
