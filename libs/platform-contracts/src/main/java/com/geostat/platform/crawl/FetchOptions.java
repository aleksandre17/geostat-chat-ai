package com.geostat.platform.crawl;

import com.geostat.platform.parse.ParseProfile;

/**
 * Configuration for a single page fetch operation.
 */
public record FetchOptions(
        RenderMode renderMode,
        int timeoutMs,
        String userAgent,
        NetworkPolicy network) {

    public FetchOptions {
        if (network == null) {
            network = NetworkPolicy.defaults();
        }
    }

    public static FetchOptions defaults() {
        return new FetchOptions(RenderMode.STATIC, 10_000, "GeostatBot/1.0", NetworkPolicy.defaults());
    }

    /** Factory that builds fetch options from a corpus parse profile. */
    public static FetchOptions forProfile(ParseProfile profile) {
        NetworkPolicy net = (profile.networkPolicy() != null)
                ? profile.networkPolicy()
                : NetworkPolicy.defaults();
        RenderMode mode = (profile.renderMode() != null)
                ? profile.renderMode()
                : RenderMode.STATIC;
        String agent = (net.userAgent() != null && !net.userAgent().isBlank())
                ? net.userAgent()
                : "GeostatBot/1.0";
        return new FetchOptions(mode, 10_000, agent, net);
    }
}
