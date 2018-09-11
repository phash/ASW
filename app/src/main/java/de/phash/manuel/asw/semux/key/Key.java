/**
 * Copyright (c) 2017-2018 The Semux Developers
 * <p>
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package de.phash.manuel.asw.semux.key;


import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/**
 * Represents a key pair for the ED25519 signature algorithm.
 * <p>
 * Public key is encoded in "X.509"; private key is encoded in "PKCS#8".
 */
public class Key {

    public static final int PUBLIC_KEY_LEN = 44;
    public static final int PRIVATE_KEY_LEN = 48;
    public static final int ADDRESS_LEN = 20;


    private static final KeyPairGenerator gen = new KeyPairGenerator();

    static {
        /*
         * Algorithm specifications
         *
         * Name: Ed25519
         *
         * Curve: ed25519curve
         *
         * H: SHA-512
         *
         * l: $q = 2^{252} + 27742317777372353535851937790883648493$
         *
         * B: 0x5866666666666666666666666666666666666666666666666666666666666666
         */
        try {
            EdDSANamedCurveSpec params = EdDSANamedCurveTable.getByName("Ed25519");
            gen.initialize(params, new SecureRandom());
        } catch (InvalidAlgorithmParameterException e) {
        }
    }

    protected EdDSAPrivateKey sk;
    protected EdDSAPublicKey pk;

    /**
     * Creates a random ED25519 key pair.
     */
    public Key() {
        KeyPair keypair = gen.generateKeyPair();
        sk = (EdDSAPrivateKey) keypair.getPrivate();
        pk = (EdDSAPublicKey) keypair.getPublic();
    }

    /**
     * Creates an ED25519 key pair with a specified private key
     *
     * @param privateKey
     *            the private key in "PKCS#8" format
     * @throws InvalidKeySpecException
     */
    public Key(byte[] privateKey) throws InvalidKeySpecException {
        this.sk = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(privateKey));
        this.pk = new EdDSAPublicKey(new EdDSAPublicKeySpec(sk.getA(), sk.getParams()));
    }

    /**
     * Creates an ED25519 key pair with the specified public and private keys.
     *
     * @param privateKey
     *            the private key in "PKCS#8" format
     * @param publicKey
     *            the public key in "X.509" format, for verification purpose only
     *
     * @throws InvalidKeySpecException
     */
    public Key(byte[] privateKey, byte[] publicKey) throws InvalidKeySpecException {
        this(privateKey);

        if (!Arrays.equals(getPublicKey(), publicKey)) {
            throw new InvalidKeySpecException("Public key and private key do not match!");
        }
    }

    /**
     * Verifies a signature.
     *
     * @param message
     *            message
     * @param signature
     *            signature
     * @return True if the signature is valid, otherwise false
     */
    public static boolean verify(byte[] message, Signature signature) {
        if (message != null && signature != null) { // avoid null pointer exception
            try {
             /*   if (Native.isEnabled()) {
                    return SodiumLibrary.cryptoAuthVerify(message, signature.getS(), signature.getA());
                } else {*/
                EdDSAEngine engine = new EdDSAEngine();
                engine.initVerify(PublicKeyCache.computeIfAbsent(signature.getPublicKey()));

                return engine.verifyOneShot(message, signature.getS());
                //}
            } catch (Exception e) {
                // do nothing
            }
        }

        return false;
    }

    /**
     * Verifies a signature.
     *
     * @param message
     *            message hash
     * @param signature
     *            signature
     * @return True if the signature is valid, otherwise false
     */
    public static boolean verify(byte[] message, byte[] signature) {
        Signature sig = Signature.fromBytes(signature);

        return verify(message, sig);
    }

    /**
     * Returns the private key, encoded in "PKCS#8".
     */
    public byte[] getPrivateKey() {
        return sk.getEncoded();
    }

    /**
     * Returns the public key, encoded in "X.509".
     *
     * @return
     */
    public byte[] getPublicKey() {
        return pk.getEncoded();
    }

    /**
     * Returns the Semux address.
     */
    public byte[] toAddress() {
        return Hash.h160(getPublicKey());
    }

    /**
     * Returns the Semux address in {@link String}.
     */
    public String toAddressString() {
        return Hex.encode(toAddress());
    }

    /**
     * Signs a message.
     *
     * @param message
     *            message
     * @return
     */
    public Signature sign(byte[] message) {
        try {
            byte[] sig;
       /*     if (Native.isEnabled()) {
                sig = Native.sign(message, Bytes.merge(sk.getSeed(), sk.getAbyte()));
            } else {*/
            EdDSAEngine engine = new EdDSAEngine();
            engine.initSign(sk);
            sig = engine.signOneShot(message);
            //}

            return new Signature(sig, pk.getAbyte());
        } catch (InvalidKeyException | SignatureException e) {
            throw new CryptoException(e);
        }
    }

    /**
     * Returns a string representation of this key.
     *
     * @return the address of this EdDSA.
     */
    @Override
    public String toString() {
        return toAddressString();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getPrivateKey());
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Key) && Arrays.equals(getPrivateKey(), ((Key) obj).getPrivateKey());
    }

    /**
     * Represents an EdDSA signature, wrapping the raw signature and public key.
     *
     */
    public static class Signature {
        public static final int LENGTH = 96;

        private static final byte[] X509 = Hex.decode("302a300506032b6570032100");
        private static final int S_LEN = 64;
        private static final int A_LEN = 32;

        private byte[] s;
        private byte[] a;

        /**
         * Creates a Signature instance.
         *
         * @param s
         * @param a
         */
        public Signature(byte[] s, byte[] a) {
            if (s == null || s.length != S_LEN)

                throw new IllegalArgumentException("Invalid S");
            if (a == null || a.length != A_LEN) {
                throw new IllegalArgumentException("Invalid A");
            }
            this.s = s;
            this.a = a;
        }

        /**
         * Parses from byte array.
         *
         * @param bytes
         * @return a {@link Signature} if success,or null
         */
        public static Signature fromBytes(byte[] bytes) {
            if (bytes == null || bytes.length != LENGTH) {
                return null;
            }

            byte[] s = Arrays.copyOfRange(bytes, 0, S_LEN);
            byte[] a = Arrays.copyOfRange(bytes, LENGTH - A_LEN, LENGTH);

            return new Signature(s, a);
        }

        /**
         * Returns the S byte array.
         *
         * @return
         */
        public byte[] getS() {
            return s;
        }

        /**
         * Returns the A byte array.
         *
         * @return
         */
        public byte[] getA() {
            return a;
        }

        /**
         * Returns the public key of the signer.
         *
         * @return
         */
        public byte[] getPublicKey() {
            return Bytes.merge(X509, a);
        }

        /**
         * Returns the address of signer.
         *
         * @return
         */
        public byte[] getAddress() {
            return Hash.h160(getPublicKey());
        }

        /**
         * Converts into a byte array.
         *
         * @return
         */
        public byte[] toBytes() {
            return Bytes.merge(s, a);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o == null || getClass() != o.getClass())
                return false;

            Signature signature = (Signature) o;

            return new EqualsBuilder()
                    .append(s, signature.s)
                    .append(a, signature.a)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(s)
                    .append(a)
                    .toHashCode();
        }
    }

}
