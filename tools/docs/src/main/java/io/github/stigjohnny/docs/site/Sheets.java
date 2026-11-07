package io.github.stigjohnny.docs.site;

import io.github.stigjohnny.docs.DocsException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Each page's sheet code in the drawing register, such as {@code M-03}: a letter for the section and a number. Codes
 * are written out here, page by page, or come from a decision record's own number; never from a page's position in a
 * list, so that adding a page never renumbers the others. A page without a code fails the build.
 */
final class Sheets {

    private static final Pattern DECISION = Pattern.compile("decisions/(\\d{4})-[a-z0-9-]+\\.html");

    private static final Map<String, String> CODES = Map.ofEntries(
            Map.entry(StartPages.INDEX, "S-00"),
            Map.entry(StartPages.README, "S-01"),
            Map.entry(StartPages.SITE_DESIGN, "S-02"),
            Map.entry(ModelPages.INDEX, "M-00"),
            Map.entry("model/system-context.html", "M-01"),
            Map.entry("model/containers.html", "M-02"),
            Map.entry("model/api-components.html", "M-03"),
            Map.entry("model/quote-book-label.html", "M-04"),
            Map.entry("model/flow-quote-book-label.html", "M-05"),
            Map.entry("modules/index.html", "C-00"),
            Map.entry("modules/quotes.html", "C-01"),
            Map.entry("modules/shipments.html", "C-02"),
            Map.entry("modules/tenants.html", "C-03"),
            Map.entry("modules/audit.html", "C-04"),
            Map.entry("modules/orders.html", "C-05"),
            Map.entry("modules/tracking.html", "C-06"),
            Map.entry("modules/returns.html", "C-07"),
            Map.entry("modules/web-workbench.html", "C-08"),
            Map.entry("decisions/index.html", "D-00"),
            Map.entry("api/index.html", "A-00"),
            Map.entry("api/createQuote.html", "A-01"),
            Map.entry("api/getQuote.html", "A-02"),
            Map.entry("api/bookShipment.html", "A-03"),
            Map.entry("api/listShipments.html", "A-04"),
            Map.entry("api/getShipment.html", "A-05"),
            Map.entry("api/getShipmentLabel.html", "A-06"),
            Map.entry("api/cancelShipment.html", "A-07"),
            Map.entry("api/getCurrentMembership.html", "A-08"),
            Map.entry("api/schemas.html", "A-09"),
            Map.entry(TrustPages.INDEX, "T-00"),
            Map.entry(AllowlistPage.PAGE, "T-01"),
            Map.entry(TrustPages.RISK, "T-02"),
            Map.entry("trust/gaps.html", "T-03"),
            Map.entry(LegalPages.LICENSE_PAGE, "T-04"),
            Map.entry(LegalPages.NOTICE_PAGE, "T-05"),
            Map.entry(NoticesPage.PAGE, "T-06"),
            Map.entry(BrainPage.PAGE, "B-00"),
            Map.entry(NotFoundPage.PAGE, "404"));

    private Sheets() {
    }

    /** The page's sheet code; a decision record's is {@code D-} and its own number. */
    static String code(String page) {
        Matcher decision = DECISION.matcher(page);
        if (decision.matches()) {
            int number = Integer.parseInt(decision.group(1));
            return String.format(Locale.ROOT, "D-%02d", number);
        }
        String code = CODES.get(page);
        if (code == null) {
            throw new DocsException(page + " has no sheet code; give it one in tools/docs (Sheets) before publishing it");
        }
        return code;
    }
}
