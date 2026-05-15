package br.com.desafio.cardapi.domain.ports.out;

public interface CryptoPort {
    String encrypt(String plainText);
    String decrypt(String cipherText);
    String generateHash(String plainText);
}
