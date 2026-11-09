package io.github.stigjohnny.shipping.shipments.internal.domain;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Pure UTF-8 SVG text templating: no active content, links, style, fonts or external resources. */
public final class SvgLabelRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);
    private static final int FONT_SIZE = 24;
    private static final int TEXT_WIDTH = 920;
    private static final int TEXT_HEIGHT = 70;

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

    /**
     * Font-independent layout estimate: one em per displayed code point, half an em for an ASCII space, and one em
     * above and below each baseline. Expand XML noncharacters before measuring; escape entities afterwards.
     * Each field has 920 x 70 units, starting at x=80, y=(original baseline - 24). Choose the largest integer font
     * size up to 24 whose wrapped lines fit that box. Short fields retain their original bytes and position.
     * Wrap at code-point boundaries, retaining every character (including spaces and supplementary characters).
     * The contract permits at most 70 input code points per field, or 560 after placeholder expansion: even that
     * fits in five lines at size 7. No platform font metrics or extra SVG attributes are needed.
     */
    private static void text(StringBuilder svg, int y, String value) {
        String visible = value.replace("\uFFFE", "[U+FFFE]").replace("\uFFFF", "[U+FFFF]");
        int size = FONT_SIZE;
        while (lineCount(visible, size) * 2 * size > TEXT_HEIGHT) {
            size--;
        }
        int baseline = y - FONT_SIZE + size;
        for (int start = 0; start < visible.length();) {
            int end = lineEnd(visible, start, size);
            svg.append("<text x=\"80\" y=\"").append(baseline)
                    .append("\" font-family=\"sans-serif\" font-size=\"").append(size).append("\" fill=\"black\">")
                    .append(escape(visible.substring(start, end))).append("</text>\n");
            start = end;
            baseline += 2 * size;
        }
    }

    private static int lineCount(String value, int size) {
        int lines = 0;
        for (int start = 0; start < value.length(); start = lineEnd(value, start, size)) {
            lines++;
        }
        return lines;
    }

    private static int lineEnd(String value, int start, int size) {
        int end = start;
        int halfEms = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            halfEms += codePoint == ' ' ? 1 : 2;
            if (halfEms * size > 2 * TEXT_WIDTH) {
                break;
            }
            end += Character.charCount(codePoint);
        }
        return end;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
