package com.cdwater.ridego.contracts;

public final class EventTypes {

    public static final String TRIP_REQUESTED = "TripRequested";
    public static final String TRIP_MATCHING_STARTED = "TripMatchingStarted";
    public static final String TRIP_OFFERED = "TripOffered";
    public static final String TRIP_MATCHING_RESTARTED = "TripMatchingRestarted";
    public static final String TRIP_ACCEPTED = "TripAccepted";
    public static final String TRIP_ARRIVED = "TripArrived";
    public static final String TRIP_STARTED = "TripStarted";
    public static final String TRIP_COMPLETED = "TripCompleted";
    public static final String TRIP_CANCELLED = "TripCancelled";
    public static final String TRIP_UNMATCHED = "TripUnmatched";

    public static final String MATCHING_STARTED = "MatchingStarted";
    public static final String OFFER_CREATED = "OfferCreated";
    public static final String OFFER_ACCEPTED = "OfferAccepted";
    public static final String OFFER_EXPIRED = "OfferExpired";
    public static final String OFFER_REJECTED = "OfferRejected";

    public static final String DRIVER_PRESENCE_CHANGED = "DriverPresenceChanged";

    public static final String INVOICE_ISSUED = "InvoiceIssued";
    public static final String INVOICE_PAID = "InvoicePaid";

    private EventTypes() {
    }
}
