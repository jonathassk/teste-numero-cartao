package br.com.desafio.cardapi.application.services;

import br.com.desafio.cardapi.domain.exception.CardAlreadyExistsException;
import br.com.desafio.cardapi.domain.exception.CardValidationException;
import br.com.desafio.cardapi.domain.model.Card;
import br.com.desafio.cardapi.domain.ports.out.CardRepositoryPort;
import br.com.desafio.cardapi.domain.ports.out.CryptoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardService")
class CardServiceTest {

    @Mock
    private CardRepositoryPort repository;

    @Mock
    private CryptoPort crypto;

    private CardService service;

    private static final String VALID_CARD   = "4111111111111111";
    private static final String CARD_HASH    = "hashed-value";
    private static final String CARD_CIPHER  = "encrypted-value";

    @BeforeEach
    void setUp() {
        service = new CardService(repository, crypto);
    }

    // ──────────────────────────────────────────────────────────────
    // insertSingleCard()
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("insertSingleCard()")
    class InsertSingleCard {

        @BeforeEach
        void mockCrypto() {
            lenient().when(crypto.generateHash(VALID_CARD)).thenReturn(CARD_HASH);
            lenient().when(crypto.encrypt(VALID_CARD)).thenReturn(CARD_CIPHER);
        }

        @Test
        @DisplayName("deve salvar e retornar Card para número válido")
        void shouldSaveAndReturnCard() {
            when(repository.existsByHash(CARD_HASH)).thenReturn(false);
            when(repository.save(any())).thenReturn(new Card(CARD_HASH, CARD_CIPHER));

            Card result = service.insertSingleCard(VALID_CARD);

            assertThat(result).isNotNull();
            assertThat(result.getCardHash()).isEqualTo(CARD_HASH);
            verify(repository).save(any());
        }

