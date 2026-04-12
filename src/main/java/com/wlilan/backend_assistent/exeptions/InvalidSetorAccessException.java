package com.wlilan.backend_assistent.exeptions;

public class InvalidSetorAccessException extends RuntimeException {

  public InvalidSetorAccessException() {
    super("Setor invalido para este usuario");
  }

  public InvalidSetorAccessException(String message) {
    super(message);
  }
}
