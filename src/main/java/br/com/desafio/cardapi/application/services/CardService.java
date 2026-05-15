package br.com.desafio.cardapi.application.services;

import br.com.desafio.cardapi.domain.model.Card;
import br.com.desafio.cardapi.domain.ports.in.CardUseCase;
import br.com.desafio.cardapi.domain.exception.CardValidationException;
import br.com.desafio.cardapi.domain.exception.CardAlreadyExistsException;
import br.com.desafio.cardapi.domain.ports.out.CardRepositoryPort;
import br.com.desafio.cardapi.domain.ports.out.CryptoPort;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CardService implements CardUseCase {

    private final CardRepositoryPort repository;
    private final CryptoPort crypto;

    private static final int BATCH_SIZE = 1000;

    public CardService(CardRepositoryPort repository, CryptoPort crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    @Override
    public Card insertSingleCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank())
            throw new CardValidationException("Card number cannot be empty");
        if (cardNumber.length() < 13 || cardNumber.length() > 19)
            throw new CardValidationException("Card number must be between 13 and 19 digits");
        if (!cardNumber.matches("^[0-9]*$"))
            throw new CardValidationException("Card number must be numeric");
        if (!cardNumber.startsWith("4") && !cardNumber.startsWith("5") && !cardNumber.startsWith("2")
                && !cardNumber.startsWith("6"))
            throw new CardValidationException("Card number must start with 2, 4, 5 or 6");

        String hash = crypto.generateHash(cardNumber);
        if (repository.existsByHash(hash))
            throw new CardAlreadyExistsException("Card already exists");

        return repository.save(new Card(hash, crypto.encrypt(cardNumber)));
    }

    @Override
    public Optional<Card> checkCardExists(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank())
            return Optional.empty();
        return repository.findByHash(crypto.generateHash(cardNumber));
    }

    @Override
    public BatchResult processBatchFile(InputStream fileStream) {
        int inserted = 0;
        int duplicates = 0;
        int errors = 0;

        try (var reader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
            String line;
            List<Card> batch = new ArrayList<>(BATCH_SIZE);

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("C"))
                    continue;
                if (line.length() < 8) {
                    errors++;
                    continue;
                }

                String cardNumber = extractCardNumber(line);
                if (cardNumber.isBlank()) {
                    errors++;
                    continue;
                }

                String hash = crypto.generateHash(cardNumber);
                if (repository.existsByHash(hash)) {
                    duplicates++;
                    continue;
                }

                batch.add(new Card(hash, crypto.encrypt(cardNumber)));
                inserted++;

                if (batch.size() >= BATCH_SIZE) {
                    repository.saveAll(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty())
                repository.saveAll(batch);

        } catch (Exception e) {
            throw new RuntimeException("Error processing batch file", e);
        }

        return new BatchResult(inserted, duplicates, errors);
    }

    private String extractCardNumber(String line) {
        if (line.length() <= 7)
            return "";
        return line.substring(7, Math.min(line.length(), 26)).trim();
    }
}
