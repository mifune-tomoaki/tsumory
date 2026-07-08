package com.example.tsumory.security;

import com.example.tsumory.domain.User;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class TsumoryUserDetails implements UserDetails {

  @Getter private final Long id;
  private final String email;
  private final String passwordHash;

  public TsumoryUserDetails(User user) {
    this.id = user.getId();
    this.email = user.getEmail();
    this.passwordHash = user.getPasswordHash();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String toString() {
    return "TsumoryUserDetails[id=%d, email=%s, passwordHash=***]".formatted(id, email);
  }
}
