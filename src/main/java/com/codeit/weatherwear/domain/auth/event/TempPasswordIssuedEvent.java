package com.codeit.weatherwear.domain.auth.event;

/**
 * 임시 비밀번호가 발급되었음을 알리는 이벤트.
 *
 * <p>메일 발송은 이 이벤트를 받은 리스너가 "커밋이 확정된 뒤"에만 수행한다.
 */
public record TempPasswordIssuedEvent(String email, String tempPassword) {

  // record 기본 toString은 모든 필드를 출력하므로, 로그에 평문 임시 비밀번호가 남지 않도록 가린다.
  @Override
  public String toString() {
    return "TempPasswordIssuedEvent[email=" + email + ", tempPassword=****]";
  }
}
