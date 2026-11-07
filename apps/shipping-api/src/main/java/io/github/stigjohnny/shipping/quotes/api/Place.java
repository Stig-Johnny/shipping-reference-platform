package io.github.stigjohnny.shipping.quotes.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A country and a postcode in the contract's format. A postcode is part of an address, so a rejection names the rule
 * it breaks, never the value.
 */
public record Place(String countryCode, String postcode) {

    private static final Pattern COUNTRY_CODE = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern POSTCODE = Pattern.compile("^[A-Z0-9][A-Z0-9 -]{0,8}[A-Z0-9]$");

    public Place {
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(postcode, "postcode");
        if (!COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new IllegalArgumentException("countryCode must be two upper-case letters (ISO 3166-1 alpha-2).");
        }
        if (!POSTCODE.matcher(postcode).matches()) {
            throw new IllegalArgumentException("postcode must be 2 to 10 upper-case letters, digits, spaces or "
                    + "hyphens, starting and ending with a letter or digit.");
        }
    }
}
