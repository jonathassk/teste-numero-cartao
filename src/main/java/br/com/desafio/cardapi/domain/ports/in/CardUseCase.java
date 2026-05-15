package br.com.desafio.cardapi.domain.ports.in;

import br.com.desafio.cardapi.domain.model.Card;
import java.io.InputStream;
import java.util.Optional;

public interface CardUseCase {
    Card insertSingleCard(String cardNumber);

    Optional<Card> checkCardExists(String cardNumber);

    BatchResult processBatchFile(InputStream fileStream);

    record BatchResult(int inserted, int duplicates, int errors) {
    }
}
