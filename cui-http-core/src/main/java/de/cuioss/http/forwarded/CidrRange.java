/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.http.forwarded;

import java.net.InetAddress;

/**
 * An immutable IPv4 or IPv6 CIDR range (or single host when no prefix is given), used to define
 * trusted-proxy hops for {@code X-Forwarded-For} client-IP resolution.
 *
 * <p>Matching is purely numeric — literals are parsed without DNS resolution — so an untrusted
 * hostname can never be interpreted as a trusted address.</p>
 */
final class CidrRange {

    private final byte[] network;
    private final int prefixBits;

    private CidrRange(byte[] network, int prefixBits) {
        this.network = network;
        this.prefixBits = prefixBits;
    }

    /**
     * Parses a CIDR spec such as {@code 10.0.0.0/8}, {@code 2001:db8::/32}, or a bare literal
     * {@code 192.168.1.1} (treated as a single host).
     *
     * @param spec the CIDR or IP literal
     * @return the parsed range
     * @throws IllegalArgumentException if the spec is not a valid IP literal / CIDR
     */
    static CidrRange parse(String spec) {
        String trimmed = spec.strip();
        int slash = trimmed.indexOf('/');
        String ipPart = slash < 0 ? trimmed : trimmed.substring(0, slash);
        InetAddress parsed = IpAddresses.parse(ipPart);
        if (parsed == null) {
            throw new IllegalArgumentException("Not an IP literal in trusted proxy: " + spec);
        }
        byte[] address = parsed.getAddress();
        int maxBits = address.length * 8;

        int prefix = maxBits;
        if (slash >= 0) {
            String prefixPart = trimmed.substring(slash + 1);
            try {
                prefix = Integer.parseInt(prefixPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix in trusted proxy: " + spec, e);
            }
            if (prefix < 0 || prefix > maxBits) {
                throw new IllegalArgumentException(
                        "CIDR prefix out of range [0.." + maxBits + "] in trusted proxy: " + spec);
            }
        }
        maskInPlace(address, prefix);
        return new CidrRange(address, prefix);
    }

    /**
     * @return {@code true} when {@code candidate} falls within this range (same address family
     *         and matching network prefix)
     */
    boolean contains(InetAddress candidate) {
        byte[] other = candidate.getAddress();
        if (other.length != network.length) {
            return false;
        }
        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (network[i] != other[i]) {
                return false;
            }
        }
        int remainingBits = prefixBits % 8;
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits) & 0xFF;
            return (network[fullBytes] & mask) == (other[fullBytes] & mask);
        }
        return true;
    }

    private static void maskInPlace(byte[] address, int prefixBits) {
        for (int i = 0; i < address.length; i++) {
            int bitStart = i * 8;
            if (bitStart >= prefixBits) {
                address[i] = 0;
            } else if (bitStart + 8 > prefixBits) {
                int keep = prefixBits - bitStart;
                int mask = 0xFF << (8 - keep) & 0xFF;
                address[i] = (byte) (address[i] & mask);
            }
        }
    }
}
