package br.com.desafio.cardapi.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-super-secure-2026");
        ReflectionTestUtils.setField(jwtService, "expiration", 3_600_000L); // 1h
    }

    // ── generateToken() ──────────────────────────────────────────────
    @Test
    @DisplayName("generateToken() deve retornar um token JWT não-nulo e não-vazio")
    void generateToken_shouldReturnNonBlankToken() {
        String token = jwtService.generateToken("admin");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("generateToken() deve retornar token no formato header.payload.signature")
    void generateToken_shouldHaveThreeParts() {
        String token = jwtService.generateToken("admin");
        assertThat(token.split("\\.")).hasSize(3);
    }

    // ── validateTokenAndGetSubject() ──────────────────────────────────
    @Test
    @DisplayName("validate() deve retornar o subject (username) para token válido")
    void validate_shouldReturnSubjectForValidToken() {
        String token = jwtService.generateToken("jonathas");
        String subject = jwtService.validateTokenAndGetSubject(token);
        assertThat(subject).isEqualTo("jonathas");
    }

    @Test
    @DisplayName("validate() deve retornar null para token com assinatura inválida")
    void validate_shouldReturnNullForTamperedToken() {
        String token = jwtService.generateToken("admin");
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";
        assertThat(jwtService.validateTokenAndGetSubject(tampered)).isNull();
    }

    @Test
    @DisplayName("validate() deve retornar null para string aleatória que não é JWT")
    void validate_shouldReturnNullForGarbage() {
        assertThat(jwtService.validateTokenAndGetSubject("not.a.jwt")).isNull();
    }

    @Test
    @DisplayName("validate() deve retornar null para token gerado com secret diferente")
    void validate_shouldReturnNullForTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService();
        ReflectionTestUtils.setField(other, "secret", "completely-different-secret");
        ReflectionTestUtils.setField(other, "expiration", 3_600_000L);

        String foreignToken = other.generateToken("hacker");
        assertThat(jwtService.validateTokenAndGetSubject(foreignToken)).isNull();
    }

    @Test
    @DisplayName("validate() deve retornar null para token expirado")
    void validate_shouldReturnNullForExpiredToken() {
        JwtService shortLived = new JwtService();
        ReflectionTestUtils.setField(shortLived, "secret", "test-secret-key-super-secure-2026");
        ReflectionTestUtils.setField(shortLived, "expiration", -1L); // já nasceu expirado

        String expiredToken = shortLived.generateToken("admin");
        assertThat(jwtService.validateTokenAndGetSubject(expiredToken)).isNull();
    }
}
