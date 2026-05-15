package br.com.desafio.cardapi.adapters.in.web;

import br.com.desafio.cardapi.domain.exception.CardValidationException;
import br.com.desafio.cardapi.domain.model.Card;
import br.com.desafio.cardapi.domain.ports.in.CardUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cards")
@Tag(name = "Cards", description = "Operações sobre cartões de crédito")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardUseCase cardUseCase;

    public CardController(CardUseCase cardUseCase) {
        this.cardUseCase = cardUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastra um único cartão", description = "O cartão deve ser numérico e ter entre 13 e 19 dígitos.")
    public ResponseEntity<?> insertSingleCard(@RequestBody Map<String, String> payload) {
        String cardNumber = payload.get("card_number");
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new CardValidationException("O parâmetro 'card_number' é obrigatório.");
        }

        Card saved = cardUseCase.insertSingleCard(cardNumber.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Cartão cadastrado com sucesso.",
                "id", saved.getId()
        ));
    }

    @PostMapping(value = "/batch", consumes = "multipart/form-data")
    @Operation(summary = "Importa cartões via arquivo TXT (Posicional)", description = "Processa um arquivo em lote gerando HASH e criptografando cada cartão válido.")
    public ResponseEntity<?> insertBatchCards(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new CardValidationException("Arquivo vazio.");
        }

        try {
            CardUseCase.BatchResult result = cardUseCase.processBatchFile(file.getInputStream());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Processamento em lote finalizado.",
                    "inseridos", result.inserted(),
                    "duplicados", result.duplicates(),
                    "erros", result.errors()
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/check")
    @Operation(summary = "Consulta existência de cartão", description = "Informa se o cartão fornecido existe na base de dados de forma extremamente rápida em O(1).")
    public ResponseEntity<?> checkCard(@RequestParam("card_number") String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new CardValidationException("O parâmetro 'card_number' é obrigatório.");
        }

        Optional<Card> cardOpt = cardUseCase.checkCardExists(cardNumber.trim());
        if (cardOpt.isPresent()) {
            return ResponseEntity.ok(Map.of("exists", true, "id", cardOpt.get().getId()));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("exists", false));
        }
    }
}
