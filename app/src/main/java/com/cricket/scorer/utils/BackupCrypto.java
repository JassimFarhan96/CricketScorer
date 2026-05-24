package com.cricket.scorer.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * BackupCrypto.java
 *
 * AES-256-GCM encryption used for Cricket Scorer backup files.
 *
 * File format produced by encryptToFile() and consumed by decryptToStream():
 *
 *   ┌──────────────────────┐
 *   │ 8 bytes  "CSCBAK01"  │  ← magic header (Cricket Scorer Backup v1)
 *   ├──────────────────────┤
 *   │ 12 bytes random IV   │  ← unique per backup
 *   ├──────────────────────┤
 *   │ N bytes AES-GCM      │  ← ciphertext + 16-byte GCM auth tag
 *   │ encrypted zip stream │
 *   └──────────────────────┘
 *
 * GCM provides both confidentiality and authenticity — a tampered backup
 * fails to decrypt with a GeneralSecurityException instead of producing
 * garbage data. The IV is random per backup so identical inputs always
 * produce different ciphertext.
 *
 * Key derivation: a fixed application-level secret is hashed (SHA-256) to
 * produce the 256-bit AES key. This keeps backups portable — any install
 * of the app on any device can read any backup made by any other install.
 *
 * Security note: the key is embedded in the APK. A determined attacker
 * can recover it via reverse engineering. This level of protection is
 * adequate for the stated goal — making the file unreadable to the
 * casual end user — but is NOT a substitute for proper credential
 * encryption (which would require a user-supplied password).
 */
public final class BackupCrypto {

    private BackupCrypto() {}

    static final byte[] MAGIC = new byte[]{'C', 'S', 'C', 'B', 'A', 'K', '0', '1'};
    private static final int IV_BYTES        = 12;     // GCM standard
    private static final int GCM_TAG_BITS    = 128;    // GCM auth tag length
    private static final String TRANSFORM    = "AES/GCM/NoPadding";

    /**
     * Application-level secret. Hashed to derive the 256-bit AES key.
     * If you change this string in a future release, OLD BACKUPS WILL NO
     * LONGER DECRYPT — only do so intentionally with migration handling.
     */
    private static final String APP_SECRET =
            "CricketScorerBackup::v1::" +
            "do-not-edit-this-string-or-old-backups-will-break-x47p9q";

    /** Encrypts `plainZip` → `outFile` following the format above. */
    public static void encryptToFile(File plainZip, File outFile) throws IOException {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);

        try (BufferedOutputStream raw = new BufferedOutputStream(new FileOutputStream(outFile))) {
            // Header
            raw.write(MAGIC);
            raw.write(iv);
            raw.flush();

            // Ciphertext
            Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, iv);
            try (CipherOutputStream cos = new CipherOutputStream(raw, cipher);
                 BufferedInputStream bis = new BufferedInputStream(new FileInputStream(plainZip))) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = bis.read(buf)) != -1) {
                    cos.write(buf, 0, read);
                }
                // CipherOutputStream.close() writes the final GCM tag.
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts an encrypted backup InputStream into `out` (which will
     * typically be a FileOutputStream to a temp file). The input stream
     * is expected to start with the magic header + IV, followed by the
     * AES-GCM ciphertext.
     *
     * @throws IOException if the input is not a valid Cricket Scorer
     *         backup (bad magic) or the ciphertext fails authentication.
     */
    public static void decryptToStream(InputStream in, OutputStream out) throws IOException {
        // Header
        byte[] magic = new byte[MAGIC.length];
        int readMagic = readFully(in, magic);
        if (readMagic != MAGIC.length || !equalBytes(magic, MAGIC)) {
            throw new IOException("Not a Cricket Scorer backup file — missing magic header.");
        }
        byte[] iv = new byte[IV_BYTES];
        int readIv = readFully(in, iv);
        if (readIv != IV_BYTES) {
            throw new IOException("Backup file truncated — IV missing.");
        }

        try {
            Cipher cipher = newCipher(Cipher.DECRYPT_MODE, iv);
            try (CipherInputStream cis = new CipherInputStream(in, cipher)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = cis.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
                // GeneralSecurityException is thrown lazily on read() if the GCM
                // tag doesn't verify — wrapped by CipherInputStream as IOException.
            }
        } catch (GeneralSecurityException e) {
            throw new IOException(
                    "Backup is corrupt or was created by a different app version: "
                            + e.getMessage(), e);
        }
    }

    /** Convenience: decrypt directly to a file. */
    public static void decryptToFile(InputStream in, File outFile) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
            decryptToStream(in, bos);
        }
    }

    /** Build a Cipher initialised for encrypt or decrypt with the given IV. */
    private static Cipher newCipher(int mode, byte[] iv) throws GeneralSecurityException {
        byte[] keyBytes = deriveKey();
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    /** SHA-256(APP_SECRET) → 32-byte AES-256 key. */
    private static byte[] deriveKey() throws GeneralSecurityException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(APP_SECRET.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new GeneralSecurityException("Key derivation failed", e);
        }
    }

    /** Fully reads `dst.length` bytes (or returns actual count if EOF early). */
    private static int readFully(InputStream in, byte[] dst) throws IOException {
        int total = 0;
        while (total < dst.length) {
            int n = in.read(dst, total, dst.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    /** Read a full stream into a byte array (used only for small headers). */
    static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
