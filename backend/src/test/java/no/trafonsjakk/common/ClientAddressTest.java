package no.trafonsjakk.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Behind Render the edge overwrites True-Client-IP with the address that connected to it, and appends to
 * X-Forwarded-For whatever the client sent. Through the Vercel rewrite, Vercel sets X-Forwarded-For to the
 * visitor's address and Render sees Vercel as the connecting client.
 */
class ClientAddressTest {

    @Test
    void throughVercelTheVisitorIsTheEntryVercelAddedBeforeItself() {
        ClientAddress a = ClientAddress.from("76.76.21.21", "198.51.100.7, 76.76.21.21, 172.71.0.1, 10.0.0.1", "10.0.0.1");
        assertThat(a.edge()).isEqualTo("76.76.21.21");
        assertThat(a.client()).isEqualTo("198.51.100.7");
    }

    @Test
    void directlyAtRenderAForgedEntryIsUsedButTheEdgeAddressCannotBeForged() {
        ClientAddress a = ClientAddress.from("203.0.113.50", "6.6.6.6, 203.0.113.50, 172.71.0.1", "10.0.0.1");
        assertThat(a.edge()).isEqualTo("203.0.113.50");
        assertThat(a.client()).isEqualTo("6.6.6.6"); // forgeable: that's why the edge has its own limit
    }

    @Test
    void withoutAForwardedChainTheClientIsTheEdge() {
        assertThat(ClientAddress.from("203.0.113.50", null, "10.0.0.1")).isEqualTo(new ClientAddress("203.0.113.50", "203.0.113.50"));
        assertThat(ClientAddress.from("203.0.113.50", "203.0.113.50, 10.0.0.1", "10.0.0.1").client()).isEqualTo("203.0.113.50");
    }

    @Test
    void withoutRendersHeaderItIsTheSocketAddress() {
        assertThat(ClientAddress.from(null, "6.6.6.6", "127.0.0.1")).isEqualTo(new ClientAddress("127.0.0.1", "127.0.0.1"));
    }

    @Test
    void ignoresValuesThatAreNotAddresses() {
        assertThat(ClientAddress.from("<script>", null, "127.0.0.1").edge()).isEqualTo("127.0.0.1");
        assertThat(ClientAddress.from("1".repeat(80), null, "127.0.0.1").edge()).isEqualTo("127.0.0.1");
        assertThat(ClientAddress.from("203.0.113.50", "evil value, 203.0.113.50", "10.0.0.1").client()).isEqualTo("203.0.113.50");
        assertThat(ClientAddress.from("2001:db8::1", "2001:db8::7, 2001:db8::1", "10.0.0.1").client()).isEqualTo("2001:db8::7");
    }
}
