package br.com.desafio.cardapi.infrastructure.config;

import br.com.desafio.cardapi.application.services.CardService;
import br.com.desafio.cardapi.domain.ports.out.CardRepositoryPort;
import br.com.desafio.cardapi.domain.ports.out.CryptoPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public CardService cardService(CardRepositoryPort repository, CryptoPort crypto) {
        return new CardService(repository, crypto);
    }
}
