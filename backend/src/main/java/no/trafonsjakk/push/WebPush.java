package no.trafonsjakk.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The cryptography of Web Push, with the JDK only: the message is encrypted for the browser's subscription
 * (RFC 8291, "aes128gcm" content coding from RFC 8188), and the request carries a VAPID token (RFC 8292) signed
 * with our private key, so the push service knows it's us. Keys are in the usual format: base64url, the public
 * key as an uncompressed P-256 point (65 bytes), the private key as the 32-byte scalar.
 */
public final class WebPush {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder UNB64 = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    static final ECParameterSpec P256 = p256();

    private WebPush() {
    }

    public static byte[] decode(String base64url) {
        return UNB64.decode(base64url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String encode(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    public static ECPublicKey publicKey(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 4) {
            throw new GeneralSecurityException("not an uncompressed P-256 point");
        }
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)), new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256));
    }

    public static ECPrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        // 32 bytes, though some tools drop leading zero bytes
        if (scalar.length == 0 || scalar.length > 32) {
            throw new GeneralSecurityException("not a P-256 private key");
        }
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
    }

    public static byte[] point(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    /** Whether the private key belongs to the public key (a misconfiguration check at startup). */
    public static boolean matches(ECPrivateKey priv, ECPublicKey pub) throws GeneralSecurityException {
        byte[] data = "trafonsjakk".getBytes(StandardCharsets.UTF_8);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(priv);
        s.update(data);
        byte[] sig = s.sign();
        Signature v = Signature.getInstance("SHA256withECDSA");
        v.initVerify(pub);
        v.update(data);
        return v.verify(sig);
    }

    /** The encrypted request body for one subscription ({@code p256dh} and {@code auth} from the browser). */
    public static byte[] encrypt(byte[] payload, byte[] p256dh, byte[] auth) throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        KeyPair ephemeral = gen.generateKeyPair();
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, p256dh, auth, ephemeral, salt);
    }

    static byte[] encrypt(byte[] payload, byte[] p256dh, byte[] auth, KeyPair ephemeral, byte[] salt) throws GeneralSecurityException {
        ECPublicKey ua = publicKey(p256dh);
        byte[] asPublic = point((ECPublicKey) ephemeral.getPublic());
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeral.getPrivate());
        ka.doPhase(ua, true);
        byte[] shared = ka.generateSecret();

        byte[] prkKey = hmac(auth, shared);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), p256dh, asPublic, new byte[] {1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[] {1})), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // One record: the payload, then the delimiter for the last record (no padding)
        byte[] cipher = aes.doFinal(concat(payload, new byte[] {2}));

        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + cipher.length)
                .put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(cipher).array();
    }

    /** The Authorization header value: {@code vapid t=<JWT>, k=<public key>}. */
    public static String vapidHeader(String audience, String subject, ECPrivateKey key, byte[] publicPoint, Instant now)
            throws GeneralSecurityException {
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encode(("{\"aud\":\"" + audience + "\",\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond()
                + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(key);
        s.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + header + "." + claims + "." + encode(s.sign()) + ", k=" + encode(publicPoint);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        ByteBuffer b = ByteBuffer.allocate(n);
        for (byte[] p : parts) {
            b.put(p);
        }
        return b.array();
    }

    private static void copy32(BigInteger v, byte[] out, int at) {
        byte[] raw = v.toByteArray();
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, out, at + 32 - len, len);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
