package br.com.desafio.cardapi.adapters.out.persistence;

import br.com.desafio.cardapi.domain.model.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardPersistenceAdapter")
class CardPersistenceAdapterTest {

    @Mock
    private CassandraCardRepository repository;

    @InjectMocks
    private CardPersistenceAdapter adapter;

    private CardEntity savedEntity;

    @BeforeEach
    void setUp() {
        savedEntity = new CardEntity("hash123", "id-uuid-abc", "encryptedXYZ");
    }

    // ── save() ──────────────────────────────────────────────────────
    @Test
    @DisplayName("save() deve persistir a entidade e retornar Card com os mesmos dados")
    void save_shouldPersistAndReturnDomainCard() {
        when(repository.save(any(CardEntity.class))).thenReturn(savedEntity);

        Card input = new Card("hash123", "encryptedXYZ");
        Card result = adapter.save(input);

        assertThat(result.getCardHash()).isEqualTo("hash123");
        assertThat(result.getEncryptedCard()).isEqualTo("encryptedXYZ");
        assertThat(result.getId()).isEqualTo("id-uuid-abc");
        verify(repository).save(any(CardEntity.class));
    }

    @Test
    @DisplayName("save() deve gerar um UUID quando Card não possui ID")
    void save_shouldGenerateUuidWhenIdIsNull() {
        when(repository.save(any(CardEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Card input = new Card("hash123", "encryptedXYZ"); // sem ID
        Card result = adapter.save(input);

        assertThat(result.getId()).isNotNull().isNotBlank();
    }

    // ── saveAll() ────────────────────────────────────────────────────
    @Test
    @DisplayName("saveAll() deve chamar repository.saveAll com a lista convertida")
    void saveAll_shouldDelegateBatchToRepository() {
        List<Card> cards = List.of(
                new Card("hash1", "enc1"),
                new Card("hash2", "enc2")
        );

        adapter.saveAll(cards);

        verify(repository).saveAll(argThat(list ->
                StreamSupport.stream(list.spliterator(), false).count() == 2));
    }

    // ── findByHash() ─────────────────────────────────────────────────
    @Test
    @DisplayName("findByHash() deve retornar Optional com Card quando hash existe")
    void findByHash_shouldReturnCardWhenFound() {
        when(repository.findById("hash123")).thenReturn(Optional.of(savedEntity));

        Optional<Card> result = adapter.findByHash("hash123");

        assertThat(result).isPresent();
        assertThat(result.get().getCardHash()).isEqualTo("hash123");
    }

    @Test
    @DisplayName("findByHash() deve retornar Optional vazio quando hash não existe")
    void findByHash_shouldReturnEmptyWhenNotFound() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        assertThat(adapter.findByHash("unknown")).isEmpty();
    }

    // ── existsByHash() ────────────────────────────────────────────────
    @Test
    @DisplayName("existsByHash() deve retornar true quando hash existe")
    void existsByHash_shouldReturnTrueWhenExists() {
        when(repository.existsById("hash123")).thenReturn(true);
        assertThat(adapter.existsByHash("hash123")).isTrue();
    }

    @Test
    @DisplayName("existsByHash() deve retornar false quando hash não existe")
    void existsByHash_shouldReturnFalseWhenNotExists() {
        when(repository.existsById("unknown")).thenReturn(false);
        assertThat(adapter.existsByHash("unknown")).isFalse();
    }
}
