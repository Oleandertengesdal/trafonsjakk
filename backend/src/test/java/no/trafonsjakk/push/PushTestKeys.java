package no.trafonsjakk.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** A pretend browser for tests: its keys, and decrypting what the server sends it (RFC 8291, the other side). */
public final class PushTestKeys {

    public final KeyPair keys;
    public final byte[] auth = new byte[16];

    public PushTestKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            keys = gen.generateKeyPair();
            new java.security.SecureRandom().nextBytes(auth);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String p256dh() {
        return WebPush.encode(WebPush.point((ECPublicKey) keys.getPublic()));
    }

    public String authKey() {
        return WebPush.encode(auth);
    }

    /** A VAPID key pair as the server reads it from its environment: {public, private}. */
    public static String[] vapidPair() {
        PushTestKeys k = new PushTestKeys();
        byte[] d = ((ECPrivateKey) k.keys.getPrivate()).getS().toByteArray();
        byte[] scalar = new byte[32];
        int len = Math.min(d.length, 32);
        System.arraycopy(d, d.length - len, scalar, 32 - len, len);
        return new String[] {k.p256dh(), WebPush.encode(scalar)};
    }

    public String decrypt(byte[] body) throws GeneralSecurityException {
        ByteBuffer b = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        b.get(salt);
        int rs = b.getInt();
        if (rs != 4096) {
            throw new GeneralSecurityException("record size " + rs);
        }
        byte[] asPublic = new byte[b.get() & 0xff];
        b.get(asPublic);
        byte[] cipher = new byte[b.remaining()];
        b.get(cipher);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(keys.getPrivate());
        ka.doPhase(WebPush.publicKey(asPublic), true);
        byte[] shared = ka.generateSecret();
        byte[] uaPublic = WebPush.point((ECPublicKey) keys.getPublic());
        byte[] prkKey = WebPush.hmac(auth, shared);
        byte[] ikm = WebPush.hmac(prkKey, WebPush.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[] {1}));
        byte[] prk = WebPush.hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(WebPush.hmac(prk, WebPush.concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(WebPush.hmac(prk, WebPush.concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = aes.doFinal(cipher);
        if (plain[plain.length - 1] != 2) {
            throw new GeneralSecurityException("not the last record");
        }
        return new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8);
    }

    /** Checks a VAPID Authorization header against the public key; returns the token's claims (JSON). */
    public static String verifyVapid(String header, String publicKey) throws GeneralSecurityException {
        String t = header.substring(header.indexOf("t=") + 2, header.indexOf(','));
        String k = header.substring(header.indexOf("k=") + 2).trim();
        if (!k.equals(publicKey)) {
            throw new GeneralSecurityException("wrong k");
        }
        String[] parts = t.split("\\.");
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify(WebPush.publicKey(WebPush.decode(publicKey)));
        v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!v.verify(WebPush.decode(parts[2]))) {
            throw new GeneralSecurityException("bad signature");
        }
        return new String(WebPush.decode(parts[1]), StandardCharsets.UTF_8);
    }
}
