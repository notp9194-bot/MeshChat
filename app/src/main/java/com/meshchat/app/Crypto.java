package com.meshchat.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * End-to-end encryption: EC P-256 key pair per device, ECDH shared secret -> SHA-256 -> AES-256-GCM.
 * User ID = derived from SHA-256(public key), so an ID can be verified against its key.
 */
public class Crypto {
    private static final String ALPHA = "abcdefghjkmnpqrstuvwxyz23456789";
    private static Crypto inst;

    public static synchronized Crypto get(Context c) {
        if (inst == null) inst = new Crypto(c.getApplicationContext());
        return inst;
    }

    private PrivateKey priv;
    public String pubB64, id;

    private Crypto(Context c) {
        SharedPreferences sp = c.getSharedPreferences("mesh_crypto", Context.MODE_PRIVATE);
        try {
            String pr = sp.getString("priv", null), pu = sp.getString("pub", null);
            KeyFactory kf = KeyFactory.getInstance("EC");
            if (pr == null || pu == null) {
                KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair kp = g.generateKeyPair();
                pr = Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP);
                pu = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
                sp.edit().putString("priv", pr).putString("pub", pu).apply();
            }
            priv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(pr, Base64.NO_WRAP)));
            pubB64 = pu;
            id = idFor(pu);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static String idFor(String pubB64) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(Base64.decode(pubB64, Base64.NO_WRAP));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(ALPHA.charAt((h[i] & 0xff) % ALPHA.length()));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private SecretKeySpec derive(String peerPub) throws Exception {
        PublicKey pk = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(peerPub, Base64.NO_WRAP)));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(priv);
        ka.doPhase(pk, true);
        byte[] key = MessageDigest.getInstance("SHA-256").digest(ka.generateSecret());
        return new SecretKeySpec(key, "AES");
    }

    public String encrypt(String peerPub, String plain) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, derive(peerPub), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    public String decrypt(String peerPub, String b64) throws Exception {
        byte[] in = Base64.decode(b64, Base64.NO_WRAP);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, derive(peerPub), new GCMParameterSpec(128, in, 0, 12));
        return new String(c.doFinal(in, 12, in.length - 12), "UTF-8");
    }
}
