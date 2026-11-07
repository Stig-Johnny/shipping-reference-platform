package io.github.stigjohnny.shipping.shipments.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The request fingerprint (invariant S7): SHA-256, as lower-case hex, over the request's fields in the specification's
 * order, each written as its UTF-8 byte length in decimal, a colon and its value, and an absent optional field as
 * "0:". The two golden values were computed by hand from that encoding with {@code shasum -a 256}, not with this code.
 * Every field changes the fingerprint, an absent optional field differs from a present one, and values count exactly
 * as they are: no trimming and no case folding.
 */
class RequestFingerprintTest {

    private static final QuoteId QUOTE = new QuoteId(UUID.fromString("5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21"));
    private static final OfferId OFFER = new OfferId(UUID.fromString("0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02"));
    private static final Recipient RECIPIENT = ShipmentFixtures.RECIPIENT;
    /** The contract's example booking request: no second address line, the reference ORDER-1001. */
    private static final BookingCommand EXAMPLE = new BookingCommand(QUOTE, OFFER, RECIPIENT,
            new ShipmentReference("ORDER-1001"));
    /** EXAMPLE as the specification encodes it, written out by hand. */
    private static final String EXAMPLE_ENCODED = "36:5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21"
            + "36:0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02" + "21:Synthetic Recipient 1" + "16:1 Example Street" + "0:"
            + "5:11122" + "9:Exampleby" + "2:SE" + "10:ORDER-1001";
    private static final String EXAMPLE_GOLDEN = "4811c2af57f81d7671dd467eeaa85f167cc99f0757a37cfb7f27d53353bd770c";

    @Test
    void theContractsExampleHasTheFingerprintComputedByHand() {
        assertThat(sha256(EXAMPLE_ENCODED)).isEqualTo(EXAMPLE_GOLDEN);

        assertThat(RequestFingerprint.of(EXAMPLE)).isEqualTo(new RequestFingerprint(EXAMPLE_GOLDEN));
    }

