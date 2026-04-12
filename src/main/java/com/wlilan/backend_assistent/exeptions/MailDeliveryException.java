package com.wlilan.backend_assistent.exeptions;

public class MailDeliveryException extends RuntimeException {

  public MailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
