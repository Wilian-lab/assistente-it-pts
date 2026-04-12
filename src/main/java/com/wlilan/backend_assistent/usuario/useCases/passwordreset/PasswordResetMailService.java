package com.wlilan.backend_assistent.usuario.useCases.passwordreset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.wlilan.backend_assistent.exeptions.MailDeliveryException;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;

@Service
public class PasswordResetMailService {
  private static final Logger log = LoggerFactory.getLogger(PasswordResetMailService.class);

  private final JavaMailSender mailSender;
  private final String mailHost;
  private final String mailUsername;
  private final String mailPassword;
  private final String mailFrom;
  private final String subject;
  private final String frontendResetUrl;

  public PasswordResetMailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${spring.mail.username:}") String mailUsername,
      @Value("${spring.mail.password:}") String mailPassword,
      @Value("${app.password-reset.mail-from}") String mailFrom,
      @Value("${app.password-reset.mail-subject}") String subject,
      @Value("${app.password-reset.frontend-url}") String frontendResetUrl) {
    this.mailSender = mailSenderProvider.getIfAvailable();
    this.mailHost = mailHost;
    this.mailUsername = mailUsername;
    this.mailPassword = mailPassword;
    this.mailFrom = mailFrom;
    this.subject = subject;
    this.frontendResetUrl = frontendResetUrl;
  }

  public void sendResetLink(UsuarioEntity usuario, String rawToken) {
    requireMailSender();

    var resetLink = buildResetLink(rawToken);

    var message = new SimpleMailMessage();
    message.setFrom(this.mailFrom);
    message.setTo(usuario.getEmail());
    message.setSubject(this.subject);
    message.setText("""
        Ola %s,

        Recebemos um pedido para redefinir sua senha no Assistente IT.

        Acesse o link abaixo para cadastrar uma nova senha:
        %s

        Se voce nao fez esta solicitacao, ignore este email.
        """.formatted(firstNonBlank(usuario.getName(), "usuario"), resetLink).trim());

    send(message, "Nao foi possivel enviar o email de recuperacao de senha.");
  }

  public boolean isMailConfigured() {
    return this.mailSender != null
        && isFilled(this.mailHost)
        && isFilled(this.mailUsername)
        && isFilled(this.mailPassword);
  }

  public void sendRecoveryCode(UsuarioEntity usuario, String recoveryCode, String setor, boolean regenerated) {
    requireMailSender();

    var actionLabel = regenerated ? "Novo codigo de recuperacao" : "Codigo de recuperacao da sua conta";
    var intro = regenerated
        ? "Seu codigo de recuperacao foi atualizado por um administrador."
        : "Sua conta foi criada no Assistente IT.";

    var message = new SimpleMailMessage();
    message.setFrom(this.mailFrom);
    message.setTo(usuario.getEmail());
    message.setSubject(actionLabel + " - Assistente IT");
    message.setText("""
        Ola %s,

        %s

        Email de acesso: %s
        Setor: %s
        Codigo de recuperacao: %s

        Guarde esse codigo em local seguro. Ele podera ser usado para redefinir sua senha.
        """.formatted(
            firstNonBlank(usuario.getName(), "usuario"),
            intro,
            usuario.getEmail(),
            firstNonBlank(setor, "Nao informado"),
            recoveryCode).trim());

    send(message, "Nao foi possivel enviar o email com o codigo de recuperacao.");
  }

  private void requireMailSender() {
    if (!isMailConfigured()) {
      throw new MailDeliveryException(
          "O envio de email nao esta configurado neste ambiente. Configure o SMTP para usar a recuperacao por email.",
          null);
    }
  }

  private void send(SimpleMailMessage message, String errorMessage) {
    try {
      this.mailSender.send(message);
    } catch (Exception exception) {
      log.error(
          "Falha ao enviar email via SMTP. from={}, to={}, host={}, username={}",
          message.getFrom(),
          String.join(",", message.getTo() == null ? new String[0] : message.getTo()),
          this.mailHost,
          this.mailUsername,
          exception);
      throw new MailDeliveryException(errorMessage, exception);
    }
  }

  private String buildResetLink(String rawToken) {
    var separator = this.frontendResetUrl.contains("?") ? "&" : "?";
    return this.frontendResetUrl + separator + "token=" + rawToken;
  }

  private String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.trim().isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private boolean isFilled(String value) {
    return value != null && !value.trim().isBlank();
  }
}
