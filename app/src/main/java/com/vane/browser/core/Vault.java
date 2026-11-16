package com.vane.browser.core;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 敏感字段加密：密钥放在 Android Keystore（不可导出），AES-GCM 加密后存 Base64。
 * 换手机/清数据会解不开——这是设计目标，不是 bug。
 */
public final class Vault {

    private static final String ALIAS = "vane_secret_v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";

    private Vault() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return (SecretKey) ks.getKey(ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    /** 返回 "base64(iv):base64(密文)"，失败返回 null。 */
    public static String encrypt(Context c, String plain) {
        if (plain == null) return null;
        try {
            Cipher ci = Cipher.getInstance(TRANSFORM);
            ci.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = ci.getIV();
            byte[] ct = ci.doFinal(plain.getBytes("UTF-8"));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String decrypt(Context c, String stored) {
        if (stored == null || !stored.contains(":")) return null;
        try {
            String[] parts = stored.split(":", 2);
            Cipher ci = Cipher.getInstance(TRANSFORM);
            ci.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(ci.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }
}
