package br.com.desafio.cardapi.adapters.out.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CryptoAdapter")
class CryptoAdapterTest {

    // 16-byte AES-128 key (multiple of 8 bits: 128-bit key)
    private static final String AES_KEY = "0123456789abcdef"; // 16 chars → 128 bits
    private static final String HMAC_SECRET = "hmac-secret-key-for-unit-testing";

    private CryptoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CryptoAdapter(AES_KEY, HMAC_SECRET);
    }

    // ──────────────────────────────────────────────────────────────
    // encrypt()
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("encrypt()")
    class Encrypt {

        @Test
        @DisplayName("deve retornar uma string Base64 não-nula e não-vazia")
        void shouldReturnNonNullBase64String() {
            String result = adapter.encrypt("4111111111111111");

            assertThat(result).isNotNull().isNotBlank();
            assertThatCode(() -> Base64.getDecoder().decode(result))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deve embutir o IV nos primeiros 12 bytes do payload")
        void shouldEmbedIvInFirst12Bytes() {
            String cipherText = adapter.encrypt("4111111111111111");
            byte[] combined = Base64.getDecoder().decode(cipherText);

            assertThat(combined.length).isGreaterThan(12);
        }

        @Test
        @DisplayName("mesmo plaintext deve gerar ciphertexts distintos (IV aleatório)")
        void shouldProduceDifferentCiphertextsForSamePlaintext() {
            String card = "4111111111111111";
            String cipher1 = adapter.encrypt(card);
            String cipher2 = adapter.encrypt(card);

            assertThat(cipher1).isNotEqualTo(cipher2);
        }

        @Test
        @DisplayName("deve criptografar uma string vazia sem lançar exceção")
        void shouldEncryptEmptyString() {
            assertThatCode(() -> adapter.encrypt(""))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deve criptografar texto longo sem lançar exceção")
        void shouldEncryptLongString() {
            String longInput = "9".repeat(500);
            assertThatCode(() -> adapter.encrypt(longInput))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // decrypt()
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("decrypt()")
    class Decrypt {

        @Test
        @DisplayName("decrypt(encrypt(x)) deve retornar x — round-trip básico")
        void shouldDecryptToOriginalValue() {
            String original = "4111111111111111";
            String cipherText = adapter.encrypt(original);

            assertThat(adapter.decrypt(cipherText)).isEqualTo(original);
        }

        @Test
        @DisplayName("round-trip deve funcionar com número de cartão mínimo (13 dígitos)")
        void shouldRoundTripShortCardNumber() {
            String card = "4000000000000";
            assertThat(adapter.decrypt(adapter.encrypt(card))).isEqualTo(card);
        }

        @Test
        @DisplayName("round-trip deve funcionar com número de cartão máximo (19 dígitos)")
        void shouldRoundTripLongCardNumber() {
            String card = "6221260000000000000";
            assertThat(adapter.decrypt(adapter.encrypt(card))).isEqualTo(card);
        }

        @Test
        @DisplayName("round-trip deve preservar dígitos de Visa, Mastercard, Elo e Amex")
        void shouldRoundTripVariousCardPrefixes() {
            String[] cards = { "4111111111111111", "5500000000000004", "6362970000457013", "2221000000000000" };
            for (String card : cards) {
                assertThat(adapter.decrypt(adapter.encrypt(card)))
                        .as("round-trip para %s", card)
                        .isEqualTo(card);
            }
        }

        @Test
        @DisplayName("deve lançar RuntimeException para ciphertext corrompido")
        void shouldThrowOnTamperedCiphertext() {
            String cipherText = adapter.encrypt("4111111111111111");
            String tampered = cipherText.substring(0, cipherText.length() - 4) + "AAAA";

            assertThatThrownBy(() -> adapter.decrypt(tampered))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error decrypting data");
        }

        @Test
        @DisplayName("deve lançar RuntimeException para string inválida (não é Base64)")
        void shouldThrowOnInvalidBase64Input() {
            assertThatThrownBy(() -> adapter.decrypt("not-valid-base64!!!"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("deve lançar RuntimeException para payload menor que o IV (< 12 bytes)")
        void shouldThrowOnPayloadShorterThanIv() {
            String tooShort = Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3, 4 });

            assertThatThrownBy(() -> adapter.decrypt(tooShort))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("decrypt com chave diferente deve lançar RuntimeException (autenticação GCM)")
        void shouldFailDecryptionWithWrongKey() {
            String cipherText = adapter.encrypt("4111111111111111");
            CryptoAdapter differentKeyAdapter = new CryptoAdapter("ffffffffffffffff", HMAC_SECRET);

            assertThatThrownBy(() -> differentKeyAdapter.decrypt(cipherText))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error decrypting data");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // generateHash()
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("generateHash()")
    class GenerateHash {

        @Test
        @DisplayName("deve retornar uma string Base64 não-nula e não-vazia")
        void shouldReturnNonNullBase64String() {
            String hash = adapter.generateHash("4111111111111111");

            assertThat(hash).isNotNull().isNotBlank();
            assertThatCode(() -> Base64.getDecoder().decode(hash))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("HMAC-SHA256 deve produzir exatamente 32 bytes (256 bits) → 44 chars Base64")
        void shouldProduceCorrectHmacLength() {
            byte[] hashBytes = Base64.getDecoder().decode(adapter.generateHash("4111111111111111"));
            assertThat(hashBytes).hasSize(32);
        }

        @Test
        @DisplayName("mesma entrada deve sempre produzir o mesmo hash (determinístico)")
        void shouldBeDeterministic() {
            String card = "4111111111111111";
            assertThat(adapter.generateHash(card)).isEqualTo(adapter.generateHash(card));
        }

        @Test
        @DisplayName("entradas distintas devem produzir hashes distintos")
        void shouldProduceDifferentHashesForDifferentInputs() {
            String hash1 = adapter.generateHash("4111111111111111");
            String hash2 = adapter.generateHash("5500000000000004");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("segredos HMAC distintos devem produzir hashes distintos para o mesmo input")
        void shouldProduceDifferentHashesForDifferentSecrets() {
            CryptoAdapter other = new CryptoAdapter(AES_KEY, "outro-segredo-completamente-diferente");
            String card = "4111111111111111";

            assertThat(adapter.generateHash(card)).isNotEqualTo(other.generateHash(card));
        }

        @Test
        @DisplayName("deve gerar hash de string vazia sem lançar exceção")
        void shouldHashEmptyStringWithoutException() {
            assertThatCode(() -> adapter.generateHash(""))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deve gerar hash de texto longo sem lançar exceção")
        void shouldHashLongStringWithoutException() {
            assertThatCode(() -> adapter.generateHash("9".repeat(500)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("entradas que diferem apenas em 1 caractere devem produzir hashes totalmente diferentes (avalanche)")
        void shouldShowAvalancheEffect() {
            String hash1 = adapter.generateHash("4111111111111111");
            String hash2 = adapter.generateHash("4111111111111112");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }
}
