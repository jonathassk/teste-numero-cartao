package br.com.desafio.cardapi.domain.exception;

public class CardValidationException extends RuntimeException {
    public CardValidationException(String message) {
        super(message);
    }
}
