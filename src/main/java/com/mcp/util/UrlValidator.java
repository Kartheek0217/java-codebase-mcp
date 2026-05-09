package com.mcp.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Utility for validating URLs and preventing SSRF attacks.
 */
public class UrlValidator {

	/**
	 * Validates if a URL is safe to fetch (SSRF protection). NOTE: This
	 * implementation is vulnerable to DNS rebinding attacks as it resolves the IP
	 * only once at validation time. For higher security, re-validate after the
	 * connection is established.
	 */
	public static boolean isSafeUrl(String url) {
		if (url == null || url.isEmpty()) {
			return false;
		}

		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				return false;
			}

			String host = uri.getHost();
			if (host == null) {
				return false;
			}

			// Block common internal hostnames
			if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
				return false;
			}

			// Resolve and check IP
			try {
				InetAddress address = InetAddress.getByName(host);
				if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
						|| address.isAnyLocalAddress()) {
					return false;
				}
			} catch (UnknownHostException e) {
				// If we can't resolve it, we don't know if it's safe, but usually it's better
				// to fail closed
				// for public web crawling. However, for a generic crawler, maybe we allow it if
				// it's just a
				// DNS issue. But for SSRF, let's be strict.
				return false;
			}

			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
