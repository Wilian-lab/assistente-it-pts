package com.wlilan.backend_assistent.exeptions;

public class UserFoundException extends RuntimeException {
  public UserFoundException(String message) {
    super(message);
  }

  public UserFoundException() {
    super("Usuario ja existe");
  }
}
