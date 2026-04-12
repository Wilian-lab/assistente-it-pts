package com.wlilan.backend_assistent.it;

public class UserFoundException extends RuntimeException {
  public UserFoundException(String message) {
    super(message);
  }

  public UserFoundException() {
    super("Usuario ja existe");
  }
}