    @Test
    void lengthsCountUtf8BytesAndAnAbsentReferenceIsZeroAndAColon() {
        // "Eksempeløy" is 10 characters and 11 bytes: the o with a stroke takes two.
        BookingCommand command = new BookingCommand(QUOTE, OFFER, new Recipient("Synthetic Recipient 2",
                "2 Example Street", "Floor 3", "0150", "Eksempeløy", "NO"), null);
        String encoded = "36:5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b21" + "36:0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a02"
                + "21:Synthetic Recipient 2" + "16:2 Example Street" + "7:Floor 3" + "4:0150" + "11:Eksempeløy"
                + "2:NO" + "0:";
        String golden = "6a39f5f6c5ceb6aec7487dd7f9bf12c04987d397925cb16ffd62404539fffa65";
        assertThat(sha256(encoded)).isEqualTo(golden);

        assertThat(RequestFingerprint.of(command).value()).isEqualTo(golden);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eachFieldChanged")
    void changingAnyOneFieldChangesTheFingerprint(String field, BookingCommand changed) {
        assertThat(RequestFingerprint.of(changed)).isNotEqualTo(RequestFingerprint.of(EXAMPLE));
    }

    @Test
    void theNineChangesGiveNineFingerprintsOfTheirOwn() {
        List<RequestFingerprint> fingerprints = Stream.concat(Stream.of(EXAMPLE),
                eachFieldChanged().map(arguments -> (BookingCommand) arguments.get()[1]))
                .map(RequestFingerprint::of).toList();

        assertThat(fingerprints).hasSize(10).doesNotHaveDuplicates();
    }

    @Test
    void anAbsentOptionalFieldDiffersFromAPresentOne() {
        BookingCommand withoutReference = new BookingCommand(QUOTE, OFFER, RECIPIENT, null);
        BookingCommand withSecondLine = new BookingCommand(QUOTE, OFFER, new Recipient(RECIPIENT.name(),
                RECIPIENT.addressLine1(), "0:", RECIPIENT.postcode(), RECIPIENT.city(), RECIPIENT.countryCode()),
                EXAMPLE.reference());

        assertThat(RequestFingerprint.of(withoutReference)).isNotEqualTo(RequestFingerprint.of(EXAMPLE));
        // Even a second address line that reads like the encoding of an absent field is told apart from none.
        assertThat(RequestFingerprint.of(withSecondLine)).isNotEqualTo(RequestFingerprint.of(EXAMPLE));
    }

    @Test
    void theLengthsKeepTheFieldsApart() {
        // Without the lengths both requests would read "Synthetic Recipient 11 Example Street".
        BookingCommand shifted = new BookingCommand(QUOTE, OFFER, new Recipient("Synthetic Recipient 11",
                " Example Street", null, RECIPIENT.postcode(), RECIPIENT.city(), RECIPIENT.countryCode()),
                EXAMPLE.reference());

        assertThat(RequestFingerprint.of(shifted)).isNotEqualTo(RequestFingerprint.of(EXAMPLE));
    }

    @Test
    void valuesCountExactlyWithoutTrimmingOrCaseFolding() {
        for (BookingCommand variant : List.of(
                new BookingCommand(QUOTE, OFFER, RECIPIENT, new ShipmentReference("order-1001")),
                new BookingCommand(QUOTE, OFFER, recipient("Synthetic Recipient 1 ", "Exampleby"),
                        EXAMPLE.reference()),
                new BookingCommand(QUOTE, OFFER, recipient("Synthetic Recipient 1", "EXAMPLEBY"),
                        EXAMPLE.reference()))) {
            assertThat(RequestFingerprint.of(variant)).as(variant.toString())
                    .isNotEqualTo(RequestFingerprint.of(EXAMPLE));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"4811C2AF57F81D7671DD467EEAA85F167CC99F0757A37CFB7F27D53353BD770C",
            "811c2af57f81d7671dd467eeaa85f167cc99f0757a37cfb7f27d53353bd770c",
            "4811c2af57f81d7671dd467eeaa85f167cc99f0757a37cfb7f27d53353bd770c0",
            "g811c2af57f81d7671dd467eeaa85f167cc99f0757a37cfb7f27d53353bd770c", ""})
    void aFingerprintIsSixtyFourLowerCaseHexadecimalCharacters(String value) {
        assertThatThrownBy(() -> new RequestFingerprint(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A request fingerprint is 64 lower-case hexadecimal characters.");
    }

    /** EXAMPLE with one field changed, for each of the nine. */
    static Stream<Arguments> eachFieldChanged() {
        Recipient r = RECIPIENT;
        ShipmentReference reference = new ShipmentReference("ORDER-1001");
        return Stream.of(
                Arguments.of("quoteId", new BookingCommand(new QuoteId(UUID.fromString(
                        "5b0f6c3e-2a43-4c1e-9d55-0d6f3e1a7b22")), OFFER, r, reference)),
                Arguments.of("offerId", new BookingCommand(QUOTE, new OfferId(UUID.fromString(
                        "0e5b8a3c-7f0d-4b8e-8f3a-6c2d1e0f9a03")), r, reference)),
                Arguments.of("recipient.name", new BookingCommand(QUOTE, OFFER, new Recipient("Synthetic Recipient 2",
                        r.addressLine1(), null, r.postcode(), r.city(), r.countryCode()), reference)),
                Arguments.of("recipient.addressLine1", new BookingCommand(QUOTE, OFFER, new Recipient(r.name(),
                        "2 Example Street", null, r.postcode(), r.city(), r.countryCode()), reference)),
                Arguments.of("recipient.addressLine2", new BookingCommand(QUOTE, OFFER, new Recipient(r.name(),
                        r.addressLine1(), "Floor 3", r.postcode(), r.city(), r.countryCode()), reference)),
                Arguments.of("recipient.postcode", new BookingCommand(QUOTE, OFFER, new Recipient(r.name(),
                        r.addressLine1(), null, "11123", r.city(), r.countryCode()), reference)),
                Arguments.of("recipient.city", new BookingCommand(QUOTE, OFFER, new Recipient(r.name(),
                        r.addressLine1(), null, r.postcode(), "Exampleby North", r.countryCode()), reference)),
                Arguments.of("recipient.countryCode", new BookingCommand(QUOTE, OFFER, new Recipient(r.name(),
                        r.addressLine1(), null, r.postcode(), r.city(), "NO"), reference)),
                Arguments.of("reference", new BookingCommand(QUOTE, OFFER, r, new ShipmentReference("ORDER-1002"))));
    }

    private static Recipient recipient(String name, String city) {
        return new Recipient(name, RECIPIENT.addressLine1(), null, RECIPIENT.postcode(), city, RECIPIENT.countryCode());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
