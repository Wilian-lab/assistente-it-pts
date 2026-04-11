package com.wlilan.backend_assistent.Security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.exceptions.JWTVerificationException;

@Service
public class TokenService {

  public static final long DEFAULT_EXPIRES_IN_SECONDS = 8 * 60 * 60;

  private static final Logger log = LoggerFactory.getLogger(TokenService.class);

  @Value("${security.token.secret}")
  private String secretKey;

  @Value("${security.token.issuer}")
  private String issuer;

  @Value("${security.token.expires-in-seconds:" + DEFAULT_EXPIRES_IN_SECONDS + "}")
  private long expiresInSeconds;

  public String generateToken(String subject, String role, String setorAtivo) {
    var algorithm = Algorithm.HMAC256(this.secretKey);
    var now = Instant.now();

    return JWT.create()
        .withIssuer(this.issuer)
        .withSubject(subject)
        .withClaim("role", role)
        .withClaim("setorAtivo", SetorSupport.normalize(setorAtivo))
        .withIssuedAt(now)
        .withExpiresAt(now.plus(this.expiresInSeconds, ChronoUnit.SECONDS))
        .sign(algorithm);
  }

  public TokenValidationResult validate(String token) {
    try {
      var algorithm = Algorithm.HMAC256(this.secretKey);
      var decodedToken = JWT.require(algorithm)
          .withIssuer(this.issuer)
          .build()
          .verify(token);
      return TokenValidationResult.valid(
          decodedToken.getSubject(),
          decodedToken.getClaim("setorAtivo").asString());
    } catch (TokenExpiredException exception) {
      log.warn("JWT validation failed: {}", exception.getMessage());
      return TokenValidationResult.expired();
    } catch (JWTVerificationException e) {
      log.warn("JWT validation failed: {}", e.getMessage());
      return TokenValidationResult.invalid();
    }
  }

  public long getExpiresInSeconds() {
    return this.expiresInSeconds;
  }

  public record TokenValidationResult(
      String subject,
      String setorAtivo,
      boolean expiredToken) {

    public static TokenValidationResult valid(String subject, String setorAtivo) {
      return new TokenValidationResult(subject, setorAtivo, false);
    }

    public static TokenValidationResult expired() {
      return new TokenValidationResult(null, null, true);
    }

    public static TokenValidationResult invalid() {
      return new TokenValidationResult(null, null, false);
    }

    public boolean isValid() {
      return subject != null && !subject.isBlank();
    }

    public boolean isExpired() {
      return expiredToken;
    }
  }
}
