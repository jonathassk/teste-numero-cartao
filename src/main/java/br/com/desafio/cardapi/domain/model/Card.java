package br.com.desafio.cardapi.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Card {
    private String id;
    private String cardHash;
    private String encryptedCard;
    
    // Construtor sem ID (para criação)
    public Card(String cardHash, String encryptedCard) {
        this.cardHash = cardHash;
        this.encryptedCard = encryptedCard;
    }
}
