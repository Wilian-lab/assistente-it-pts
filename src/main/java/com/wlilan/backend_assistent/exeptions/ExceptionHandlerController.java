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

  @ExceptionHandler(UserFoundException.class)
  public ResponseEntity<ErrorMessageDTO> handleUserFound(UserFoundException e) {
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
}
