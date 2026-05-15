package br.com.desafio.cardapi.adapters.out.persistence;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Table("cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CardEntity {
    @PrimaryKey("card_hash")
    private String cardHash;

    @Column("id")
    private String id = UUID.randomUUID().toString();

    @Column("encrypted_card")
    private String encryptedCard;
}
