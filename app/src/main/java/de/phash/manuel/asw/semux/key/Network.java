/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package de.phash.manuel.asw.semux.key;

import java.util.HashMap;
import java.util.Map;

public enum Network {

    MAINNET((byte) 0, "MAINNET"),

    TESTNET((byte) 1, "TESTNET"),

    DEVNET((byte) 2, "DEVNET");

    Network(byte id, String label) {
        this.id = id;
        this.label = label;
    }

    private final byte id;
    private final String label;

    private static final Map<String, Network> labels = new HashMap<>();
    private static final Map<Byte, Network> ids = new HashMap<>();

    static {
        for (Network net : Network.values()) {
            labels.put(net.label, net);
            ids.put(net.id, net);
        }
    }

    public byte id() {
        return id;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    public static Network of(byte networkId) {
        return ids.get(networkId);
    }

    public static Network of(String label) {
        return labels.get(label);
    }
}
