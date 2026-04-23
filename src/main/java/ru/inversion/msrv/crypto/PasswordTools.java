package ru.inversion.msrv.crypto;

import ru.inversion.utils.Checks;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

public final class PasswordTools {

    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KEY_ALGO = "AES";

    private static final int ITER = 100_000;
    private static final int KEY_BITS = 256;
    private static final int TAG_BITS = 128;
    private static final int NONCE_LEN = 12;

    private PasswordTools() {
    }

    /**
     * Шифрует plainPassword ключом, производным от clientPassword и login.
     */
    public static PasswordContainer encrypt( char[] clientPassword, String login, char[] plainPassword )
    {
        Checks.Require.chars( clientPassword,"clientPassword" );
        Checks.Require.text ( login,         "login" );
        Checks.Require.chars( plainPassword, "plainPassword" );

        byte[] salt = null;
        byte[] keyBytes = null;
        byte[] plainBytes = null;
        byte[] ciphertext = null;

        try {

            salt     = buildSaltFromLogin(login);
            keyBytes = deriveKey(clientPassword, salt);

            byte[] nonce = new byte[NONCE_LEN];
            new SecureRandom().nextBytes(nonce);

            plainBytes = utf8(plainPassword);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, KEY_ALGO), new GCMParameterSpec(TAG_BITS, nonce));

            ciphertext = cipher.doFinal(plainBytes);

            return new PasswordContainer( nonce, ciphertext );

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt password container", ex);
        } finally {
            wipe(salt);
            wipe(keyBytes);
            wipe(plainBytes);
            wipe(ciphertext);
        }
    }

    /**
     * Расшифровывает passwordContainer обратно в пароль target DB.
     */
    public static char[] decrypt(char[] clientPassword, String login, PasswordContainer container) {
        Checks.Require.chars(clientPassword, "clientPassword");
        Checks.Require.text (login, "login");

        if( container == null )
            throw new IllegalArgumentException("container is null");

        byte[] salt = null;
        byte[] keyBytes = null;
        byte[] plainBytes = null;

        try {
            salt = buildSaltFromLogin(login);
            keyBytes = deriveKey(clientPassword, salt);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init( Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, KEY_ALGO), new GCMParameterSpec(TAG_BITS, container.getNonce()) );

            plainBytes = cipher.doFinal(container.getCiphertext());

            return utf8ToChars( plainBytes );

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt password container", ex);
        } finally {
            wipe(salt);
            wipe(keyBytes);
            wipe(plainBytes);
        }
    }

    private static byte[] deriveKey(char[] clientPassword, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(clientPassword, salt, ITER, KEY_BITS);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF);
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * salt = SHA-256( UPPER(login, ROOT) в UTF-8 )
     */
    private static byte[] buildSaltFromLogin(String login) throws Exception {
        byte[] loginBytes = null;
        try {
            loginBytes = login.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(loginBytes);
        } finally {
            wipe(loginBytes);
        }
    }

    /** */
    private static byte[] utf8( char[] chars )
    {
        ByteBuffer bb = StandardCharsets.UTF_8.encode( CharBuffer.wrap(chars) );
        byte[] out = new byte[bb.remaining()];
        bb.get(out);
        return out;
    }

    /** */
    private static char[] utf8ToChars(byte[] bytes) {
        CharBuffer cb = StandardCharsets.UTF_8.decode( ByteBuffer.wrap(bytes) );
        char[] out = new char[cb.remaining()];
        cb.get(out);
        return out;
    }

    /** */
    private static void wipe(byte[] bytes) {
        if( bytes != null )
            Arrays.fill( bytes, (byte)0 );
    }

    /** */
    public static final class PasswordContainer {
        private final byte[] nonce;
        private final byte[] ciphertext;

        public PasswordContainer( byte[] nonce, byte[] ciphertext )
        {
            this.nonce      = Arrays.copyOf( Checks.Require.bytes( nonce, "nonce" ), nonce.length );
            this.ciphertext = Arrays.copyOf( Checks.Require.bytes( ciphertext, "ciphertext" ), ciphertext.length );
        }

        public static PasswordContainer fromBase64( String nonceBase64, String ciphertextBase64 ) {
            if( nonceBase64 == null || nonceBase64.trim().isEmpty() )
                throw new IllegalArgumentException("nonceBase64 is blank");

            if( ciphertextBase64 == null || ciphertextBase64.trim().isEmpty() )
                throw new IllegalArgumentException("ciphertextBase64 is blank");

            return new PasswordContainer( Base64.getDecoder().decode(nonceBase64), Base64.getDecoder().decode(ciphertextBase64) );
        }

        public byte[] getNonce() {
            return Arrays.copyOf(nonce, nonce.length);
        }

        public byte[] getCiphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }

        public String nonceBase64() {
            return Base64.getEncoder().encodeToString(nonce);
        }

        public String ciphertextBase64() {
            return Base64.getEncoder().encodeToString(ciphertext);
        }
    }
}