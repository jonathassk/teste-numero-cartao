package br.com.desafio.cardapi.adapters.out.crypto;

import br.com.desafio.cardapi.domain.ports.out.CryptoPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Component
public class CryptoAdapter implements CryptoPort {

    private final SecretKeySpec aesKeySpec;
    private final SecretKeySpec hmacKeySpec;

    private static final String AES_ALGORITHM    = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM   = "HmacSHA256";
    private static final int    GCM_IV_LENGTH    = 12;  // bytes
    private static final int    GCM_TAG_BITS     = 128; // authentication tag

    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoAdapter(
            @Value("${app.security.crypto.aes-key}") String aesKey,
            @Value("${app.security.crypto.hmac-secret}") String hmacSecret) {
        this.aesKeySpec  = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
        this.hmacKeySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Override
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV so decrypt can extract it: [12 bytes IV][ciphertext + 16 bytes tag]
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        try {
            byte[] combined     = Base64.getDecoder().decode(cipherText);
            byte[] iv           = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting data", e);
        }
    }

    @Override
    public String generateHash(String plainText) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKeySpec);
            byte[] hashBytes = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing data", e);
        }
    }
}
