package com.hoptrace.app;

public final class NativeTraceroute {
    public static final int MODE_ICMP = 0;
    public static final int MODE_UDP = 1;

    static {
        System.loadLibrary("hoptrace");
    }

    private NativeTraceroute() {}

    /**
     * Executes a native traceroute against an already resolved numeric IP address.
     * mode must be MODE_ICMP or MODE_UDP; DNS is resolved by CustomDnsResolver first.
     */
    public static native String run(String numericTarget, boolean ipv6, int mode,
                                    int maxHops, int timeoutMs, int probesPerHop);
}
