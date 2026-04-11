package com.wlilan.backend_assistent.usuario;

import java.util.LinkedHashSet;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.wlilan.backend_assistent.Security.SetorSupport;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class UsuarioSetorMigrationRunner implements CommandLineRunner {

  private final UsuarioRepository usuarioRepository;
  private final SetorRepository setorRepository;

  public UsuarioSetorMigrationRunner(UsuarioRepository usuarioRepository, SetorRepository setorRepository) {
    this.usuarioRepository = usuarioRepository;
    this.setorRepository = setorRepository;
  }

  @Override
  public void run(String... args) {
    for (var usuario : this.usuarioRepository.findAll()) {
      var desiredSetores = new LinkedHashSet<>(SetorSupport.parseSetores(usuario.getSetores()));
      if (desiredSetores.isEmpty()) {
        desiredSetores.addAll(SetorSupport.parseSetores(usuario.getSetor()));
      }
      if (desiredSetores.isEmpty()) {
        continue;
      }

      var currentSetores = usuario.getSetorCodes();
      if (currentSetores.equals(desiredSetores) && !usuario.getSetoresRelacionados().isEmpty()) {
        usuario.syncLegacySetorFields();
        this.usuarioRepository.save(usuario);
        continue;
      }

      usuario.setSetoresRelacionados(
          desiredSetores.stream()
              .map(this::findOrCreateSetor)
              .toList());
      usuario.syncLegacySetorFields();
      this.usuarioRepository.save(usuario);
    }
  }

  private SetorEntity findOrCreateSetor(String codigo) {
    var normalizedCodigo = SetorSupport.normalize(codigo);
    return this.setorRepository.findByCodigo(normalizedCodigo)
        .orElseGet(() -> {
          var setor = new SetorEntity();
          setor.setCodigo(normalizedCodigo);
          return this.setorRepository.save(setor);
        });
  }
}
