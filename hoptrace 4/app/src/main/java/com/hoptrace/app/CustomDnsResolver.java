package com.hoptrace.app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Host resolver for HopTrace. A blank DNS server keeps Android's configured system resolver;
 * a custom numeric IPv4/IPv6 resolver is queried directly over DNS UDP (TCP fallback on TC).
 */
public final class CustomDnsResolver {
    public static final int FAMILY_AUTO = 0;
    public static final int FAMILY_IPV4 = 4;
    public static final int FAMILY_IPV6 = 6;

    private static final int DNS_PORT = 53;
    private static final int DNS_TIMEOUT_MS = 2800;
    private static final int DNS_RETRIES = 2;
    private static final int TYPE_A = 1;
    private static final int TYPE_AAAA = 28;
    private static final int CLASS_IN = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CustomDnsResolver() {}

    public static InetAddress resolve(String host, int family, String customServer) throws IOException {
        if (host == null || host.trim().isEmpty()) throw new IOException("Enter a hostname or IP address.");
        if (family != FAMILY_AUTO && family != FAMILY_IPV4 && family != FAMILY_IPV6) {
            throw new IllegalArgumentException("Unsupported IP family selection.");
        }

        String target = host.trim();
        InetAddress literal = parseLiteralIfPresent(target);
        if (literal != null) return requireFamily(literal, family);

        String asciiName = normalizeHostName(target);
        if (customServer == null || customServer.trim().isEmpty()) {
            return resolveWithSystemDns(asciiName, family);
        }

        InetAddress dnsServer = parseDnsServer(customServer);
        if (family == FAMILY_IPV4) return queryAddress(asciiName, dnsServer, TYPE_A);
        if (family == FAMILY_IPV6) return queryAddress(asciiName, dnsServer, TYPE_AAAA);

        IOException ipv4Failure;
        try {
            return queryAddress(asciiName, dnsServer, TYPE_A);
        } catch (IOException e) {
            ipv4Failure = e;
        }
        try {
            return queryAddress(asciiName, dnsServer, TYPE_AAAA);
        } catch (IOException ipv6Failure) {
            throw new IOException("Custom DNS could not resolve " + asciiName +
                    " as A or AAAA. IPv4: " + ipv4Failure.getMessage() +
                    "; IPv6: " + ipv6Failure.getMessage(), ipv6Failure);
        }
    }

    public static void validateDnsServer(String value) throws IOException {
        parseDnsServer(value);
    }

