package com.wlilan.backend_assistent.Security;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

@Service
public class TokenService {

  private static final Logger log = LoggerFactory.getLogger(TokenService.class);

  @Value("${security.token.secret}")
  private String secretKey;

  @Value("${security.token.issuer}")
  private String issuer;

  public String generateToken(String subject, String role) {
    var algorithm = Algorithm.HMAC256(this.secretKey);

    return JWT.create()
        .withIssuer(this.issuer)
        .withSubject(subject)
        .withClaim("role", role)
        .withIssuedAt(Instant.now())
        .withExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
        .sign(algorithm);
  }

  public String validateToken(String token) {
    try {
      var algorithm = Algorithm.HMAC256(this.secretKey);

      return JWT.require(algorithm)
          .withIssuer(this.issuer)
          .build()
          .verify(token)
          .getSubject();
    } catch (JWTVerificationException e) {
      log.warn("JWT validation failed: {}", e.getMessage());
      return null;
    }
  }
}
