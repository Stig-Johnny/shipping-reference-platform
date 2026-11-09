package io.github.stigjohnny.shipping.quotes.internal.adapter.out.jdbc;

import io.github.stigjohnny.shipping.quotes.api.Money;
import io.github.stigjohnny.shipping.quotes.api.OfferId;
import io.github.stigjohnny.shipping.quotes.api.Place;
import io.github.stigjohnny.shipping.quotes.api.QuoteId;
import io.github.stigjohnny.shipping.quotes.api.ServiceLevel;
import io.github.stigjohnny.shipping.quotes.internal.application.QuoteStore;
import io.github.stigjohnny.shipping.quotes.internal.domain.Offer;
import io.github.stigjohnny.shipping.quotes.internal.domain.Parcel;
import io.github.stigjohnny.shipping.quotes.internal.domain.Quote;
import io.github.stigjohnny.shipping.quotes.internal.domain.QuoteRequest;
import io.github.stigjohnny.shipping.quotes.internal.domain.TransitDays;
import io.github.stigjohnny.shipping.quotes.internal.domain.Zone;
import io.github.stigjohnny.shipping.tenants.api.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Immutable quote aggregates, with transaction-local RLS binding before each statement. */
@Repository
@Profile("postgres")
@Transactional
public class JdbcQuoteStore implements QuoteStore {
    private final JdbcClient jdbc;

    public JdbcQuoteStore(JdbcClient jdbc) { this.jdbc = Objects.requireNonNull(jdbc, "jdbc"); }

    @Override public void insert(Quote quote) {
        var request = quote.request();
        var parcel = request.parcel();
        sql(quote.tenantId(), """
                insert into quotes.quote (tenant_id, quote_id, issued_at, expires_at, weight_grams, length_mm,
                    width_mm, height_mm, billable_weight_grams, zone, origin_country, origin_postcode,
                    destination_country, destination_postcode)
                values (:tenant, :id, :issued, :expires, :weight, :length, :width, :height, :billable, :zone,
                    :originCountry, :originPostcode, :destinationCountry, :destinationPostcode)
                """)
                .param("id", quote.id().value())
                .param("issued", Timestamp.from(quote.issuedAt()))
                .param("expires", Timestamp.from(quote.expiresAt()))
                .param("weight", parcel.weightGrams())
                .param("length", parcel.lengthMm())
                .param("width", parcel.widthMm())
                .param("height", parcel.heightMm())
                .param("billable", quote.billableWeightGrams())
                .param("zone", quote.zone().name())
                .param("originCountry", request.origin().countryCode())
                .param("originPostcode", request.origin().postcode())
                .param("destinationCountry", request.destination().countryCode())
                .param("destinationPostcode", request.destination().postcode())
                .update();
        for (Offer offer : quote.offers()) {
            sql(quote.tenantId(), """
                    insert into quotes.quote_offer (tenant_id, quote_id, offer_id, service_level, amount, currency,
                        transit_min_days, transit_max_days)
                    values (:tenant, :quote, :offer, :level, :amount, :currency, :min, :max)
                    """)
                    .param("quote", quote.id().value())
                    .param("offer", offer.id().value())
                    .param("level", offer.level().name())
                    .param("amount", offer.price().amount())
                    .param("currency", offer.price().currency().getCurrencyCode())
                    .param("min", offer.transitDays().minDays())
                    .param("max", offer.transitDays().maxDays())
                    .update();
        }
    }

    @Override public Optional<Quote> find(TenantId tenant, QuoteId id) {
        // Read the immutable parent first: a visible quote committed together with all its offers. Reading offers
        // first could see none, then see a quote that committed between two READ COMMITTED statements.
        return sql(tenant, "select * from quotes.quote where tenant_id = :tenant and quote_id = :id")
                .param("id", id.value())
                .query((row, _) -> map(row, tenant, id))
                .optional();
    }

    private Quote map(ResultSet row, TenantId tenant, QuoteId id) throws SQLException {
        List<Offer> offers = sql(tenant, """
                select * from quotes.quote_offer where tenant_id = :tenant and quote_id = :id
                order by case service_level when 'ECONOMY' then 0 when 'STANDARD' then 1 else 2 end
                """)
                .param("id", id.value())
                .query((offer, _) -> new Offer(
                        new OfferId(UUID.fromString(offer.getString("offer_id"))),
                        ServiceLevel.valueOf(offer.getString("service_level")),
                        new Money(offer.getBigDecimal("amount"), Currency.getInstance(offer.getString("currency"))),
                        new TransitDays(offer.getInt("transit_min_days"), offer.getInt("transit_max_days"))))
                .list();
        return new Quote(id, tenant,
                        new QuoteRequest(new Parcel(row.getInt("weight_grams"), row.getInt("length_mm"),
                                row.getInt("width_mm"), row.getInt("height_mm")),
                                new Place(row.getString("origin_country"), row.getString("origin_postcode")),
                                new Place(row.getString("destination_country"), row.getString("destination_postcode")),
                                offers.size() == 1 ? offers.getFirst().level() : null),
                        Zone.valueOf(row.getString("zone")), row.getInt("billable_weight_grams"),
                        row.getTimestamp("issued_at").toInstant(), row.getTimestamp("expires_at").toInstant(), offers);
    }

    private JdbcClient.StatementSpec sql(TenantId tenant, String statement) {
        jdbc.sql("select set_config('app.tenant_id', :tenant, true)")
                .param("tenant", tenant.value().toString())
                .query(String.class)
                .single();
        return jdbc.sql(statement)
                .param("tenant", tenant.value());
    }
}
