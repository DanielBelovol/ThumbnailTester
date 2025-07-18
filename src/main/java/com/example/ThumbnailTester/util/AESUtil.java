package com.example.ThumbnailTester.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class AESUtil {
    private static final String ALGORITHM = "AES";
    @Value("${key.aes}")
    private String keyAes;

    public String decrypt(String encryptedText) throws Exception {
        byte[] decodedKey = keyAes.getBytes("UTF-8");
        SecretKeySpec secretKey = new SecretKeySpec(decodedKey, ALGORITHM);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        byte[] decodedEncryptedText = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(decodedEncryptedText);

        return new String(decryptedBytes, "UTF-8");
    }

    public String encrypt(String plainText) throws Exception {
        byte[] decodedKey = keyAes.getBytes("UTF-8");
        SecretKeySpec secretKey = new SecretKeySpec(decodedKey, ALGORITHM);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
}
