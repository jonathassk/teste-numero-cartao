package br.com.desafio.cardapi.adapters.in.web;

import br.com.desafio.cardapi.infrastructure.config.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Geração de Token JWT")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping
    @Operation(summary = "Realiza login e retorna o Token", description = "Pode ser acessado com qualquer usuário e senha não vazios para gerar o JWT de teste.")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        // Para fins do desafio, aceitamos qualquer usuário e senha desde que preenchidos
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            String token = jwtService.generateToken(username);
            return ResponseEntity.ok(Map.of("access_token", token));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Credenciais inválidas"));
    }
}
