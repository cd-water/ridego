package com.cdwater.ridego.contracts;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventContractTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final String TRIP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OFFER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String INVOICE_ID = "33333333-3333-3333-3333-333333333333";

    private static final TripRequestedPayload TRIP_REQUESTED = new TripRequestedPayload(
            TRIP_ID, "rider-1", "city-1", 31.2304, 121.4737, 31.24, 121.49);
    private static final String TRIP_REQUESTED_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111","riderId":"rider-1","cityId":"city-1",\
            "pickupLat":31.2304,"pickupLng":121.4737,"dropoffLat":31.24,"dropoffLng":121.49}""";

    private static final TripLifecyclePayload TRIP_LIFECYCLE =
            new TripLifecyclePayload(TRIP_ID);
    private static final String TRIP_LIFECYCLE_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111"}""";

    private static final TripOfferedPayload TRIP_OFFERED =
            new TripOfferedPayload(TRIP_ID, "driver-1", OFFER_ID);
    private static final String TRIP_OFFERED_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111","driverId":"driver-1",\
            "offerId":"22222222-2222-2222-2222-222222222222"}""";

    private static final TripDriverPayload TRIP_DRIVER =
            new TripDriverPayload(TRIP_ID, "driver-1");
    private static final String TRIP_DRIVER_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111","driverId":"driver-1"}""";

    private static final TripCompletedPayload TRIP_COMPLETED = new TripCompletedPayload(
            TRIP_ID, "rider-1", "driver-1",
            Instant.parse("2026-11-13T10:05:00Z"), Instant.parse("2026-11-13T10:20:00Z"),
            31.2304, 121.4737, 31.24, 121.49);
    private static final String TRIP_COMPLETED_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111","riderId":"rider-1","driverId":"driver-1",\
            "startedAt":"2026-11-13T10:05:00Z","completedAt":"2026-11-13T10:20:00Z",\
            "pickupLat":31.2304,"pickupLng":121.4737,"dropoffLat":31.24,"dropoffLng":121.49}""";

    private static final TripCancelledPayload TRIP_CANCELLED =
            new TripCancelledPayload(TRIP_ID, "rider", "rider_changed_mind");
    private static final String TRIP_CANCELLED_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111","actor":"rider",\
            "reason":"rider_changed_mind"}""";

    private static final TripDriverCancelledPayload TRIP_DRIVER_CANCELLED =
            new TripDriverCancelledPayload(TRIP_ID, "driver-1", "vehicle_issue");
    private static final String TRIP_DRIVER_CANCELLED_JSON = """
            {"tripId":"11111111-1111-1111-1111-111111111111","driverId":"driver-1",\
            "reason":"vehicle_issue"}""";

    private static final OfferLifecyclePayload OFFER = new OfferLifecyclePayload(
            OFFER_ID, TRIP_ID, "driver-1", Instant.parse("2026-11-13T10:00:20Z"));
    private static final String OFFER_JSON = """
            {"offerId":"22222222-2222-2222-2222-222222222222",\
            "tripId":"11111111-1111-1111-1111-111111111111","driverId":"driver-1",\
            "expiresAt":"2026-11-13T10:00:20Z"}""";

    private static final OfferLifecyclePayload OFFER_ACCEPT_REJECTED =
            new OfferLifecyclePayload(OFFER_ID, TRIP_ID, "driver-1", null);
    private static final String OFFER_ACCEPT_REJECTED_JSON = """
            {"offerId":"22222222-2222-2222-2222-222222222222",\
            "tripId":"11111111-1111-1111-1111-111111111111","driverId":"driver-1","expiresAt":null}""";

    private static final DriverPresenceChangedPayload PRESENCE =
            new DriverPresenceChangedPayload("driver-1", "city-1", true);
    private static final String PRESENCE_JSON = """
            {"driverId":"driver-1","cityId":"city-1","online":true}""";

    private static final InvoicePayload INVOICE_ISSUED =
            new InvoicePayload(INVOICE_ID, TRIP_ID, "rider-1", 0, "ISSUED");
    private static final String INVOICE_ISSUED_JSON = """
            {"invoiceId":"33333333-3333-3333-3333-333333333333",\
            "tripId":"11111111-1111-1111-1111-111111111111","riderId":"rider-1",\
            "amountCents":0,"state":"ISSUED"}""";

    private static final InvoicePayload INVOICE_PAID =
            new InvoicePayload(INVOICE_ID, TRIP_ID, "rider-1", 0, "PAID");
    private static final String INVOICE_PAID_JSON = """
            {"invoiceId":"33333333-3333-3333-3333-333333333333",\
            "tripId":"11111111-1111-1111-1111-111111111111","riderId":"rider-1",\
            "amountCents":0,"state":"PAID"}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventEnvelopeCodec codec =
            new EventEnvelopeCodec(objectMapper, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

    @Test
    void fixturesMatchTypedPayloadsInBothDirections() {
        for (Row row : rows()) {
            Object decoded = objectMapper.readValue(row.payloadJson(), row.payload().getClass());
            assertEquals(row.payload(), decoded, row.eventType() + " decode");

            JsonNode encoded = objectMapper.readTree(
                    objectMapper.writeValueAsString(row.payload()));
            assertEquals(objectMapper.readTree(row.payloadJson()), encoded,
                    row.eventType() + " encode");
        }
    }

    @Test
    void everyDeclaredEventTypeHasExactlyOneFixture() {
        Set<String> fixtureTypes = rows().stream()
                .map(Row::eventType)
                .collect(Collectors.toSet());
        assertEquals(rows().size(), fixtureTypes.size(), "duplicate fixture rows");
        assertEquals(declaredEventTypes(), fixtureTypes);
    }

    @Test
    void decodesFullEnvelopeFixture() {
        String json = """
                {"eventId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","eventType":"TripRequested",\
                "eventVersion":1,"occurredAt":"2026-11-13T10:00:00Z",\
                "aggregateId":"11111111-1111-1111-1111-111111111111","cityId":"city-1",\
                "correlationId":"11111111-1111-1111-1111-111111111111","causationId":null,\
                "payload":%s}""".formatted(TRIP_REQUESTED_JSON);

        EventEnvelope<TripRequestedPayload> event =
                codec.decode(json, TripRequestedPayload.class);

        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", event.eventId());
        assertEquals(EventTypes.TRIP_REQUESTED, event.eventType());
        assertEquals(1, event.eventVersion());
        assertEquals(NOW, event.occurredAt());
        assertEquals(TRIP_ID, event.aggregateId());
        assertEquals("city-1", event.cityId());
        assertEquals(TRIP_ID, event.correlationId());
        assertNull(event.causationId());
        assertEquals(TRIP_REQUESTED, event.payload());
    }

    @Test
    void unknownEventTypeDecodesWithoutFailing() {
        String json = """
                {"eventId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","eventType":"SomethingNew",\
                "eventVersion":1,"occurredAt":"2026-11-13T10:00:00Z","aggregateId":"agg-1",\
                "cityId":null,"correlationId":null,"causationId":null,\
                "payload":{"foo":"bar"}}""";

        EventEnvelope<JsonNode> event = codec.decode(json);

        assertEquals("SomethingNew", event.eventType());
        assertEquals("bar", event.payload().get("foo").asText());
    }

    @Test
    void topicsHaveStableWireNames() {
        assertEquals("trip.events.v1", Topics.TRIP_EVENTS);
        assertEquals("dispatch.events.v1", Topics.DISPATCH_EVENTS);
        assertEquals("location.events.v1", Topics.LOCATION_EVENTS);
        assertEquals("location.driver-positions.v1", Topics.DRIVER_POSITIONS);
        assertEquals("billing.events.v1", Topics.BILLING_EVENTS);
    }

    private static List<Row> rows() {
        return List.of(
                new Row(EventTypes.TRIP_REQUESTED, TRIP_REQUESTED, TRIP_REQUESTED_JSON),
                new Row(EventTypes.TRIP_MATCHING_STARTED, TRIP_LIFECYCLE, TRIP_LIFECYCLE_JSON),
                new Row(EventTypes.TRIP_OFFERED, TRIP_OFFERED, TRIP_OFFERED_JSON),
                new Row(EventTypes.TRIP_MATCHING_RESTARTED, TRIP_REQUESTED, TRIP_REQUESTED_JSON),
                new Row(EventTypes.TRIP_ACCEPTED, TRIP_DRIVER, TRIP_DRIVER_JSON),
                new Row(EventTypes.TRIP_ARRIVED, TRIP_DRIVER, TRIP_DRIVER_JSON),
                new Row(EventTypes.TRIP_STARTED, TRIP_DRIVER, TRIP_DRIVER_JSON),
                new Row(EventTypes.TRIP_COMPLETED, TRIP_COMPLETED, TRIP_COMPLETED_JSON),
                new Row(EventTypes.TRIP_CANCELLED, TRIP_CANCELLED, TRIP_CANCELLED_JSON),
                new Row(EventTypes.TRIP_DRIVER_CANCELLED, TRIP_DRIVER_CANCELLED,
                        TRIP_DRIVER_CANCELLED_JSON),
                new Row(EventTypes.TRIP_ACCEPT_REJECTED, OFFER_ACCEPT_REJECTED,
                        OFFER_ACCEPT_REJECTED_JSON),
                new Row(EventTypes.TRIP_UNMATCHED, TRIP_LIFECYCLE, TRIP_LIFECYCLE_JSON),
                new Row(EventTypes.MATCHING_STARTED, TRIP_LIFECYCLE, TRIP_LIFECYCLE_JSON),
                new Row(EventTypes.OFFER_CREATED, OFFER, OFFER_JSON),
                new Row(EventTypes.OFFER_ACCEPTED, OFFER, OFFER_JSON),
                new Row(EventTypes.OFFER_EXPIRED, OFFER, OFFER_JSON),
                new Row(EventTypes.OFFER_REJECTED, OFFER, OFFER_JSON),
                new Row(EventTypes.DRIVER_PRESENCE_CHANGED, PRESENCE, PRESENCE_JSON),
                new Row(EventTypes.INVOICE_ISSUED, INVOICE_ISSUED, INVOICE_ISSUED_JSON),
                new Row(EventTypes.INVOICE_PAID, INVOICE_PAID, INVOICE_PAID_JSON));
    }

    private static Set<String> declaredEventTypes() {
        return Arrays.stream(EventTypes.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == String.class)
                .map(EventContractTest::stringValue)
                .collect(Collectors.toSet());
    }

    private static String stringValue(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Row(String eventType, Object payload, String payloadJson) {
    }
}
