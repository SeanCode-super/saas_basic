package com.saasbasics.platform.common.security;

import com.saasbasics.platform.common.exception.BizException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF (Server-Side Request Forgery) 安全防护校验器。
 * 校验第三方集成（SSO、对象存储端点、Webhook 等）发起的外部 HTTP/HTTPS 请求，
 * 阻断向私有网络（RFC 1918）、环回地址、链路本地/云厂商元数据（169.254.169.254）以及保留网段的外联请求。
 */
public final class SsrfProtectionValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata.google.internal",
            "instance-data"
    );

    private SsrfProtectionValidator() {
    }

    /**
     * 校验出站 URI 字符串。
     *
     * @param urlString           目标 URL 字符串
     * @param allowPrivateForDev 是否允许本地/私网（仅用于本地开发与单测模式）
     * @return 校验通过的 URI 实例
     */
    public static URI validateOutboundUri(String urlString, boolean allowPrivateForDev) {
        if (urlString == null || urlString.isBlank()) {
            throw new BizException("SSRF_INVALID_URL", "Outbound URL cannot be empty");
        }
        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (Exception ex) {
            throw new BizException("SSRF_INVALID_URL", "Outbound URL format is invalid: " + ex.getMessage());
        }
        return validateOutboundUri(uri, allowPrivateForDev);
    }

    /**
     * 校验出站 URI 对象。
     *
     * @param uri                 目标 URI
     * @param allowPrivateForDev 是否允许本地/私网
     * @return 校验通过的 URI 实例
     */
    public static URI validateOutboundUri(URI uri, boolean allowPrivateForDev) {
        if (uri == null) {
            throw new BizException("SSRF_INVALID_URL", "Outbound URI cannot be null");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new BizException("SSRF_INVALID_SCHEME", "Only HTTP and HTTPS protocols are permitted for outbound requests");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException("SSRF_INVALID_HOST", "Outbound URI must contain a valid host");
        }

        validateHost(host, allowPrivateForDev);
        return uri;
    }

    /**
     * 校验目标主机是否安全。
     *
     * @param host                主机名或 IP
     * @param allowPrivateForDev 是否允许私有网络
     */
    public static void validateHost(String host, boolean allowPrivateForDev) {
        if (host == null || host.isBlank()) {
            throw new BizException("SSRF_INVALID_HOST", "Outbound target host cannot be empty");
        }

        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }

        if (BLOCKED_HOSTNAMES.contains(normalizedHost)
                || normalizedHost.endsWith(".internal")
                || normalizedHost.endsWith(".local")) {
            if (!allowPrivateForDev) {
                throw new BizException("SSRF_BLOCKED", "Outbound target host is prohibited: " + host);
            }
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalizedHost);
            for (InetAddress address : addresses) {
                if (isPrivateOrReservedAddress(address) && !allowPrivateForDev) {
                    throw new BizException("SSRF_BLOCKED", "Outbound target IP is prohibited (private/reserved): " + address.getHostAddress());
                }
            }
        } catch (UnknownHostException ex) {
            throw new BizException("SSRF_HOST_UNRESOLVED", "Failed to resolve outbound target host: " + host);
        }
    }

    /**
     * 判断 IP 是否属于私有、环回、链路本地（包含云厂商元数据）、多播或保留网段。
     */
    public static boolean isPrivateOrReservedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }

        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();

        // 处理 IPv4 映射的 IPv6 地址 (::ffff:192.0.2.128)
        if (address instanceof Inet6Address && bytes.length == 16) {
            boolean isIpv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    isIpv4Mapped = false;
                    break;
                }
            }
            if (isIpv4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                byte[] ipv4Bytes = new byte[4];
                System.arraycopy(bytes, 12, ipv4Bytes, 0, 4);
                try {
                    return isPrivateOrReservedAddress(InetAddress.getByAddress(ipv4Bytes));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
            // IPv6 Unique Local Addresses (fc00::/7 -> 0xfc or 0xfd)
            int firstByte = bytes[0] & 0xFF;
            if ((firstByte & 0xFE) == 0xFC) {
                return true;
            }
        }

        if (address instanceof Inet4Address && bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 0.0.0.0/8 (Current network)
            if (b0 == 0) {
                return true;
            }
            // 10.0.0.0/8 (RFC 1918)
            if (b0 == 10) {
                return true;
            }
            // 100.64.0.0/10 (Carrier-Grade NAT / RFC 6598)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                return true;
            }
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) {
                return true;
            }
            // 169.254.0.0/16 (Link Local, including 169.254.169.254 cloud metadata)
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            // 172.16.0.0/12 (RFC 1918)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
                return true;
            }
            // 192.0.0.0/24 (IETF Protocol Assignments)
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xFF) == 0) {
                return true;
            }
            // 192.168.0.0/16 (RFC 1918)
            if (b0 == 192 && b1 == 168) {
                return true;
            }
            // 198.18.0.0/15 (Network benchmark tests)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) {
                return true;
            }
            // 240.0.0.0/4 (Reserved for future use) & 255.255.255.255 (Broadcast)
            if (b0 >= 240) {
                return true;
            }
        }

        return false;
    }
}
