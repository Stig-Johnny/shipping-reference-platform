package io.github.stigjohnny.shipping.quotes.internal.domain;

/** Why nothing can be offered: the first of the tariff's availability rules that fails, in this order. */
public enum UnavailableReason {
    /** The origin or the destination is not one of the six served countries. */
    ROUTE_NOT_SERVED,
    /** The billable weight is above 30000 g. */
    BILLABLE_WEIGHT_ABOVE_LIMIT,
    /** The requested level is not available for the parcel: EXPRESS above 10000 billable grams. */
    SERVICE_LEVEL_NOT_AVAILABLE
}
