package io.github.stigjohnny.shipping.tenants.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The demo token's format and hash are exact (invariant T2): {@code ^demo-[a-z0-9-]{3,40}$}, and the SHA-256 of the
 * whole token's UTF-8 bytes, which is what the database stores (docs/modules/tenants.md). The tokens are the public
 * fixtures of that document; even so, a token's text never appears in its string form, so it cannot reach a log.
 */
class DemoTokenTest {

    static Stream<String> wellFormed() {
        return Stream.of("demo-clerk-alpha", "demo-clerk-beta", "demo-no-tenant-user", "demo-abc",
                "demo-" + "a".repeat(40), "demo-0-9", "demo----");
    }

    static Stream<String> malformed() {
        return Stream.of("", "demo-", "demo-ab", "demo-" + "a".repeat(41), "clerk-alpha", "Demo-clerk-alpha",
                "demo-Clerk-alpha", "demo-clerk_alpha", "demo-clerk alpha", " demo-clerk-alpha", "demo-clerk-alpha\n",
                "demo-clérk", "Bearer demo-clerk-alpha", "demo:clerk-alpha");
    }

    @ParameterizedTest
    @MethodSource("wellFormed")
    void aTokenOfTheDemoFormatIsAccepted(String token) {
        assertThat(DemoToken.parse(token)).map(DemoToken::value).contains(token);
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void anyOtherTextIsNoToken(String text) {
        assertThat(DemoToken.parse(text)).isEmpty();
        assertThatThrownBy(() -> new DemoToken(text)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A demo token matches ^demo-[a-z0-9-]{3,40}$.");
    }

    @Test
    void theHashIsTheSha256OfTheWholeTokensUtf8Bytes() throws NoSuchAlgorithmException {
        DemoToken token = DemoToken.parse("demo-clerk-alpha").orElseThrow();

        assertThat(token.sha256()).hasSize(32).isEqualTo(MessageDigest.getInstance("SHA-256")
                .digest("demo-clerk-alpha".getBytes(StandardCharsets.UTF_8)));
        assertThat(token.sha256()).isNotEqualTo(DemoToken.parse("demo-clerk-beta").orElseThrow().sha256());
    }

    @Test
    void eachHashIsANewArraySoNoCallerCanChangeAnother() {
        DemoToken token = DemoToken.parse("demo-clerk-alpha").orElseThrow();
        byte[] first = token.sha256();
        first[0] ^= 1;

        assertThat(token.sha256()).isNotEqualTo(first).isNotSameAs(token.sha256());
    }

    @Test
    void theStringFormNeverShowsTheToken() {
        assertThat(DemoToken.parse("demo-clerk-alpha").orElseThrow()).hasToString("DemoToken[redacted]");
    }
}
