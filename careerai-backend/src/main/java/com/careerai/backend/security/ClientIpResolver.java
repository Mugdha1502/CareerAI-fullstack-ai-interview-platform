package com.careerai.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Works out the client IP for rate limiting without trusting what the client says.
 *
 * X-Forwarded-For is only read when the direct peer (request.getRemoteAddr()) is one of the
 * configured trusted proxies (app.security.trusted-proxies, comma-separated IPs or CIDR ranges).
 * The header is then read from the right, skipping trusted proxy hops, and the first address that
 * is not a trusted proxy is the client — so a value the client typed at the left end is ignored.
 * With no trusted proxies configured (the default), the header is never used.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

    /** Result: the client address, and whether it is really just an unconfigured proxy's address. */
    public record ClientAddress(String ip, boolean untrustedProxy) {
    }

    private record Cidr(byte[] network, int prefix) {
    }

    private final List<Cidr> trustedProxies = new ArrayList<>();
    private final AtomicBoolean proxyWarningLogged = new AtomicBoolean();

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") List<String> trustedProxies) {
        for (String entry : trustedProxies) {
            if (entry == null || entry.isBlank()) continue;
            this.trustedProxies.add(parseCidr(entry.trim()));
        }
    }

    public ClientAddress resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        String forwarded = request.getHeader("X-Forwarded-For");

        if (remote == null) return new ClientAddress("unknown", false);
        if (!isTrusted(remote)) {
            // A forwarding header from a private/loopback peer usually means a reverse proxy that is
            // not configured as trusted: every user would appear to share the proxy's address.
            boolean behindProxy = forwarded != null && !forwarded.isBlank() && isPrivate(remote);
            if (behindProxy && proxyWarningLogged.compareAndSet(false, true)) {
                log.warn("Requests arrive through a proxy at {}, but app.security.trusted-proxies (TRUSTED_PROXIES) is not set; "
                    + "X-Forwarded-For is ignored and per-IP login limits are disabled for proxied requests.", remote);
            }
            return new ClientAddress(remote, behindProxy);
        }
        if (forwarded == null || forwarded.isBlank()) return new ClientAddress(remote, false);

        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalize(hops[i]);
            if (hop == null) break; // not an IP literal: stop rather than trust anything further left
            if (!isTrusted(hop)) return new ClientAddress(hop, false);
        }
        return new ClientAddress(remote, false);
    }

    private boolean isTrusted(String ip) {
        byte[] address = toBytes(ip);
        if (address == null) return false;
        for (Cidr cidr : trustedProxies) {
            if (matches(cidr, address)) return true;
        }
        return false;
    }

    private static boolean isPrivate(String ip) {
        try {
            InetAddress address = InetAddress.getByAddress(toBytes(ip));
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc); // IPv6 ULA fc00::/7
        } catch (UnknownHostException | NullPointerException ex) {
            return false;
        }
    }

    private static boolean matches(Cidr cidr, byte[] address) {
        if (cidr.network().length != address.length) return false;
        int fullBytes = cidr.prefix() / 8;
        int remainingBits = cidr.prefix() % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (cidr.network()[i] != address[i]) return false;
        }
        if (remainingBits == 0) return true;
        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return (cidr.network()[fullBytes] & mask) == (address[fullBytes] & mask);
    }

    private static Cidr parseCidr(String entry) {
        String[] parts = entry.split("/", 2);
        byte[] network = toBytes(normalize(parts[0]));
        if (network == null) {
            throw new IllegalStateException("app.security.trusted-proxies contains an invalid address: " + entry);
        }
        int prefix = network.length * 8;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                prefix = -1;
            }
            if (prefix < 0 || prefix > network.length * 8) {
                throw new IllegalStateException("app.security.trusted-proxies contains an invalid prefix: " + entry);
            }
        }
        return new Cidr(network, prefix);
    }

    /** Returns the trimmed IP literal, or null when the value is not an IPv4/IPv6 literal (no DNS lookups). */
    static String normalize(String value) {
        if (value == null) return null;
        String ip = value.trim();
        if (ip.startsWith("[") && ip.endsWith("]")) ip = ip.substring(1, ip.length() - 1);
        return toBytes(ip) == null ? null : ip;
    }

    private static byte[] toBytes(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        var v4 = IPV4.matcher(ip);
        if (v4.matches()) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int part = Integer.parseInt(v4.group(i + 1));
                if (part > 255) return null;
                bytes[i] = (byte) part;
            }
            return bytes;
        }
        if (ip.contains(":") && IPV6.matcher(ip).matches()) {
            try {
                return InetAddress.getByName(ip).getAddress(); // literal only (validated above), so no DNS
            } catch (UnknownHostException ex) {
                return null;
            }
        }
        return null;
    }
}
