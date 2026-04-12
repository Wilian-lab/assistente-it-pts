package com.wlilan.backend_assistent.exeptions;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandlerController {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorMessageDTO> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
    var fieldError = e.getBindingResult().getFieldError();

    var dto = new ErrorMessageDTO(
        fieldError != null ? fieldError.getDefaultMessage() : "Dados invalidos",
        fieldError != null ? fieldError.getField() : null,
        HttpStatus.BAD_REQUEST.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
  }

  @ExceptionHandler(com.wlilan.backend_assistent.it.UserFoundException.class)
  public ResponseEntity<ErrorMessageDTO> handleUserFound(com.wlilan.backend_assistent.it.UserFoundException e) {
    var dto = new ErrorMessageDTO(
        e.getMessage(),
        null,
        HttpStatus.BAD_REQUEST.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorMessageDTO> handleIllegalArgument(IllegalArgumentException e) {
    var dto = new ErrorMessageDTO(
        e.getMessage(),
        null,
        HttpStatus.BAD_REQUEST.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorMessageDTO> handleInvalidCredentials(InvalidCredentialsException e) {
    var dto = new ErrorMessageDTO(
        e.getMessage(),
        null,
        HttpStatus.UNAUTHORIZED.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(dto);
  }

  @ExceptionHandler(InvalidSetorAccessException.class)
  public ResponseEntity<ErrorMessageDTO> handleInvalidSetorAccess(InvalidSetorAccessException e) {
    var dto = new ErrorMessageDTO(
        e.getMessage(),
        "setor",
        HttpStatus.FORBIDDEN.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(dto);
  }

  @ExceptionHandler(MailDeliveryException.class)
  public ResponseEntity<ErrorMessageDTO> handleMailDelivery(MailDeliveryException e) {
    var dto = new ErrorMessageDTO(
        e.getMessage(),
        null,
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(dto);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ErrorMessageDTO> handleTooManyRequests(TooManyRequestsException e) {
    var dto = new ErrorMessageDTO(
        e.getMessage(),
        null,
        HttpStatus.TOO_MANY_REQUESTS.value(),
        LocalDateTime.now());

    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(dto);
  }
}
