#ifndef CRYPTO_H
#define CRYPTO_H

/* AES-128-CBC sifreleme/sifre cozme
 * Anahtar ve IV her iki tarafta (Android + Server) ayni.
 * Mesajlar Base64 kodlanarak gonderilir. */

/* Base64 kodlanmis sifeli metni dondurur. Sonucu free() ile serbest birakin. */
char* aes_encrypt(const char* plaintext);

/* Base64 kodlanmis sifeli metni cozer. Sonucu free() ile serbest birakin. */
char* aes_decrypt(const char* base64_ciphertext);

#endif
