package com.example.lms.security;

import com.example.lms.entity.StudentProfile;
import com.example.lms.repository.StudentProfileRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomOidcUserService extends OidcUserService {

  private final StudentProfileRepository studentProfiles;

  public CustomOidcUserService(StudentProfileRepository studentProfiles) {
    this.studentProfiles = studentProfiles;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = super.loadUser(userRequest);
    String email = oidcUser.getEmail();

    if (email == null) {
      throw new OAuth2AuthenticationException(new OAuth2Error("invalid_request", "Email not found from OAuth2 provider", ""));
    }

    StudentProfile profile = studentProfiles.findByEmail(email)
        .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("unauthorized", "Email not registered as a library student account.", "")));

    List<GrantedAuthority> authorities = new ArrayList<>(oidcUser.getAuthorities());
    authorities.add(new SimpleGrantedAuthority("ROLE_" + profile.getAccount().getRole().name()));

    // Use the account username as the Principal name instead of Google's subject ID
    return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
  }
}
