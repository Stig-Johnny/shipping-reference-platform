package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Pure UTF-8 SVG text templating: no active content, links, style, fonts or external resources. */
public final class SvgLabelRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);

    public String render(Shipment shipment) {
        String tracking = shipment.trackingNumber().value();
        String title = "Simulated shipping label " + tracking;
        StringBuilder svg = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100mm\" height=\"150mm\" viewBox=\"0 0 1000 1500\" role=\"img\" aria-label=\"")
                .append(escape(title)).append("\">\n<title>").append(escape(title)).append("</title>\n");
        text(svg, 70, "SIMULATED LABEL - NOT VALID FOR CARRIAGE");
        text(svg, 140, shipment.serviceLevel().name());
        text(svg, 240, "FROM " + shipment.origin().countryCode() + " " + shipment.origin().postcode());
        text(svg, 340, "TO");
        Recipient recipient = shipment.recipient();
        text(svg, 410, recipient.name());
        text(svg, 480, recipient.addressLine1());
        if (recipient.addressLine2() != null) {
            text(svg, 550, recipient.addressLine2());
        }
        text(svg, 620, recipient.postcode() + " " + recipient.city());
        text(svg, 690, recipient.countryCode());
        text(svg, 990, tracking);
        int x = 80;
        for (int i = 3; i < tracking.length(); i++) {
            int width = (tracking.charAt(i) - '0' + 1) * 4;
            svg.append("<rect x=\"").append(x).append("\" y=\"1100\" width=\"").append(width)
                    .append("\" height=\"160\" fill=\"black\"/>\n");
            x += width + 8;
        }
        if (shipment.reference() != null) {
            text(svg, 1350, "REF " + shipment.reference().value());
        }
        text(svg, 1430, "BOOKED " + DATE.format(shipment.bookedAt()));
        return svg.append("</svg>\n").toString();
    }

    private static void text(StringBuilder svg, int y, String value) {
        svg.append("<text x=\"80\" y=\"").append(y)
                .append("\" font-family=\"sans-serif\" font-size=\"24\" fill=\"black\">")
                .append(escape(value)).append("</text>\n");
    }

    private static String escape(String text) {
        return text.replace("\uFFFE", "[U+FFFE]").replace("\uFFFF", "[U+FFFF]").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
