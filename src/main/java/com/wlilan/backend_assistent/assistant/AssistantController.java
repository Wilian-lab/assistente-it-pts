package com.wlilan.backend_assistent.assistant;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wlilan.backend_assistent.assistant.dto.AssistantAskRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantAskResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantBenchmarkRequest;
import com.wlilan.backend_assistent.assistant.dto.AssistantBenchmarkResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantContextResponse;
import com.wlilan.backend_assistent.assistant.dto.AssistantOptionsResponse;
import com.wlilan.backend_assistent.usuario.UsuarioEntity;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

  private final AssistantService assistantService;
  private final AssistantMaintenanceService assistantMaintenanceService;
  private final AssistantBenchmarkService assistantBenchmarkService;

  public AssistantController(
      AssistantService assistantService,
      AssistantMaintenanceService assistantMaintenanceService,
      AssistantBenchmarkService assistantBenchmarkService) {
    this.assistantService = assistantService;
    this.assistantMaintenanceService = assistantMaintenanceService;
    this.assistantBenchmarkService = assistantBenchmarkService;
  }

  @PostMapping("/ask")
  public ResponseEntity<AssistantAskResponse> ask(
      @Valid @RequestBody AssistantAskRequest request,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.assistantService.ask(request, usuario));
  }

  @GetMapping("/context")
  public ResponseEntity<AssistantContextResponse> context(
      @RequestParam("itId") String itId,
      @RequestParam(value = "setorAtivo", required = false) String setorAtivo,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.assistantService.context(itId, setorAtivo, usuario));
  }

  @GetMapping("/options")
  public ResponseEntity<AssistantOptionsResponse> options(
      @RequestParam("itId") String itId,
      @RequestParam(value = "setorAtivo", required = false) String setorAtivo,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    return ResponseEntity.ok(this.assistantService.options(itId, setorAtivo, usuario));
  }

  @PostMapping("/reindex")
  public ResponseEntity<?> reindex(Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    if (usuario.getRole() == null || !"ADMIN".equalsIgnoreCase(usuario.getRole().name())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(java.util.Map.of("message", "Somente administradores podem reconstruir o indice do assistente."));
    }

    return ResponseEntity.ok(this.assistantMaintenanceService.rebuildAll());
  }

  @PostMapping("/benchmark")
  public ResponseEntity<AssistantBenchmarkResponse> benchmark(
      @Valid @RequestBody AssistantBenchmarkRequest request,
      Authentication authentication) {
    var usuario = (UsuarioEntity) authentication.getPrincipal();
    if (usuario.getRole() == null || !"ADMIN".equalsIgnoreCase(usuario.getRole().name())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return ResponseEntity.ok(this.assistantBenchmarkService.run(request, usuario));
  }
}
