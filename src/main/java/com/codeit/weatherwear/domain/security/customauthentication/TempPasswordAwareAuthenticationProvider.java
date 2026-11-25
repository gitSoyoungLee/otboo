package com.codeit.weatherwear.domain.security.customauthentication;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 기존 비밀번호와 임시 비밀번호(유효기간 내) 중 하나만 맞으면 로그인을 허용하는 인증 제공자.
 *
 * <p>비밀번호 초기화를 요청해도 기존 비밀번호를 덮어쓰지 않으므로,
 * 임시 비밀번호 메일이 실패하거나 제3자가 초기화를 요청해도 사용자는 기존 비밀번호로 로그인할 수 있다.
 */
@Slf4j
public class TempPasswordAwareAuthenticationProvider extends DaoAuthenticationProvider {

  @Override
  protected void additionalAuthenticationChecks(UserDetails userDetails,
      UsernamePasswordAuthenticationToken authentication) throws AuthenticationException {
    try {
      // 1) 기존 비밀번호 검사 (Spring Security 기본 동작)
      super.additionalAuthenticationChecks(userDetails, authentication);
    } catch (BadCredentialsException e) {
      // 2) 기존 비밀번호가 틀렸다면, 유효한 임시 비밀번호인지 확인
      Object credentials = authentication.getCredentials();
      if (credentials != null
          && userDetails instanceof CustomUserDetails customUserDetails
          && customUserDetails.matchesTempPassword(credentials.toString(), getPasswordEncoder(),
          Instant.now())) {
        log.info("[Sign In] Authenticated with temporary password. userId={}",
            customUserDetails.getUserId());
        return;
      }
      throw e;
    }
  }
}