        @Test
        @DisplayName("deve lançar CardAlreadyExistsException quando cartão já existe")
        void shouldThrowWhenCardAlreadyExists() {
            when(repository.existsByHash(CARD_HASH)).thenReturn(true);

            assertThatThrownBy(() -> service.insertSingleCard(VALID_CARD))
                    .isInstanceOf(CardAlreadyExistsException.class);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("deve lançar CardValidationException para número nulo")
        void shouldThrowForNullCardNumber() {
            assertThatThrownBy(() -> service.insertSingleCard(null))
                    .isInstanceOf(CardValidationException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("deve lançar CardValidationException para número com menos de 13 dígitos")
        void shouldThrowForTooShortCardNumber() {
            assertThatThrownBy(() -> service.insertSingleCard("411111"))
                    .isInstanceOf(CardValidationException.class)
                    .hasMessageContaining("between 13 and 19");
        }

        @Test
        @DisplayName("deve lançar CardValidationException para número com mais de 19 dígitos")
        void shouldThrowForTooLongCardNumber() {
            assertThatThrownBy(() -> service.insertSingleCard("41111111111111111119"))
                    .isInstanceOf(CardValidationException.class)
                    .hasMessageContaining("between 13 and 19");
        }

        @Test
        @DisplayName("deve lançar CardValidationException para número com caracteres não numéricos")
        void shouldThrowForNonNumericCardNumber() {
            assertThatThrownBy(() -> service.insertSingleCard("4111-1111-1111-1111"))
                    .isInstanceOf(CardValidationException.class)
                    .hasMessageContaining("numeric");
        }

        @Test
        @DisplayName("deve lançar CardValidationException para prefixo inválido (começa com 3)")
        void shouldThrowForInvalidPrefix() {
            // Amex começa com 3 — não é suportado (apenas 2, 4, 5, 6)
            assertThatThrownBy(() -> service.insertSingleCard("3714496353984312"))
                    .isInstanceOf(CardValidationException.class)
                    .hasMessageContaining("start with");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // checkCardExists()
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("checkCardExists()")
    class CheckCardExists {

        @Test
        @DisplayName("deve retornar Optional com Card quando cartão existe")
        void shouldReturnPresentOptionalWhenCardExists() {
            when(crypto.generateHash(VALID_CARD)).thenReturn(CARD_HASH);
            when(repository.findByHash(CARD_HASH)).thenReturn(Optional.of(new Card(CARD_HASH, CARD_CIPHER)));

            assertThat(service.checkCardExists(VALID_CARD)).isPresent();
        }

        @Test
        @DisplayName("deve retornar Optional vazio quando cartão não existe")
        void shouldReturnEmptyWhenCardNotFound() {
            when(crypto.generateHash(VALID_CARD)).thenReturn(CARD_HASH);
            when(repository.findByHash(CARD_HASH)).thenReturn(Optional.empty());

            assertThat(service.checkCardExists(VALID_CARD)).isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio para número nulo sem lançar exceção")
        void shouldReturnEmptyForNullInput() {
            assertThat(service.checkCardExists(null)).isEmpty();
            verifyNoInteractions(repository, crypto);
        }

        @Test
        @DisplayName("deve retornar Optional vazio para número em branco sem lançar exceção")
        void shouldReturnEmptyForBlankInput() {
            assertThat(service.checkCardExists("   ")).isEmpty();
            verifyNoInteractions(repository, crypto);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // processBatchFile()
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("processBatchFile()")
    class ProcessBatchFile {

        private InputStream toStream(String content) {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @BeforeEach
        void mockCrypto() {
            lenient().when(crypto.generateHash(any())).thenReturn(CARD_HASH);
            lenient().when(crypto.encrypt(any())).thenReturn(CARD_CIPHER);
        }

        @Test
        @DisplayName("deve inserir registros novos e ignorar linhas que não começam com 'C'")
        void shouldInsertNewCardsAndSkipNonCLines() {
            // Formato posicional: posições 7-25 contêm o número do cartão
            String content = "HEADER \n"
                    + "C      4111111111111111\n"
                    + "C      5500000000000004\n";

            when(repository.existsByHash(any())).thenReturn(false);

            CardService.BatchResult result = service.processBatchFile(toStream(content));

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(result.duplicates()).isEqualTo(0);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("deve contar duplicatas quando cartão já existe no banco")
        void shouldCountDuplicates() {
            String content = "C      4111111111111111\n";
            when(repository.existsByHash(any())).thenReturn(true);

            CardService.BatchResult result = service.processBatchFile(toStream(content));

            assertThat(result.inserted()).isEqualTo(0);
            assertThat(result.duplicates()).isEqualTo(1);
            verify(repository, never()).saveAll(any());
        }

        @Test
        @DisplayName("deve contar erro para linha 'C' com menos de 8 caracteres")
        void shouldCountErrorForTooShortLine() {
            String content = "C123\n"; // linha C mas com menos de 8 chars
            CardService.BatchResult result = service.processBatchFile(toStream(content));

            assertThat(result.errors()).isEqualTo(1);
            assertThat(result.inserted()).isEqualTo(0);
        }

        @Test
        @DisplayName("deve retornar zeros para arquivo vazio")
        void shouldReturnZerosForEmptyFile() {
            CardService.BatchResult result = service.processBatchFile(toStream(""));

            assertThat(result.inserted()).isEqualTo(0);
            assertThat(result.duplicates()).isEqualTo(0);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("deve persistir em lote quando o buffer atinge BATCH_SIZE")
        void shouldFlushBatchWhenBufferIsFull() {
            // Gera 1001 linhas válidas → deve acionar 1 saveAll no meio + 1 no final
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1001; i++) {
                sb.append("C      4111111111111111\n");
            }
            when(repository.existsByHash(any())).thenReturn(false);

            service.processBatchFile(toStream(sb.toString()));

            // saveAll deve ter sido chamado pelo menos 2 vezes (flush a cada 1000 + restante)
            verify(repository, atLeast(2)).saveAll(any());
        }
    }
}
