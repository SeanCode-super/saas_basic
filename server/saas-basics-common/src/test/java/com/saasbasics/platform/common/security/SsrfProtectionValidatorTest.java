package com.saasbasics.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saasbasics.platform.common.exception.BizException;
import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SsrfProtectionValidatorTest {

    @Test
    void rejectsDisallowedSchemes() {
        BizException ex1 = assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("file:///etc/passwd", false));
        assertEquals("SSRF_INVALID_SCHEME", ex1.getCode());

        BizException ex2 = assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("gopher://127.0.0.1:6379/_flushall", false));
        assertEquals("SSRF_INVALID_SCHEME", ex2.getCode());

        BizException ex3 = assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("ftp://example.com/test.txt", false));
        assertEquals("SSRF_INVALID_SCHEME", ex3.getCode());
    }

    @Test
    void rejectsPrivateAndLoopbackIpsInProduction() {
        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://127.0.0.1:8080/api", false));

        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://localhost:9000", false));

        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://192.168.1.100/token", false));

        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://10.0.0.1/sso", false));

        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://172.20.10.5:8000/callback", false));

        // AWS/GCP Metadata
        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://169.254.169.254/latest/meta-data/", false));

        assertThrows(BizException.class, () ->
                SsrfProtectionValidator.validateOutboundUri("http://metadata.google.internal/computeMetadata/v1/", false));
    }

    @Test
    void allowsPrivateInDevMode() {
        assertDoesNotThrow(() ->
                SsrfProtectionValidator.validateOutboundUri("http://127.0.0.1:9000/test-bucket", true));

        assertDoesNotThrow(() ->
                SsrfProtectionValidator.validateOutboundUri("http://localhost:8080/oauth2/token", true));
    }

    @Test
    void testIsPrivateOrReservedAddress() throws Exception {
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("10.1.2.3")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("172.16.5.4")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("192.168.0.1")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("169.254.169.254")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("100.64.0.1")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(SsrfProtectionValidator.isPrivateOrReservedAddress(InetAddress.getByName("::1")));
    }
}
