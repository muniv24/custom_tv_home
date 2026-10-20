package com.ansub.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

// Runs shell commands through the TV's own network debugging (adbd on 127.0.0.1:5555), so the home
// screen can turn apps on and off without a computer. The first time, the TV asks "Allow debugging?"
// for this app's key; tick "Always allow". Blocking: call it off the main thread.
final class AdbShell {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f,
            OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000000, MAX_DATA = 4096;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    // PKCS#1 v1.5 DigestInfo for SHA-1: adbd signs its 20-byte token as if it were a SHA-1 digest.
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14};

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;

    // Connects and logs in. Waits up to waitMs for "Allow" to be pressed on a new key.
    static AdbShell connect(Context c, int waitMs) throws Exception {
        return new AdbShell(keys(c), waitMs);
    }

    private AdbShell(KeyPair keys, int waitMs) throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        socket.setSoTimeout(10000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes("UTF-8"));
        boolean signed = false;
        while (true) {
            int[] h = new int[6];
            byte[] data = read(h);
            if (h[0] == CNXN) return;
            if (h[0] != AUTH || h[1] != AUTH_TOKEN) throw new IOException("unexpected reply");
            if (!signed) {
                send(AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), data));
                signed = true;
            } else {
                // The TV doesn't know this key yet: offer it, and wait while someone presses Allow.
                String pub = Base64.encodeToString(pubKey((RSAPublicKey) keys.getPublic()), Base64.NO_WRAP);
                send(AUTH, AUTH_RSAPUBLICKEY, 0, (pub + " mksd@tv\0").getBytes("UTF-8"));
                socket.setSoTimeout(waitMs);
            }
        }
    }

    // Runs one command and returns what it printed.
    String run(String cmd) throws IOException {
        socket.setSoTimeout(15000);
        send(OPEN, 1, 0, ("shell:" + cmd + "\0").getBytes("UTF-8"));
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        int remote = 0;
        while (true) {
            int[] h = new int[6];
            byte[] data = read(h);
            if (h[0] == OKAY) remote = h[1];
            else if (h[0] == WRTE) { text.write(data); send(OKAY, 1, h[1], new byte[0]); }
            else if (h[0] == CLSE) { send(CLSE, 1, remote != 0 ? remote : h[1], new byte[0]); break; }
        }
        return text.toString("UTF-8");
    }

    void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd).put(data);
        out.write(b.array());
        out.flush();
    }

    private byte[] read(int[] h) throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 6; i++) h[i] = b.getInt();
        byte[] data = new byte[h[3]];
        in.readFully(data);
        return data;
    }

    private static byte[] sign(RSAPrivateKey key, byte[] token) throws Exception {
        int len = (key.getModulus().bitLength() + 7) / 8;
        byte[] block = new byte[len];
        int pad = len - 3 - SHA1_PREFIX.length - token.length;
        block[1] = 1;
        for (int i = 0; i < pad; i++) block[2 + i] = (byte) 0xff;
        System.arraycopy(SHA1_PREFIX, 0, block, 3 + pad, SHA1_PREFIX.length);
        System.arraycopy(token, 0, block, 3 + pad + SHA1_PREFIX.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(block);
    }

    // adbd's own public key layout: word count, n0inv, modulus and R^2 mod n little-endian, exponent.
    private static byte[] pubKey(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        int words = n.bitLength() / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        b.put(littleEndian(n, words * 4)).put(littleEndian(rr, words * 4));
        b.putInt(key.getPublicExponent().intValue());
        return b.array();
    }

    private static byte[] littleEndian(BigInteger v, int len) {
        byte[] big = v.toByteArray(), le = new byte[len];
        for (int i = 0; i < len && i < big.length; i++) le[i] = big[big.length - 1 - i];
        return le;
    }

    // One key per install, kept in the app's private files.
    private static KeyPair keys(Context c) throws Exception {
        File priv = new File(c.getFilesDir(), "adbkey"), pub = new File(c.getFilesDir(), "adbkey.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair k = g.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(priv)) { o.write(k.getPrivate().getEncoded()); }
        try (FileOutputStream o = new FileOutputStream(pub)) { o.write(k.getPublic().getEncoded()); }
        return k;
    }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream i = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            new DataInputStream(i).readFully(b);
            return b;
        }
    }
}
