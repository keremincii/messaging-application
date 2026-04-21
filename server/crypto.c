#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <openssl/evp.h>
#include <openssl/bio.h>
#include <openssl/buffer.h>
#include "crypto.h"

/* Android ve Server ayni anahtari kullanmali */
static const unsigned char AES_KEY[16] = {
    'C','h','a','t','A','p','p','2','0','2','4','K','e','y','!','!'
};
static const unsigned char AES_IV[16] = {
    'C','h','a','t','A','p','p','I','n','i','t','V','e','c','!','!'
};

/* ── Base64 Encode ─────────────────────────────────────────── */
static char* base64_encode(const unsigned char* input, int length) {
    BIO *bio, *b64;
    BUF_MEM *bufferPtr;

    b64 = BIO_new(BIO_f_base64());
    bio = BIO_new(BIO_s_mem());
    bio = BIO_push(b64, bio);

    BIO_set_flags(bio, BIO_FLAGS_BASE64_NO_NL); /* Tek satir, newline yok */
    BIO_write(bio, input, length);
    BIO_flush(bio);
    BIO_get_mem_ptr(bio, &bufferPtr);

    char* result = (char*)malloc(bufferPtr->length + 1);
    memcpy(result, bufferPtr->data, bufferPtr->length);
    result[bufferPtr->length] = '\0';

    BIO_free_all(bio);
    return result;
}

/* ── Base64 Decode ─────────────────────────────────────────── */
static unsigned char* base64_decode(const char* input, int* out_len) {
    int length = strlen(input);
    unsigned char* buffer = (unsigned char*)malloc(length);

    BIO *bio, *b64;
    b64 = BIO_new(BIO_f_base64());
    bio = BIO_new_mem_buf(input, length);
    bio = BIO_push(b64, bio);

    BIO_set_flags(bio, BIO_FLAGS_BASE64_NO_NL);
    *out_len = BIO_read(bio, buffer, length);

    BIO_free_all(bio);
    return buffer;
}

/* ── AES-128-CBC Sifrele ───────────────────────────────────── */
char* aes_encrypt(const char* plaintext) {
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return NULL;

    EVP_EncryptInit_ex(ctx, EVP_aes_128_cbc(), NULL, AES_KEY, AES_IV);

    int plain_len = strlen(plaintext);
    int max_len = plain_len + EVP_MAX_BLOCK_LENGTH;
    unsigned char* ciphertext = (unsigned char*)malloc(max_len);

    int len, cipher_len;
    EVP_EncryptUpdate(ctx, ciphertext, &len, (const unsigned char*)plaintext, plain_len);
    cipher_len = len;
    EVP_EncryptFinal_ex(ctx, ciphertext + len, &len);
    cipher_len += len;

    EVP_CIPHER_CTX_free(ctx);

    char* result = base64_encode(ciphertext, cipher_len);
    free(ciphertext);
    return result;
}

/* ── AES-128-CBC Sifre Coz ─────────────────────────────────── */
char* aes_decrypt(const char* base64_ciphertext) {
    int cipher_len;
    unsigned char* ciphertext = base64_decode(base64_ciphertext, &cipher_len);
    if (cipher_len <= 0) {
        free(ciphertext);
        return NULL;
    }

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (!ctx) { free(ciphertext); return NULL; }

    EVP_DecryptInit_ex(ctx, EVP_aes_128_cbc(), NULL, AES_KEY, AES_IV);

    unsigned char* plaintext = (unsigned char*)malloc(cipher_len + 1);
    int len, plain_len;
    EVP_DecryptUpdate(ctx, plaintext, &len, ciphertext, cipher_len);
    plain_len = len;
    EVP_DecryptFinal_ex(ctx, plaintext + len, &len);
    plain_len += len;

    EVP_CIPHER_CTX_free(ctx);
    free(ciphertext);

    plaintext[plain_len] = '\0';
    return (char*)plaintext;
}
