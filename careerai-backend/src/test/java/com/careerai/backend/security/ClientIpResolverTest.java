package com.careerai.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remote, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void withoutTrustedProxiesTheHeaderIsNeverUsed() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        assertThat(resolver.resolve(request("203.0.113.5", "1.2.3.4")).ip()).isEqualTo("203.0.113.5");
        assertThat(resolver.resolve(request("203.0.113.5", null)).ip()).isEqualTo("203.0.113.5");
    }

    @Test
    void anUnconfiguredPrivateProxyIsFlaggedSoPerIpLimitsDoNotHitEveryone() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        ClientIpResolver.ClientAddress address = resolver.resolve(request("10.0.0.7", "203.0.113.9"));
        assertThat(address.ip()).isEqualTo("10.0.0.7");
        assertThat(address.untrustedProxy()).isTrue();
        assertThat(resolver.resolve(request("10.0.0.7", null)).untrustedProxy()).isFalse();
    }

    @Test
    void trustedProxyHopsAreSkippedFromTheRight() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8", "192.168.1.10"));
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9")).ip()).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, 10.2.3.4")).ip()).isEqualTo("203.0.113.9");
        // Spoofed left-most value: the right-most untrusted hop wins.
        assertThat(resolver.resolve(request("10.0.0.5", "1.1.1.1, 203.0.113.9")).ip()).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("192.168.1.10", "203.0.113.9")).ip()).isEqualTo("203.0.113.9");
        // 192.168.1.11 is not trusted: its header is ignored.
        assertThat(resolver.resolve(request("192.168.1.11", "203.0.113.9")).ip()).isEqualTo("192.168.1.11");
    }

    @Test
    void invalidHeaderValuesNeverBecomeTheClientAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertThat(resolver.resolve(request("10.0.0.5", "evil.example.com")).ip()).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "999.1.1.1")).ip()).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "203.0.113.9, not-an-ip")).ip()).isEqualTo("10.0.0.5");
        assertThat(resolver.resolve(request("10.0.0.5", "")).ip()).isEqualTo("10.0.0.5");
    }

    @Test
    void ipv6AddressesAndRangesWork() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("fd00::/8"));
        assertThat(resolver.resolve(request("fd12::1", "2001:db8::42")).ip()).isEqualTo("2001:db8::42");
        assertThat(resolver.resolve(request("2001:db8::1", "2001:db8::42")).ip()).isEqualTo("2001:db8::1");
    }

    @Test
    void badConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> new ClientIpResolver(List.of("not-an-ip"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ClientIpResolver(List.of("10.0.0.0/40"))).isInstanceOf(IllegalStateException.class);
    }
}
