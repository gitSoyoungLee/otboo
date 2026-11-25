package com.codeit.weatherwear.domain.security.customauthentication;

import com.codeit.weatherwear.domain.user.entity.Role;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Slf4j
public class CustomUserDetails implements UserDetails, OAuth2User {

  private final UUID userID;
  private final String email;
  private final String password;
  private final Role role;
  private final boolean locked;
  private final Instant tempPasswordExpirationTime;
  private final String tempPassword;

  private Map<String, Object> attributes;

  public CustomUserDetails(UUID userId, String email, String password, Role role,
      boolean locked, Instant tempPasswordExpirationTime) {
    this(userId, email, password, role, locked, tempPasswordExpirationTime, null);
  }

  // 로그인(이메일+비밀번호) 시 사용: 임시 비밀번호 해시까지 담는다
  public CustomUserDetails(UUID userId, String email, String password, Role role,
      boolean locked, Instant tempPasswordExpirationTime, String tempPassword) {
    this.userID = userId;
    this.email = email;
    this.password = password;
    this.role = role;
    this.locked = locked;
    this.tempPasswordExpirationTime = tempPasswordExpirationTime;
    this.tempPassword = tempPassword;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    List<GrantedAuthority> authorities = List.of(
        new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    return authorities;
  }

  @Override
  public String getPassword() {
    return password;
  }

  // email + password로 로그인하므로 name 대신 email을 return
  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !locked;
  }

  // 기존에는 임시 비밀번호가 만료되면 계정 전체 로그인을 막았다(기존 비밀번호가 덮어써졌기 때문).
  // 이제 기존 비밀번호는 항상 유효하고, 임시 비밀번호 만료는 matchesTempPassword에서만 판단한다.
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /** 유효기간 내의 임시 비밀번호와 일치하는지 확인한다. */
  public boolean matchesTempPassword(String rawPassword,
      org.springframework.security.crypto.password.PasswordEncoder passwordEncoder, Instant now) {
    if (tempPassword == null || tempPasswordExpirationTime == null) {
      return false;
    }
    if (!tempPasswordExpirationTime.isAfter(now)) {
      log.info("Sign In with tempPassword rejected: expired. userId={}", userID);
      return false;
    }
    return passwordEncoder.matches(rawPassword, tempPassword);
  }

  public UUID getUserId() {
    return this.userID;
  }

  // OAuth2

  @Override
  public String getName() {
    if (attributes != null) {
      return (String) attributes.get("name");
    }
    return null;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }
}
