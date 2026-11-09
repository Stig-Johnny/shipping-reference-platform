package io.github.stigjohnny.shipping.shipments.internal.application;

import io.github.stigjohnny.shipping.shipments.api.ShipmentId;
import io.github.stigjohnny.shipping.shipments.internal.domain.LabelOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.SvgLabelRenderer;
import java.util.Objects;

/** Rendering follows the same tenant-scoped read as shipment detail. */
public final class RenderLabel {
    private final GetShipment getShipment;
    private final SvgLabelRenderer renderer;

    public RenderLabel(GetShipment getShipment, SvgLabelRenderer renderer) {
        this.getShipment = Objects.requireNonNull(getShipment, "getShipment");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public LabelOutcome handle(ShipmentId id) {
        return getShipment.handle(id).<LabelOutcome>map(shipment ->
                new LabelOutcome.Rendered(renderer.render(shipment), shipment.trackingNumber()))
                .orElseGet(LabelOutcome.NotFound::new);
    }
}
