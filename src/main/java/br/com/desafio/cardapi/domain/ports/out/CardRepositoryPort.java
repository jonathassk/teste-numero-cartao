package br.com.desafio.cardapi.domain.ports.out;

import br.com.desafio.cardapi.domain.model.Card;
import java.util.List;
import java.util.Optional;

public interface CardRepositoryPort {
    Card save(Card card);
    void saveAll(List<Card> cards);
    Optional<Card> findByHash(String cardHash);
    boolean existsByHash(String cardHash);
}
