package com.vane.browser.core;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** 隐私空间文件加解密：AES-GCM 流式，边读边加/解密，不整块进内存。 */
public final class VaultFile {

    private static final String ALIAS = "vane_vault_file_v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";

    private VaultFile() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        javax.crypto.KeyGenerator kg =
                javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore");
        kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                        | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    /** 加密 src → dst（dst 前 12 字节是 IV）。 */
    public static boolean encrypt(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            out.write(iv);
            try (CipherOutputStream cos = new CipherOutputStream(out, c)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) cos.write(buf, 0, n);
                cos.flush();
            }
            return true;
        } catch (Throwable t) {
            dst.delete();
            return false;
        }
    }

    /** 解密 enc → dst。 */
    public static boolean decrypt(File enc, File dst) {
        try (FileInputStream in = new FileInputStream(enc)) {
            byte[] iv = new byte[12];
            if (in.read(iv) != 12) return false;
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            try (CipherInputStream cis = new CipherInputStream(in, c);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = cis.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
            }
            return true;
        } catch (Throwable t) {
            dst.delete();
            return false;
        }
    }
}
