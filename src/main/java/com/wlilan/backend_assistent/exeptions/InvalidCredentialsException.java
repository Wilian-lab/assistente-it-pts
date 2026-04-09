package com.wlilan.backend_assistent.exeptions;

public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() {
    super("Email ou senha incorretos");
  }

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
