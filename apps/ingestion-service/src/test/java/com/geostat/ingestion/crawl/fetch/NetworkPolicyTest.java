package com.geostat.ingestion.crawl.fetch;

import com.geostat.platform.crawl.BasicAuthCredential;
import com.geostat.platform.crawl.NetworkPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkPolicyTest {

    @Test
    void defaults_areProdSafe() {
        NetworkPolicy net = NetworkPolicy.defaults();
        assertThat(net.tlsVerify()).isTrue();
        assertThat(net.respectRobotsTxt()).isTrue();
        assertThat(net.hasBasicAuth()).isFalse();
        assertThat(net.hasDnsOverrides()).isFalse();
    }

    @Test
    void basicAuthCredential_rejectsBlankPassword() {
        assertThatThrownBy(() -> new BasicAuthCredential("user", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENV_VAR");
    }

    @Test
    void basicAuthCredential_rejectsBlankUsername() {
        assertThatThrownBy(() -> new BasicAuthCredential("", "pass"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasDnsOverrides_returnsFalse_forEmptyMap() {
        NetworkPolicy net = new NetworkPolicy(true, true, null, Map.of(), null);
        assertThat(net.hasDnsOverrides()).isFalse();
    }

    @Test
    void hasDnsOverrides_returnsTrue_whenMapNonEmpty() {
        NetworkPolicy net = new NetworkPolicy(true, true, null,
                Map.of("internal.example.local", "192.168.1.100"), null);
        assertThat(net.hasDnsOverrides()).isTrue();
    }
}
