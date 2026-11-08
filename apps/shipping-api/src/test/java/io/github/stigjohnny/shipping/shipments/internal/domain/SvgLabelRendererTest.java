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

    private static Shipment shipment(Recipient recipient, @org.jspecify.annotations.Nullable ShipmentReference reference) {
        BookableOffer offer = new BookableOffer(new QuoteId(id(901)), new OfferId(id(902)), ServiceLevel.STANDARD,
                eur("18.81"), OSLO, STOCKHOLM, Instant.parse("2026-11-10T09:30:00Z"));
        return Shipment.book(new ShipmentId(id(1)), ALPHA, new BookingCommand(offer.quoteId(), offer.offerId(), recipient,
                reference), offer, KEY, new TrackingNumber("SIM0123456789"), Instant.parse("2026-11-10T09:05:00Z"));
    }

    private static void checkXml(String svg) throws Exception {
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
    }
}
