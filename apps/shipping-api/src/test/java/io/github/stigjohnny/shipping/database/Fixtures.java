package io.github.stigjohnny.shipping.database;

/**
 * Synthetic rows for the database tests: the tenants, users and demo tokens of docs/modules/tenants.md, and quotes in
 * the style of docs/modules/quotes.md, priced from its golden table.
 */
final class Fixtures {

    static final String ALPHA = "11111111-1111-4111-8111-111111111111";
    static final String BETA = "22222222-2222-4222-8222-222222222222";
    /** A well-formed tenant ID that no tenant has. */
    static final String NOBODY = "99999999-9999-4999-8999-999999999999";
    static final String CLERK_ALPHA = "00000000-0000-4000-8000-0000000000a1";
    static final String NO_TENANT_USER = "00000000-0000-4000-8000-0000000000c1";

    /** Tenant Alpha's quote, NO 0150 to SE 11122, 1200 g, 300 x 200 x 100 mm, with two offers. */
    static final String ALPHA_QUOTE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    static final String ALPHA_ECONOMY = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaae1";
    static final String ALPHA_STANDARD = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaae2";
    /** Tenant Beta's quote, SE 11122 to SE 41101, 2500 g, 400 x 300 x 200 mm, with one offer. */
    static final String BETA_QUOTE = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1";
    static final String BETA_STANDARD = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbe1";

    private static final String QUOTE_COLUMNS = "insert into quotes.quote (tenant_id, quote_id, issued_at, expires_at,"
            + " weight_grams, length_mm, width_mm, height_mm, billable_weight_grams, zone, origin_country,"
            + " origin_postcode, destination_country, destination_postcode) values ";
    private static final String OFFER_COLUMNS = "insert into quotes.quote_offer (tenant_id, quote_id, offer_id,"
            + " service_level, amount, currency, transit_min_days, transit_max_days) values ";

    private Fixtures() {
    }

    /** Alpha's quote as an INSERT, for whichever tenant and quote ID the test claims. */
    static String alphaQuote(String tenant, String quote) {
        return QUOTE_COLUMNS + "('" + tenant + "', '" + quote + "', '2026-01-05T09:00:00.000Z',"
                + " '2026-01-05T09:30:00.000Z', 1200, 300, 200, 100, 1200, 'INTERNATIONAL', 'NO', '0150', 'SE',"
                + " '11122')";
    }

    static String betaQuote() {
        return QUOTE_COLUMNS + "('" + BETA + "', '" + BETA_QUOTE + "', '2026-01-05T10:00:00.000Z',"
                + " '2026-01-05T10:30:00.000Z', 2500, 400, 300, 200, 4800, 'DOMESTIC', 'SE', '11122', 'SE', '41101')";
    }

    /** An offer as an INSERT: tenant, quote, offer, service level, amount in EUR, transit days. */
    static String offer(String tenant, String quote, String offer, String level, String amount, int minDays,
            int maxDays) {
        return OFFER_COLUMNS + "('" + tenant + "', '" + quote + "', '" + offer + "', '" + level + "', " + amount
                + ", 'EUR', " + minDays + ", " + maxDays + ")";
    }
}
