package io.github.stigjohnny.shipping.database;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.ALPHA;
import static io.github.stigjohnny.shipping.quotes.internal.domain.QuoteFixtures.exampleQuote;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.KEY;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.command;
import static io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentFixtures.sequentialIds;

import io.github.stigjohnny.shipping.http.TestClock;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteOffersService;
import io.github.stigjohnny.shipping.shipments.internal.adapter.out.jdbc.JdbcTrackingNumbers;
import io.github.stigjohnny.shipping.shipments.internal.application.BookShipment;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingCommand;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingOutcome;
import io.github.stigjohnny.shipping.shipments.internal.domain.BookingRejection;
import io.github.stigjohnny.shipping.shipments.internal.domain.IdempotencyKey;
import io.github.stigjohnny.shipping.shipments.internal.domain.ShipmentReference;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Actual overlapping runtime-role transactions, including a lock-wait observation through PostgreSQL's catalog. */
@Tag("database")
class BookingIdempotencyIntegrationTest {
    private static DatabaseServer server;
    @BeforeAll static void start() { server = DatabaseServer.start(); }
    @AfterAll static void stop() { server.close(); }

    @Test void concurrentDuplicatesReplayTheCommittedShipmentEvenAfterExpiry() throws Exception {
        concurrent("same_key", false, false, false);
    }
    @Test void concurrentDifferentBodiesWithTheSameKeyAreRefused() throws Exception {
        concurrent("changed_body", true, false, false);
    }
    @Test void concurrentDifferentKeysCannotBookOneQuoteTwice() throws Exception {
        concurrent("same_quote", false, true, false);
    }
    @Test void aWaiterRechecksExpiryAfterTheFirstBookingRollsBack() throws Exception {
        concurrent("rollback_expiry", false, false, true);
    }

    private static void concurrent(String name, boolean changedBody, boolean otherKey, boolean rollback)
            throws Exception {
        ShippingDatabase database = server.newDatabase(name).migrate();
        var quote = exampleQuote(ALPHA, QuoteStoreContract.ISSUED);
        BookingCommand request = command(quote.id(), quote.offers().getFirst().id());
        try (JdbcStoreSession seed = new JdbcStoreSession(database)) {
            seed.quotes().insert(quote);
            seed.connection.commit();
        }
        TestClock firstClock = new TestClock();
        firstClock.set(quote.issuedAt());
        TestClock secondClock = new TestClock();
        secondClock.set(quote.issuedAt());
        try (JdbcStoreSession first = new JdbcStoreSession(database);
                JdbcStoreSession second = new JdbcStoreSession(database);
                var workers = Executors.newSingleThreadExecutor()) {
            List<Object> firstEvents = new ArrayList<>();
            List<Object> secondEvents = new ArrayList<>();
            BookShipment firstBooking = booking(first, firstClock, firstEvents);
            BookingOutcome booked = firstBooking.handle(KEY, request);
            assertThat(booked).isInstanceOf(BookingOutcome.Booked.class);
            long waitingPid = Long.parseLong(Jdbc.value(second.connection, "select pg_backend_pid()::text"));
            BookShipment secondBooking = booking(second, secondClock, secondEvents);
            BookingCommand secondRequest = changedBody ? new BookingCommand(request.quoteId(), request.offerId(),
                    request.recipient(), new ShipmentReference("CHANGED")) : request;
            IdempotencyKey secondKey = otherKey ? new IdempotencyKey("synthetic-other-key-0001") : KEY;
            var waiting = workers.submit(() -> {
                try {
                    BookingOutcome outcome = secondBooking.handle(secondKey, secondRequest);
                    second.connection.commit();
                    return outcome;
                } catch (SQLException | RuntimeException failure) {
                    second.connection.rollback();
                    throw failure;
                }
            });
            // Always release the first transaction before closing the executor, even if the wait assertion fails.
            try {
                awaitAdvisoryWait(database, waitingPid);
                if (!otherKey) {
                    secondClock.set(quote.expiresAt());
                }
            } finally {
                if (rollback) { first.connection.rollback(); } else { first.connection.commit(); }
            }
            BookingOutcome outcome = waiting.get(10, TimeUnit.SECONDS);
            if (rollback) {
                assertThat(outcome).isEqualTo(new BookingOutcome.Rejected(BookingRejection.QUOTE_EXPIRED));
            } else if (changedBody) {
                assertThat(outcome).isEqualTo(new BookingOutcome.Rejected(BookingRejection.IDEMPOTENCY_KEY_REUSED));
            } else if (otherKey) {
                assertThat(outcome).isEqualTo(new BookingOutcome.Rejected(BookingRejection.QUOTE_ALREADY_BOOKED));
            } else {
                assertThat(outcome).isEqualTo(new BookingOutcome.Replayed(((BookingOutcome.Booked) booked).shipment()));
                assertThat(secondBooking.handle(KEY, request)).isEqualTo(outcome); // Later replay, too.
            }
            assertThat(firstEvents).hasSize(1);
            assertThat(secondEvents).isEmpty();
        }
        try (Connection app = database.app()) {
            app.setAutoCommit(false);
            Jdbc.bind(app, Fixtures.ALPHA);
            assertThat(Jdbc.count(app, "shipments.shipment")).isEqualTo(rollback ? 0L : 1L);
            app.rollback();
        }
    }

    private static BookShipment booking(JdbcStoreSession session, TestClock clock, List<Object> events) {
        return new BookShipment(clock, sequentialIds(), () -> ALPHA, new QuoteOffersService(session.quotes()),
                events::add, session.shipments(), new JdbcTrackingNumbers(session.client, () -> ALPHA));
    }

    private static void awaitAdvisoryWait(ShippingDatabase database, long pid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try (Connection catalog = database.superuser()) {
            while (System.nanoTime() < deadline) {
                if (Jdbc.value(catalog, "select count(*) from pg_locks where pid = ? and locktype = 'advisory'"
                        + " and not granted", pid).equals("1")) {
                    return;
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }
        }
        throw new IllegalStateException("the duplicate did not wait on the booking lock");
    }
}
