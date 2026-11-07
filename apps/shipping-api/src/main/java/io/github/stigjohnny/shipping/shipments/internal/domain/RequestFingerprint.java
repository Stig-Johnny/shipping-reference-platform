package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What tells a replay from a key reused with another body: SHA-256, as 64 lower-case hexadecimal characters, over a
 * booking request's fields in the specification's order (docs/modules/shipments.md, "Request fingerprint"). Each field
 * is written as its UTF-8 byte length in decimal, a colon and its value, and an absent optional field as "0:", with no
 * separators, so no two requests share an encoding. Values count exactly as they were validated: no trimming and no
 * case folding. Text is encoded strictly: what UTF-8 cannot encode, such as a lone surrogate, is refused, never
 * replaced.
 */
public record RequestFingerprint(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{64}$");
    private static final byte[] ABSENT = "0:".getBytes(StandardCharsets.US_ASCII);

    public RequestFingerprint {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("A request fingerprint is 64 lower-case hexadecimal characters.");
        }
    }

    /**
     * The fingerprint of a request: quoteId, offerId, the recipient's name, addressLine1, addressLine2, postcode, city
     * and countryCode, then the reference. The IDs are written in a UUID's canonical lower-case form, the only form the
     * contract accepts.
     */
    public static RequestFingerprint of(BookingCommand command) {
        MessageDigest sha256 = sha256();
        Recipient recipient = command.recipient();
        ShipmentReference reference = command.reference();
        field(sha256, command.quoteId().value().toString());
        field(sha256, command.offerId().value().toString());
        field(sha256, recipient.name());
        field(sha256, recipient.addressLine1());
        field(sha256, recipient.addressLine2());
        field(sha256, recipient.postcode());
        field(sha256, recipient.city());
        field(sha256, recipient.countryCode());
        field(sha256, reference == null ? null : reference.value());
        return new RequestFingerprint(HexFormat.of().formatHex(sha256.digest()));
    }

    /** One field: its UTF-8 byte length in decimal, a colon and its bytes; "0:" when it is absent. */
    private static void field(MessageDigest sha256, @Nullable String value) {
        if (value == null) {
            sha256.update(ABSENT);
            return;
        }
        byte[] bytes = utf8(value);
        sha256.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
        sha256.update(bytes);
    }

    /**
     * The text's UTF-8 bytes, encoded strictly. A lenient encoder writes ? for a lone surrogate, which would give two
     * values one encoding; this one refuses it. Recipient refuses such text already, so this is the fingerprint's own
     * guard. Package-private, so that its own test reaches it.
     */
    static byte[] utf8(String value) {
        CharsetEncoder strict = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer encoded = strict.encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("A booking request's text is not well-formed Unicode, so it has no "
                    + "fingerprint.", e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256.", e);
        }
    }
}
