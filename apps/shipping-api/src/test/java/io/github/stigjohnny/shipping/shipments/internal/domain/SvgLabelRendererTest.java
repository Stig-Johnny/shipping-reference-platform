package io.github.stigjohnny.shipping.shipments.internal.domain;

import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.OSLO;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.STOCKHOLM;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.eur;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.KEY;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.id;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stigjohnny.shipping.quotes.api.BookableOffer;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Element;

/** Exact golden output plus an independent XML allowlist and byte-bound check, including adversarial synthetic text. */
class SvgLabelRendererTest {
    @Test
    void theSameShipmentAlwaysHasTheGoldenLabel() throws Exception {
        String svg = new SvgLabelRenderer().render(shipment(ShipmentFixtures.RECIPIENT, ShipmentFixtures.REFERENCE));
        assertThat(svg).isEqualTo(Files.readString(Path.of("src/test/resources/labels/standard.svg")))
                .isEqualTo(new SvgLabelRenderer().render(shipment(ShipmentFixtures.RECIPIENT, ShipmentFixtures.REFERENCE)));
        checkXml(svg);
    }

    @Test
    void xmlNoncharactersRemainVisibleWithoutCreatingAnInvalidDocument() throws Exception {
        Recipient recipient = new Recipient("Synthetic \uFFFE \uFFFF", "1 Example Street", null, "11122", "Exampleby", "SE");
        String svg = new SvgLabelRenderer().render(shipment(recipient, null));
        assertThat(svg).contains("Synthetic [U+FFFE] [U+FFFF]");
        checkXml(svg);
    }

    @Test
    void everyTextPositionIsEscapedAndTheLargestRecipientStillFits() throws Exception {
        String hostile = "<>&\"'😀".repeat(8);
        String line = hostile + "😀".repeat(22);
        Recipient recipient = new Recipient(line, line, line, "11122", hostile + "😀".repeat(2), "SE");
        String svg = new SvgLabelRenderer().render(shipment(recipient, null));
        assertThat(svg).contains("&lt;&gt;&amp;&quot;&apos;😀").doesNotContain("REF ", "\r");
        assertThat(svg.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(65536);
        checkXml(svg);
    }

    @ParameterizedTest
    @ValueSource(strings = {"W", "\uFFFF", "\uFFFE", "😀", "&", "<", " "})
    void fullLengthRecipientTextStaysInsideEachLabelField(String character) throws Exception {
        String line = character.repeat(70);
        String city = character.repeat(50);
        Recipient recipient = new Recipient(line, line, line, "11122", city, "SE");
        Shipment shipment = shipment(recipient, new ShipmentReference("W".repeat(35)));
        String svg = new SvgLabelRenderer().render(shipment);
        assertThat(svg).isEqualTo(new SvgLabelRenderer().render(shipment));
        assertThat(svg.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(65536);
        var texts = checkXml(svg).getElementsByTagName("text");
        String visible = character.equals("\uFFFF") ? "[U+FFFF]"
                : character.equals("\uFFFE") ? "[U+FFFE]" : character;
        String[] fields = {"SIMULATED LABEL - NOT VALID FOR CARRIAGE", "STANDARD", "FROM NO 0150", "TO",
                visible.repeat(70), visible.repeat(70), visible.repeat(70), "11122 " + visible.repeat(50),
                "SE", "SIM0123456789", "REF " + "W".repeat(35), "BOOKED 2026-11-10"};
        int[] baselines = {70, 140, 240, 340, 410, 480, 550, 620, 690, 990, 1350, 1430};
        int next = 0;
        int previousBottom = 0;
        for (int field = 0; field < fields.length; field++) {
            int top = baselines[field] - 24;
            int bottom = top + 70;
            String actual = "";
            while (next < texts.getLength()) {
                Element text = (Element) texts.item(next);
                int y = Integer.parseInt(text.getAttribute("y"));
                if (y >= bottom) {
                    break;
                }
                int size = Integer.parseInt(text.getAttribute("font-size"));
                int x = Integer.parseInt(text.getAttribute("x"));
                String content = text.getTextContent();
                // Independent layout oracle: one em per displayed code point (half an em for ASCII spaces),
                // with one em above and below the baseline. Parsed entities count once; placeholders count fully.
                int spaces = content.length() - content.replace(" ", "").length();
                int halfEms = 2 * content.codePointCount(0, content.length()) - spaces;
                assertThat(size).isBetween(1, 24);
                assertThat(x).isEqualTo(80);
                assertThat(halfEms * size)
                        .as("field %s estimated line width in half-units", field).isLessThanOrEqualTo(1840);
                assertThat(y - size).isGreaterThanOrEqualTo(top).isGreaterThanOrEqualTo(previousBottom);
                assertThat(y + size).isLessThanOrEqualTo(bottom).isLessThanOrEqualTo(1500);
                previousBottom = y + size;
                actual += content;
                next++;
            }
            assertThat(actual).as("field %s retains every character in order", field).isEqualTo(fields[field]);
        }
        assertThat(next).isEqualTo(texts.getLength());
    }

    private static Shipment shipment(Recipient recipient, @org.jspecify.annotations.Nullable ShipmentReference reference) {
        BookableOffer offer = new BookableOffer(new QuoteId(id(901)), new OfferId(id(902)), ServiceLevel.STANDARD,
                eur("18.81"), OSLO, STOCKHOLM, Instant.parse("2026-11-10T09:30:00Z"));
        return Shipment.book(new ShipmentId(id(1)), ALPHA, new BookingCommand(offer.quoteId(), offer.offerId(), recipient,
                reference), offer, KEY, new TrackingNumber("SIM0123456789"), Instant.parse("2026-11-10T09:05:00Z"));
    }

    private static Element checkXml(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();
        assertThat(root.getAttribute("width")).isEqualTo("100mm");
        assertThat(root.getAttribute("height")).isEqualTo("150mm");
        assertThat(root.getAttribute("viewBox")).isEqualTo("0 0 1000 1500");
        assertThat(root.getAttribute("role")).isEqualTo("img");
        assertThat(root.getAttribute("aria-label")).isEqualTo("Simulated shipping label SIM0123456789");
        assertThat(doc.getElementsByTagName("title").item(0).getTextContent()).isEqualTo(root.getAttribute("aria-label"));
        Set<String> tags = Set.of("svg", "title", "rect", "text");
        Set<String> attrs = Set.of("xmlns", "width", "height", "viewBox", "role", "aria-label", "x", "y", "fill", "stroke",
                "stroke-width", "font-family", "font-size", "font-weight", "text-anchor");
        var elements = doc.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            assertThat(tags).contains(element.getTagName());
            var attributes = element.getAttributes();
            for (int j = 0; j < attributes.getLength(); j++) {
                assertThat(attrs).contains(attributes.item(j).getNodeName());
            }
            if (element.hasAttribute("font-family")) {
                assertThat(element.getAttribute("font-family")).isEqualTo("sans-serif");
            }
        }
        var bars = doc.getElementsByTagName("rect");
        assertThat(bars.getLength()).isEqualTo(10);
        int x = 80;
        for (int i = 0; i < 10; i++) {
            Element bar = (Element) bars.item(i);
            assertThat(bar.getAttribute("x")).isEqualTo(String.valueOf(x));
            assertThat(bar.getAttribute("y")).isEqualTo("1100");
            assertThat(bar.getAttribute("width")).isEqualTo(String.valueOf((i + 1) * 4));
            assertThat(bar.getAttribute("height")).isEqualTo("160");
            x += (i + 1) * 4 + 8;
        }
        return root;
    }
}