    private static InetAddress resolveWithSystemDns(String host, int family) throws IOException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new IOException("System DNS could not resolve " + host + ": " + e.getMessage(), e);
        }
        for (InetAddress address : addresses) {
            if (matchesFamily(address, family)) return address;
        }
        throw new IOException("System DNS returned no " + familyName(family) + " address for " + host + ".");
    }

    private static InetAddress requireFamily(InetAddress address, int family) throws IOException {
        if (!matchesFamily(address, family)) {
            throw new IOException("The entered IP address does not match the selected " + familyName(family) + " mode.");
        }
        return address;
    }

    private static boolean matchesFamily(InetAddress address, int family) {
        return family == FAMILY_AUTO ||
                (family == FAMILY_IPV4 && address instanceof Inet4Address) ||
                (family == FAMILY_IPV6 && address instanceof Inet6Address);
    }

    private static String familyName(int family) {
        return family == FAMILY_IPV4 ? "IPv4" : family == FAMILY_IPV6 ? "IPv6" : "IP family";
    }

    private static InetAddress parseLiteralIfPresent(String value) throws IOException {
        String literal = value;
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        if (literal.indexOf(':') >= 0) {
            try {
                InetAddress parsed = InetAddress.getByName(literal);
                if (parsed instanceof Inet6Address) return parsed;
            } catch (Exception e) {
                throw new IOException("Invalid IPv6 address: " + value, e);
            }
            throw new IOException("Invalid IPv6 address: " + value);
        }
        if (looksLikeIpv4(literal)) return parseIpv4(literal);
        return null;
    }

    private static boolean looksLikeIpv4(String value) {
        return value.matches("[0-9.]+");
    }

    private static InetAddress parseIpv4(String value) throws IOException {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) throw new IOException("Invalid IPv4 address: " + value);
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) throw new IOException("Invalid IPv4 address: " + value);
            int octet;
            try { octet = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { throw new IOException("Invalid IPv4 address: " + value, e); }
            if (octet < 0 || octet > 255) throw new IOException("Invalid IPv4 address: " + value);
            bytes[i] = (byte) octet;
        }
        return InetAddress.getByAddress(bytes);
    }

    private static InetAddress parseDnsServer(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) throw new IOException("Enter a custom DNS server address.");
        String literal = value.trim();
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        if (literal.indexOf('%') >= 0) throw new IOException("Use a DNS server IPv6 address without a zone suffix.");
        if (literal.indexOf(':') >= 0) {
            try {
                InetAddress address = InetAddress.getByName(literal);
                if (!(address instanceof Inet6Address)) throw new IOException("Custom DNS server is not a valid IPv6 address.");
                return address;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Invalid IPv6 DNS server address.", e);
            }
        }
        if (!looksLikeIpv4(literal)) throw new IOException("DNS server must be a numeric IPv4 or IPv6 address.");
        return parseIpv4(literal);
    }

    private static String normalizeHostName(String value) throws IOException {
        String host = value.trim();
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.length() > 253 || host.contains("/") || host.contains("\\") || host.contains(" ")) {
            throw new IOException("Enter a valid hostname without a URL scheme or path.");
        }
        final String ascii;
        try { ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid hostname: " + host, e); }
        if (ascii.length() > 253) throw new IOException("Hostname is too long.");
        for (String label : ascii.split("\\.")) {
            if (label.isEmpty() || label.length() > 63) throw new IOException("Invalid hostname label in " + host + ".");
        }
        return ascii;
    }

    private static InetAddress queryAddress(String host, InetAddress server, int queryType) throws IOException {
        int transactionId = RANDOM.nextInt(0x10000);
        byte[] query = buildQuery(host, queryType, transactionId);
        byte[] response = null;
        IOException lastFailure = null;
        for (int attempt = 0; attempt < DNS_RETRIES; attempt++) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(DNS_TIMEOUT_MS);
                // Connect the UDP socket to the selected resolver. Besides simplifying send/receive,
                // this makes the platform discard datagrams from any other source address/port.
                socket.connect(new InetSocketAddress(server, DNS_PORT));
                DatagramPacket request = new DatagramPacket(query, query.length);
                socket.send(request);
                byte[] buffer = new byte[4096];
                DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                socket.receive(reply);
                response = Arrays.copyOf(reply.getData(), reply.getLength());
                break;
            } catch (SocketTimeoutException e) {
                lastFailure = new IOException("DNS server " + server.getHostAddress() + " did not answer on UDP port 53.", e);
            } catch (IOException e) {
                lastFailure = new IOException("DNS query to " + server.getHostAddress() + " failed: " + e.getMessage(), e);
            }
        }
        if (response == null) throw lastFailure == null ? new IOException("DNS query failed.") : lastFailure;

        if (response.length >= 4 && (readU16(response, 2) & 0x0200) != 0) {
            response = queryTcp(server, query);
        }
        return parseResponse(response, transactionId, queryType);
    }

    private static byte[] buildQuery(String host, int queryType, int transactionId) throws IOException {
        byte[] name = host.getBytes(StandardCharsets.US_ASCII);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(12 + name.length + 6);
        output.write((transactionId >>> 8) & 0xff);
        output.write(transactionId & 0xff);
        output.write(0x01); // recursion desired
        output.write(0x00);
        output.write(0x00); output.write(0x01); // QDCOUNT
        output.write(0x00); output.write(0x00); // ANCOUNT
        output.write(0x00); output.write(0x00); // NSCOUNT
        output.write(0x00); output.write(0x00); // ARCOUNT
        int start = 0;
        while (start < name.length) {
            int dot = host.indexOf('.', start);
            int end = dot < 0 ? name.length : dot;
            int labelLength = end - start;
            if (labelLength < 1 || labelLength > 63) throw new IOException("Invalid DNS label length.");
            output.write(labelLength);
            output.write(name, start, labelLength);
            start = end + 1;
        }
        output.write(0);
        output.write((queryType >>> 8) & 0xff); output.write(queryType & 0xff);
        output.write(0); output.write(CLASS_IN);
        return output.toByteArray();
    }

    private static byte[] queryTcp(InetAddress server, byte[] query) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server, DNS_PORT), DNS_TIMEOUT_MS);
            socket.setSoTimeout(DNS_TIMEOUT_MS);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeShort(query.length);
            output.write(query);
            output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int length;
            try { length = input.readUnsignedShort(); }
            catch (EOFException e) { throw new IOException("DNS server closed the TCP response early.", e); }
            if (length < 12 || length > 65535) throw new IOException("Invalid DNS TCP response length.");
            byte[] response = new byte[length];
            input.readFully(response);
            return response;
        }
    }

    static InetAddress parseResponse(byte[] response, int expectedId, int queryType) throws IOException {
        if (response == null || response.length < 12) throw new IOException("DNS response is too short.");
        if (readU16(response, 0) != expectedId) throw new IOException("DNS transaction ID mismatch.");
        int flags = readU16(response, 2);
        if ((flags & 0x8000) == 0) throw new IOException("Received a packet that is not a DNS response.");
        if ((flags & 0x0200) != 0) throw new IOException("DNS response was truncated and TCP fallback was not available.");
        int rcode = flags & 0x000f;
        if (rcode != 0) {
            String reason = rcode == 3 ? "NXDOMAIN (name does not exist)" : "DNS server response code " + rcode;
            throw new IOException(reason + ".");
        }

        int questionCount = readU16(response, 4);
        int answerCount = readU16(response, 6);
        int offset = 12;
        for (int i = 0; i < questionCount; i++) {
            offset = skipName(response, offset);
            if (offset + 4 > response.length) throw new IOException("Malformed DNS question section.");
            offset += 4;
        }

        for (int i = 0; i < answerCount; i++) {
            offset = skipName(response, offset);
            if (offset + 10 > response.length) throw new IOException("Malformed DNS answer section.");
            int type = readU16(response, offset);
            int recordClass = readU16(response, offset + 2);
            int dataLength = readU16(response, offset + 8);
            offset += 10;
            if (offset + dataLength > response.length) throw new IOException("DNS answer extends beyond the response packet.");
            if (recordClass == CLASS_IN && type == queryType &&
                    ((queryType == TYPE_A && dataLength == 4) || (queryType == TYPE_AAAA && dataLength == 16))) {
                byte[] address = Arrays.copyOfRange(response, offset, offset + dataLength);
                return InetAddress.getByAddress(address);
            }
            offset += dataLength;
        }
        throw new IOException("DNS response contains no " + (queryType == TYPE_A ? "A" : "AAAA") + " record.");
    }

    private static int skipName(byte[] packet, int offset) throws IOException {
        int cursor = offset;
        int jumps = 0;
        while (true) {
            if (cursor >= packet.length) throw new IOException("Malformed DNS name.");
            int length = packet[cursor] & 0xff;
            if ((length & 0xc0) == 0xc0) {
                if (cursor + 1 >= packet.length) throw new IOException("Malformed DNS compression pointer.");
                if (jumps++ > 16) throw new IOException("DNS compression pointer loop.");
                return cursor + 2;
            }
            if ((length & 0xc0) != 0) throw new IOException("Unsupported DNS label encoding.");
            cursor++;
            if (length == 0) return cursor;
            if (length > 63 || cursor + length > packet.length) throw new IOException("Malformed DNS label.");
            cursor += length;
        }
    }

    private static int readU16(byte[] packet, int offset) throws IOException {
        if (offset < 0 || offset + 2 > packet.length) throw new IOException("Unexpected end of DNS packet.");
        return ((packet[offset] & 0xff) << 8) | (packet[offset + 1] & 0xff);
    }
}
