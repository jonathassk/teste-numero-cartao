package br.com.desafio.cardapi.adapters.out.persistence;

import br.com.desafio.cardapi.domain.model.Card;
import br.com.desafio.cardapi.domain.ports.out.CardRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CardPersistenceAdapter implements CardRepositoryPort {

    private final CassandraCardRepository repository;

    public CardPersistenceAdapter(CassandraCardRepository repository) {
        this.repository = repository;
    }

    @Override
    public Card save(Card card) {
        CardEntity entity = new CardEntity(
            card.getCardHash(),
            card.getId() != null ? card.getId() : java.util.UUID.randomUUID().toString(),
            card.getEncryptedCard()
        );
        CardEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void saveAll(List<Card> cards) {
        List<CardEntity> entities = cards.stream()
                .map(c -> new CardEntity(
                        c.getCardHash(),
                        c.getId() != null ? c.getId() : java.util.UUID.randomUUID().toString(),
                        c.getEncryptedCard()
                )).collect(Collectors.toList());
        repository.saveAll(entities);
    }

    @Override
    public Optional<Card> findByHash(String cardHash) {
        return repository.findById(cardHash).map(this::toDomain);
    }

    @Override
    public boolean existsByHash(String cardHash) {
        return repository.existsById(cardHash);
    }
    
    private Card toDomain(CardEntity entity) {
        return new Card(entity.getId(), entity.getCardHash(), entity.getEncryptedCard());
    }
}
