package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Who a shipment goes to, validated as the contract's Recipient schema says. The name and the address lines hold 1 to
 * 70 characters and the city 1 to 50, counted in Unicode code points as the schema counts them, none of them a control
 * character; postcode and countryCode follow the contract's Postcode and CountryCode patterns. The text is well-formed
 * Unicode: a lone surrogate, which a JSON escape can carry and UTF-8 cannot encode, is refused, so that each accepted
 * value has one UTF-8 encoding and the request fingerprint tells every two values apart. A recipient is personal data,
 * synthetic in this repository and never logged, so a rejection names the field and its rule, never the value.
 */
public record Recipient(String name, String addressLine1, @Nullable String addressLine2, String postcode, String city,
        String countryCode) {

    /** The schema's pattern for text: no character from U+0000 to U+001F, and not U+007F. */
    private static final Pattern NO_CONTROL_CHARACTER = Pattern.compile("^[^\\u0000-\\u001F\\u007F]*$");
    private static final Pattern POSTCODE = Pattern.compile("^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$");
    private static final Pattern COUNTRY_CODE = Pattern.compile("^[A-Z]{2}$");
    private static final int LINE = 70;
    private static final int CITY = 50;

    public Recipient {
        text("name", name, LINE);
        text("addressLine1", addressLine1, LINE);
        if (addressLine2 != null) {
            text("addressLine2", addressLine2, LINE);
        }
        Objects.requireNonNull(postcode, "postcode");
        if (!POSTCODE.matcher(postcode).matches()) {
            throw new IllegalArgumentException("postcode must be 2 to 10 upper-case letters, digits, spaces or "
                    + "hyphens, starting and ending with a letter or digit.");
        }
        text("city", city, CITY);
        Objects.requireNonNull(countryCode, "countryCode");
        if (!COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new IllegalArgumentException("countryCode must be two upper-case letters (ISO 3166-1 alpha-2).");
        }
    }

    private static void text(String field, String value, int maxLength) {
        Objects.requireNonNull(value, field);
        // codePoints() joins each surrogate pair into the code point it encodes: a surrogate it yields is a lone one.
        if (value.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.SURROGATE)) {
            throw new IllegalArgumentException(field + " must be well-formed Unicode, without a lone surrogate.");
        }
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > maxLength || !NO_CONTROL_CHARACTER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be 1 to " + maxLength + " characters, none of them a "
                    + "control character.");
        }
    }
}
