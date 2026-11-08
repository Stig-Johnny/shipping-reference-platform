package io.github.stigjohnny.shipping.architecture;

import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Which classes outside the API each area of it may use, each with its reason. The lists come from what the code uses
 * today, and ExternalImportPolicyTest fails on an entry that no class uses; a class that no list names is a failure.
 * The production layers and the JDK are listed class by class. A whole package is listed only for a test library
 * whose types vary with the value under test (AssertJ's assertions, ArchUnit's rule API, the contract kit's api).
 */
final class ExternalImportWhitelists {

    /** A part of the API's main or test classes that has its own whitelist. */
    enum Area {
        ROOT("root"),
        MODULE_ROOT_AND_API("module root and api"),
        DOMAIN("internal.domain"),
        APPLICATION("internal.application"),
        ADAPTER_IN_WEB("internal.adapter.in.web"),
        ADAPTER_OUT_JDBC("internal.adapter.out.jdbc"),
        MODULE_TESTS("module tests"),
        DATABASE_HARNESS("database harness"),
        DATABASE_TESTS("database tests"),
        HTTP_TESTS("http tests"),
        SCENARIO_TESTS("scenario tests"),
        SCENARIO_FIXTURES("scenario fixtures"),
        ARCHITECTURE_TESTS("architecture tests"),
        FIXTURES("architecture fixtures");

        private final String label;

        Area(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** One class, or every class of one package (not its sub-packages), with the reason an area may use it. */
    record Allowed(Area area, String name, boolean wholePackage, String reason) {

        boolean allows(String className) {
            return wholePackage ? ExternalImportPolicy.packageOf(className).equals(name) : className.equals(name);
        }
    }

    private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";
    private static final List<String> SCENARIO_FIXTURE_TYPES = List.of(
            "quotes.internal.application.ScenarioAlterations", "quotes.internal.application.GatedQuoteStore",
            "tenants.api.TenantBadge", "tenants.internal.application.ScenarioReference");
    private static final Map<Area, List<Allowed>> WHITELISTS = whitelists(List.of(
            // Only ReviewedPostgres and its nested types may reach the reviewed container libraries.
            types(Area.DATABASE_HARNESS, "ReviewedPostgres: start the reviewed image and inspect its engine and ports",
                    "org.testcontainers.DockerClientFactory", "org.testcontainers.dockerclient.TransportConfig",
                    "org.testcontainers.postgresql.PostgreSQLContainer", "org.testcontainers.utility.DockerImageName",
                    "com.github.dockerjava.api.DockerClient", "com.github.dockerjava.api.command.InfoCmd",
                    "com.github.dockerjava.api.command.InspectContainerResponse",
                    "com.github.dockerjava.api.command.InspectImageCmd",
                    "com.github.dockerjava.api.command.InspectImageResponse",
                    "com.github.dockerjava.api.command.VersionCmd", "com.github.dockerjava.api.exception.NotFoundException",
                    "com.github.dockerjava.api.model.ContainerConfig", "com.github.dockerjava.api.model.ExposedPort",
                    "com.github.dockerjava.api.model.HostConfig", "com.github.dockerjava.api.model.Info",
                    "com.github.dockerjava.api.model.NetworkSettings", "com.github.dockerjava.api.model.Ports",
                    "com.github.dockerjava.api.model.Ports$Binding", "com.github.dockerjava.api.model.Version",
                    "com.github.dockerjava.api.model.VersionComponent"),
            types(Area.DATABASE_HARNESS, "ReviewedPostgres: validate local configuration and the reviewed socket URI",
                    "java.io.IOException", "java.io.PrintStream", "java.net.URI", "java.nio.file.Files",
                    "java.nio.file.Path", "java.lang.Class", "java.lang.System"),
            types(Area.DATABASE_HARNESS, "ReviewedPostgres: generate a per-container database password",
                    "java.security.SecureRandom", "java.util.HexFormat"),
            types(Area.DATABASE_HARNESS, "ReviewedPostgres: immutable facts, validation failures and resource cleanup",
                    "java.lang.AutoCloseable", "java.lang.Boolean", "java.lang.Enum", "java.lang.IllegalStateException",
                    "java.lang.Integer", "java.lang.Object", "java.lang.Record", "java.lang.String", "java.lang.Throwable",
                    "java.util.ArrayList", "java.util.Collection", "java.util.Iterator", "java.util.List",
                    "java.util.Map", "java.util.Map$Entry", "java.util.Objects", "java.util.Optional", "java.util.Set",
                    "java.util.TreeMap", "java.util.regex.Matcher", "java.util.regex.Pattern",
                    "java.util.stream.Collectors", "java.util.stream.Stream"),
            types(Area.DATABASE_TESTS, "database tests: the harness handle supplies the JDBC connection for the query",
                    "java.sql.Connection", "java.sql.DriverManager", "java.sql.ResultSet", "java.sql.SQLException",
                    "java.sql.Statement"),
            types(Area.DATABASE_TESTS, "database tests: synthetic configuration files and environment facts",
                    "java.io.IOException", "java.nio.file.Files", "java.nio.file.Path", "java.util.HashMap",
                    "java.util.List", "java.util.Map", "java.util.Set"),
            types(Area.DATABASE_TESTS, "database tests: language types and package nullness",
                    "java.lang.Object", "java.lang.String", "java.lang.Throwable", NULL_MARKED),
            types(Area.DATABASE_TESTS, "database tests: JUnit tests, the mandatory database task tag and fixtures",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.api.Tag", "org.junit.jupiter.api.io.TempDir",
                    "org.junit.jupiter.params.ParameterizedTest", "org.junit.jupiter.params.provider.ValueSource"),
            packages(Area.DATABASE_TESTS, "AssertJ's assertions: assertThat returns an assertion type per value",
                    "org.assertj.core.api"),

            // PR #55: exact types for the JDBC fixtures and migrations, in the database test package only.
            types(Area.DATABASE_TESTS, "DatabaseServer: close the harness handle and generate isolated role passwords",
                    "java.lang.AutoCloseable", "java.lang.IllegalArgumentException",
                    "java.security.SecureRandom", "java.util.HexFormat"),
            types(Area.DATABASE_TESTS, "Jdbc and ShippingDatabase: bound values, catalog column metadata and rollback",
                    "java.sql.PreparedStatement", "java.sql.ResultSetMetaData", "java.lang.RuntimeException"),
            types(Area.DATABASE_TESTS, "Scripts and migration fixtures: read SQL resources and create altered copies",
                    "java.io.InputStream", "java.io.UncheckedIOException", "java.lang.Class", "java.lang.ClassLoader",
                    "java.net.URL", "java.net.URISyntaxException", "java.nio.charset.StandardCharsets",
                    "java.nio.file.StandardOpenOption"),
            types(Area.DATABASE_TESTS, "Migration and RLS fixtures: deterministic catalogs, rows and version sequences",
                    "java.lang.Boolean", "java.lang.Integer", "java.lang.Long", "java.lang.Record",
                    "java.lang.IllegalStateException", "java.util.ArrayList", "java.util.Arrays",
                    "java.util.Collections", "java.util.Comparator", "java.util.Iterator", "java.util.LinkedHashMap",
                    "java.util.Map$Entry", "java.util.Objects", "java.util.regex.Matcher", "java.util.regex.Pattern",
                    "java.util.stream.IntStream", "java.util.stream.Stream"),
            types(Area.DATABASE_TESTS, "Database tests: one reviewed server per class and one case per migration",
                    "org.junit.jupiter.api.BeforeAll", "org.junit.jupiter.api.AfterAll",
                    "org.junit.jupiter.params.provider.MethodSource"),
            types(Area.DATABASE_TESTS, "ShippingDatabase: configure reviewed SQL-only Flyway and inspect applied versions",
                    "org.flywaydb.core.Flyway", "org.flywaydb.core.api.configuration.FluentConfiguration",
                    "org.flywaydb.core.api.MigrationInfo", "org.flywaydb.core.api.MigrationInfoService",
                    "org.flywaydb.core.api.MigrationVersion", "org.flywaydb.core.extensibility.MigrationType"),
            types(Area.DATABASE_TESTS, "MigrationsIntegrationTest: assert migration results and changed-checksum refusal",
                    "org.flywaydb.core.api.CoreErrorCode", "org.flywaydb.core.api.ErrorDetails",
                    "org.flywaydb.core.api.exception.FlywayValidateException",
                    "org.flywaydb.core.api.output.MigrateResult", "org.flywaydb.core.api.output.ValidateOutput",
                    "org.flywaydb.core.api.output.ValidateResult"),

            // The root package: the isolated bootstrap, correlation filter and fallback problem handler.
            types(Area.ROOT, "the Spring Boot entry point", "org.springframework.boot.SpringApplication",
                    "org.springframework.boot.autoconfigure.SpringBootApplication"),
            types(Area.ROOT, "JSpecify: every package is @NullMarked (HexagonalLayeringTest)", NULL_MARKED),
            types(Area.ROOT, "the main method's types", "java.lang.Object", "java.lang.String"),

            types(Area.ROOT, "CorrelationIdFilter: the servlet chain and request/response types",
                    "jakarta.servlet.FilterChain", "jakarta.servlet.ServletException",
                    "jakarta.servlet.http.HttpServletRequest", "jakarta.servlet.http.HttpServletResponse"),
            types(Area.ROOT, "CorrelationIdFilter: propagate servlet I/O failures",
                    "java.io.IOException"),
            types(Area.ROOT, "HTTP edge: exception handling, type descriptions and JSON Pointer construction",
                    "java.lang.Class", "java.lang.Enum", "java.lang.Exception", "java.lang.Integer", "java.lang.Long",
                    "java.lang.RuntimeException", "java.lang.StringBuilder"),
            types(Area.ROOT, "HTTP problems: validate request paths and construct problem type and instance URIs",
                    "java.net.URI", "java.net.URISyntaxException"),
            types(Area.ROOT, "ShippingApplication: provide the clock injected into quote use cases",
                    "java.time.Clock"),
            types(Area.ROOT, "FallbackProblemHandler: assemble and sort bounded validation errors",
                    "java.util.ArrayList", "java.util.Comparator", "java.util.Iterator", "java.util.LinkedHashMap",
                    "java.util.List", "java.util.Map", "java.util.Objects", "java.util.stream.Collectors",
                    "java.util.stream.Stream"),
            types(Area.ROOT, "CorrelationIdFilter: represent a refused path without a null result",
                    "java.util.Optional"),
            types(Area.ROOT, "CorrelationIdFilter: generate a correlation ID when the request has none valid",
                    "java.util.UUID"),
            types(Area.ROOT, "CorrelationIdFilter: accept only correlation IDs matching the contract",
                    "java.util.regex.Matcher", "java.util.regex.Pattern"),
            types(Area.ROOT, "FallbackProblemHandler: log unexpected failures with the request correlation ID",
                    "org.slf4j.Logger", "org.slf4j.LoggerFactory"),
            types(Area.ROOT, "CorrelationIdFilter: bind and remove the request correlation ID in the logging context",
                    "org.slf4j.MDC"),
            types(Area.ROOT, "CorrelationIdFilter: select the MVC exception resolver by qualifier",
                    "org.springframework.beans.factory.annotation.Qualifier"),
            types(Area.ROOT, "ShippingApplication: customize Jackson to reject coercions outside the contract",
                    "org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer"),
            types(Area.ROOT, "FallbackProblemHandler: inspect MVC validation failures and map them to bounded errors",
                    "org.springframework.context.MessageSourceResolvable", "org.springframework.core.MethodParameter",
                    "org.springframework.validation.BindingResult", "org.springframework.validation.FieldError",
                    "org.springframework.validation.method.ParameterValidationResult",
                    "org.springframework.web.bind.MethodArgumentNotValidException",
                    "org.springframework.web.bind.annotation.ExceptionHandler",
                    "org.springframework.web.bind.annotation.PathVariable",
                    "org.springframework.web.bind.annotation.RequestHeader",
                    "org.springframework.web.bind.annotation.RequestParam",
                    "org.springframework.web.bind.annotation.RestControllerAdvice",
                    "org.springframework.web.context.request.WebRequest",
                    "org.springframework.web.method.annotation.HandlerMethodValidationException"),
            types(Area.ROOT, "ShippingApplication: declare the clock and strict JSON configuration beans",
                    "org.springframework.context.annotation.Bean"),
            types(Area.ROOT, "HTTP edge: order the correlation filter first and fallback advice last",
                    "org.springframework.core.annotation.Order"),
            types(Area.ROOT, "FallbackProblemHandler: construct RFC 9457 responses and inspect servlet-backed requests",
                    "org.springframework.http.HttpHeaders", "org.springframework.http.HttpStatus",
                    "org.springframework.http.HttpStatusCode", "org.springframework.http.MediaType",
                    "org.springframework.http.ProblemDetail", "org.springframework.http.ResponseEntity",
                    "org.springframework.http.ResponseEntity$BodyBuilder",
                    "org.springframework.http.converter.HttpMessageConverter",
                    "org.springframework.http.converter.HttpMessageNotReadableException",
                    "org.springframework.http.server.ServerHttpRequest",
                    "org.springframework.http.server.ServerHttpResponse",
                    "org.springframework.http.server.ServletServerHttpRequest"),
            types(Area.ROOT, "FallbackProblemHandler: map firewall rejections to a safe malformed-request problem",
                    "org.springframework.security.web.firewall.RequestRejectedException"),
            types(Area.ROOT, "CorrelationIdFilter: register the servlet filter as a component",
                    "org.springframework.stereotype.Component"),
            types(Area.ROOT, "CorrelationIdFilter: run once per servlet request",
                    "org.springframework.web.filter.OncePerRequestFilter"),
            types(Area.ROOT, "CorrelationIdFilter: route filter failures through MVC problem handling",
                    "org.springframework.web.servlet.HandlerExceptionResolver"),
            types(Area.ROOT, "FallbackProblemHandler: translate MVC failures and add correlation IDs to problems",
                    "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice",
                    "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler"),
            types(Area.ROOT, "FallbackProblemHandler: classify JSON input failures and extract bounded field paths",
                    "tools.jackson.core.JacksonException", "tools.jackson.core.JacksonException$Reference",
                    "tools.jackson.core.exc.InputCoercionException",
                    "tools.jackson.databind.exc.InvalidNullException",
                    "tools.jackson.databind.exc.MismatchedInputException",
                    "tools.jackson.databind.exc.UnrecognizedPropertyException"),
            types(Area.ROOT, "ShippingApplication: reject numeric and boolean inputs where the contract "
                    + "requires strings",
                    "tools.jackson.databind.cfg.CoercionAction", "tools.jackson.databind.cfg.CoercionInputShape",
                    "tools.jackson.databind.cfg.MutableCoercionConfig"),
            types(Area.ROOT, "ShippingApplication: configure the application JSON mapper",
                    "tools.jackson.databind.json.JsonMapper$Builder"),
            types(Area.ROOT, "ShippingApplication: select the logical JSON types whose coercions are forbidden",
                    "tools.jackson.databind.type.LogicalType"),

            // A module's root package (package-info only) and its api package: the values, identifiers, lookups and
            // events other modules see. JDK value types, JSpecify and Spring Modulith's two markers only.
            types(Area.MODULE_ROOT_AND_API, "Spring Modulith's markers in package-info: @ApplicationModule on the "
                    + "module, @NamedInterface on api", "org.springframework.modulith.ApplicationModule",
                    "org.springframework.modulith.NamedInterface"),
            types(Area.MODULE_ROOT_AND_API, "JSpecify: every package is @NullMarked", NULL_MARKED),
            types(Area.MODULE_ROOT_AND_API, "records, enums and the checks in their constructors",
                    "java.lang.Object", "java.lang.Record", "java.lang.Enum", "java.lang.String",
                    "java.util.Objects", "java.lang.IllegalArgumentException", "java.lang.ArithmeticException",
                    "java.lang.RuntimeException"),
            types(Area.MODULE_ROOT_AND_API, "money: an amount in a currency, rounded once (Money)",
                    "java.math.BigDecimal", "java.math.RoundingMode", "java.util.Currency"),
            types(Area.MODULE_ROOT_AND_API, "identifiers and times", "java.util.UUID", "java.time.Instant"),
            types(Area.MODULE_ROOT_AND_API, "postal code formats (Place)", "java.util.regex.Pattern",
                    "java.util.regex.Matcher"),

            // internal.domain: the JDK's value, collection and time types and JSpecify, nothing else. No Spring,
            // Jakarta or JDBC, as HexagonalLayeringTest also says, and no I/O, threads, reflection or clock reads.
            types(Area.DOMAIN, "JSpecify: every package is @NullMarked", NULL_MARKED),
            types(Area.DOMAIN, "records, enums, sealed outcomes and their checks", "java.lang.Object",
                    "java.lang.Record", "java.lang.Enum", "java.lang.String", "java.util.Objects",
                    "java.lang.IllegalArgumentException", "java.lang.IllegalStateException",
                    "java.lang.MatchException", "java.lang.FunctionalInterface", "java.lang.Math"),
            types(Area.DOMAIN, "the synthetic tariff's money arithmetic", "java.math.BigDecimal",
                    "java.math.RoundingMode", "java.util.Currency"),
            types(Area.DOMAIN, "quote lifetimes, as instants and durations passed in", "java.time.Instant",
                    "java.time.Duration", "java.time.temporal.ChronoUnit"),
            types(Area.DOMAIN, "values and collections", "java.util.List", "java.util.Set", "java.util.Optional",
                    "java.util.UUID", "java.util.stream.Stream"),
            types(Area.DOMAIN, "formats of idempotency keys, references, tracking numbers and fingerprints",
                    "java.util.regex.Pattern", "java.util.regex.Matcher"),
            types(Area.DOMAIN, "recipient text checked and counted in code points (Recipient)", "java.lang.Character",
                    "java.util.stream.IntStream"),
            types(Area.DOMAIN, "the booking request's fingerprint: SHA-256 over strictly encoded UTF-8, in memory "
                    + "(RequestFingerprint)", "java.security.MessageDigest", "java.security.NoSuchAlgorithmException",
                    "java.util.HexFormat", "java.nio.ByteBuffer", "java.nio.CharBuffer", "java.nio.charset.Charset",
                    "java.nio.charset.CharsetEncoder", "java.nio.charset.CharacterCodingException",
                    "java.nio.charset.CodingErrorAction", "java.nio.charset.StandardCharsets"),

            // internal.application: the domain's kinds of types, the injected clock, the tenant scope, and the two
            // Spring APIs the services use: events published in the transaction that changes state (ADR 0006).
            types(Area.APPLICATION, "Spring: publishing module events, inside the state change's transaction",
                    "org.springframework.context.ApplicationEventPublisher",
                    "org.springframework.transaction.annotation.Transactional"),
            types(Area.APPLICATION, "JSpecify: every package is @NullMarked", NULL_MARKED),
            types(Area.APPLICATION, "records are a language feature; MembershipResolution (PR #75) is a record",
                    "java.lang.Record"),
            types(Area.APPLICATION, "objects, checks, outcomes and one-method ports", "java.lang.Object",
                    "java.lang.String", "java.util.Objects", "java.util.Optional", "java.lang.MatchException",
                    "java.lang.Throwable", "java.lang.FunctionalInterface"),
            types(Area.APPLICATION, "the injected clock (never Instant.now())", "java.time.Clock",
                    "java.time.Instant"),
            types(Area.APPLICATION, "the caller's tenant, bound to a scope (ThreadTenantContext)",
                    "java.lang.ScopedValue", "java.lang.ScopedValue$Carrier", "java.lang.Runnable",
                    "java.util.function.Supplier"),

            // internal.adapter.in.web: the quote and membership HTTP adapters. Exact classes only.
            types(Area.ADAPTER_IN_WEB, "DemoTokenAuthenticationFilter: authenticate and continue the servlet "
                    + "request chain",
                    "jakarta.servlet.FilterChain", "jakarta.servlet.ServletException",
                    "jakarta.servlet.http.HttpServletRequest", "jakarta.servlet.http.HttpServletResponse"),
            types(Area.ADAPTER_IN_WEB, "QuoteRequestBody and QuotesController: enforce the contract bounds "
                    + "before invoking use cases",
                    "jakarta.validation.Valid", "jakarta.validation.constraints.Max",
                    "jakarta.validation.constraints.Min", "jakarta.validation.constraints.NotNull",
                    "jakarta.validation.constraints.Pattern"),
            types(Area.ADAPTER_IN_WEB, "DemoTokenAuthenticationFilter: carry checked servlet I/O failures "
                    + "through the tenant scope",
                    "java.io.IOException"),
            types(Area.ADAPTER_IN_WEB, "Web adapters: request/response records, sealed outcome mapping and "
                    + "exception propagation",
                    "java.lang.Class", "java.lang.Exception", "java.lang.IllegalStateException", "java.lang.Integer",
                    "java.lang.MatchException", "java.lang.Object", "java.lang.Record", "java.lang.RuntimeException",
                    "java.lang.String", "java.lang.Throwable"),
            types(Area.ADAPTER_IN_WEB, "QuoteBody: serialize exact money amounts as decimal strings",
                    "java.math.BigDecimal"),
            types(Area.ADAPTER_IN_WEB, "Web adapters: construct problem type URIs and quote Location headers",
                    "java.net.URI"),
            types(Area.ADAPTER_IN_WEB, "Quotes web adapter: use the injected clock and format contract "
                    + "timestamps in UTC",
                    "java.time.Clock", "java.time.Instant", "java.time.ZoneOffset",
                    "java.time.format.DateTimeFormatter"),
            types(Area.ADAPTER_IN_WEB, "Web adapters: map response collections, check dependencies and unwrap "
                    + "optional outcomes",
                    "java.util.Collection", "java.util.List", "java.util.Objects", "java.util.Optional",
                    "java.util.stream.Stream"),
            types(Area.ADAPTER_IN_WEB, "QuoteBody: serialize the money currency code",
                    "java.util.Currency"),
            types(Area.ADAPTER_IN_WEB, "Quotes and membership web adapters: map IDs and supply random IDs at the edge",
                    "java.util.UUID"),
            types(Area.ADAPTER_IN_WEB, "JSpecify: every web adapter package is @NullMarked",
                    "org.jspecify.annotations.NullMarked"),
            types(Area.ADAPTER_IN_WEB, "SecurityConfiguration: select the MVC exception resolver by qualifier",
                    "org.springframework.beans.factory.annotation.Qualifier"),
            types(Area.ADAPTER_IN_WEB, "QuotesConfiguration: inject the publisher into the quote use case",
                    "org.springframework.context.ApplicationEventPublisher"),
            types(Area.ADAPTER_IN_WEB, "Web adapter configurations: register use cases, authentication and "
                    + "filter-chain beans",
                    "org.springframework.context.annotation.Bean",
                    "org.springframework.context.annotation.Configuration"),
            types(Area.ADAPTER_IN_WEB, "AuthenticationProblems: map authentication failures before fallback advice",
                    "org.springframework.core.annotation.Order"),
            types(Area.ADAPTER_IN_WEB, "SecurityConfiguration: enable public demo tokens only in the demo profile",
                    "org.springframework.core.env.Environment"),
            types(Area.ADAPTER_IN_WEB, "Web adapters: map business outcomes to the contract responses and RFC "
                    + "9457 problems",
                    "org.springframework.http.HttpStatus", "org.springframework.http.MediaType",
                    "org.springframework.http.ProblemDetail", "org.springframework.http.ResponseEntity",
                    "org.springframework.http.ResponseEntity$BodyBuilder"),
            types(Area.ADAPTER_IN_WEB, "AuthenticationProblems: map denied access to the contract problem",
                    "org.springframework.security.access.AccessDeniedException"),
            types(Area.ADAPTER_IN_WEB, "Demo authentication: resolve bearer credentials through the membership "
                    + "provider",
                    "org.springframework.security.authentication.AbstractAuthenticationToken",
                    "org.springframework.security.authentication.AuthenticationManager",
                    "org.springframework.security.authentication.AuthenticationProvider",
                    "org.springframework.security.authentication.BadCredentialsException",
                    "org.springframework.security.authentication.ProviderManager"),
            types(Area.ADAPTER_IN_WEB, "SecurityConfiguration: build stateless tenant-member authorization with "
                    + "safe failure handlers",
                    "org.springframework.security.config.annotation.web.builders.HttpSecurity",
                    "org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer",
                    "org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer$AuthorizationManagerRequestMatcherRegistry",
                    "org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer$AuthorizedUrl",
                    "org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer",
                    "org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer",
                    "org.springframework.security.config.http.SessionCreationPolicy"),
            types(Area.ADAPTER_IN_WEB, "Demo authentication: bind the authenticated membership and its "
                    + "tenant-member authority",
                    "org.springframework.security.core.Authentication",
                    "org.springframework.security.core.AuthenticationException",
                    "org.springframework.security.core.GrantedAuthority",
                    "org.springframework.security.core.authority.SimpleGrantedAuthority",
                    "org.springframework.security.core.context.SecurityContext",
                    "org.springframework.security.core.context.SecurityContextHolder"),
            types(Area.ADAPTER_IN_WEB, "MeController: read membership from the authenticated principal",
                    "org.springframework.security.core.annotation.AuthenticationPrincipal"),
            types(Area.ADAPTER_IN_WEB, "SecurityConfiguration and authentication filter: order filters and "
                    + "route rejected requests",
                    "org.springframework.security.web.AuthenticationEntryPoint",
                    "org.springframework.security.web.SecurityFilterChain",
                    "org.springframework.security.web.access.intercept.AuthorizationFilter",
                    "org.springframework.security.web.firewall.RequestRejectedHandler"),
            types(Area.ADAPTER_IN_WEB, "Web adapters: declare quote/membership routes, request bindings and "
                    + "problem handlers",
                    "org.springframework.web.bind.annotation.ExceptionHandler",
                    "org.springframework.web.bind.annotation.GetMapping",
                    "org.springframework.web.bind.annotation.PathVariable",
                    "org.springframework.web.bind.annotation.PostMapping",
                    "org.springframework.web.bind.annotation.RequestBody",
                    "org.springframework.web.bind.annotation.RequestMapping",
                    "org.springframework.web.bind.annotation.RestController",
                    "org.springframework.web.bind.annotation.RestControllerAdvice"),
            types(Area.ADAPTER_IN_WEB, "DemoTokenAuthenticationFilter: authenticate once per servlet request",
                    "org.springframework.web.filter.OncePerRequestFilter"),
            types(Area.ADAPTER_IN_WEB, "SecurityConfiguration: route authentication and firewall failures "
                    + "through MVC problems",
                    "org.springframework.web.servlet.HandlerExceptionResolver"),
            types(Area.ADAPTER_IN_WEB, "OptionalMember: distinguish an absent request member from an explicit "
                    + "forbidden null",
                    "tools.jackson.core.JsonParser", "tools.jackson.databind.BeanProperty",
                    "tools.jackson.databind.DeserializationContext", "tools.jackson.databind.ValueDeserializer",
                    "tools.jackson.databind.annotation.JsonDeserialize",
                    "tools.jackson.databind.exc.InvalidNullException"),

            // Issue #88: exact types needed by shipments HTTP, cursor/label values and their tests.
            types(Area.ROOT, "FallbackProblemHandler: retain nested body paths during header/method validation",
                    "org.springframework.validation.method.ParameterErrors"),
            types(Area.ADAPTER_IN_WEB, "ShipmentBody: omit absent optional members, never serialize JSON null",
                    "com.fasterxml.jackson.annotation.JsonInclude", "com.fasterxml.jackson.annotation.JsonInclude$Include"),
            types(Area.ADAPTER_IN_WEB, "ShipmentsController: bind the idempotency header and pagination queries",
                    "org.springframework.web.bind.annotation.RequestHeader", "org.springframework.web.bind.annotation.RequestParam"),
            types(Area.ADAPTER_IN_WEB, "ShipmentsController: explicit UTF-8 label bytes; ShipmentProblems: cursor error fields",
                    "java.nio.charset.StandardCharsets", "java.util.Map"),
            types(Area.APPLICATION, "ListShipments and ShipmentStore: bounded keyset pages", "java.util.List"),
            types(Area.DOMAIN, "ShipmentCursor: strict base64url and instant decoding",
                    "java.util.Base64", "java.util.Base64$Encoder", "java.util.Base64$Decoder", "java.time.DateTimeException"),
            types(Area.DOMAIN, "SvgLabelRenderer: escaped text assembly and the booking date in UTC",
                    "java.lang.StringBuilder", "java.time.ZoneOffset", "java.time.format.DateTimeFormatter"),
            types(Area.MODULE_TESTS, "Shipment HTTP tests: malformed cursors; in-memory store: keyset ordering",
                    "java.util.Base64", "java.util.Base64$Encoder", "java.util.Comparator"),
            types(Area.MODULE_TESTS, "SvgLabelRendererTest: read the golden fixture and independently inspect safe XML",
                    "java.nio.file.Files", "java.nio.file.Path", "java.io.ByteArrayInputStream", "java.util.Set",
                    "javax.xml.parsers.DocumentBuilderFactory", "javax.xml.parsers.DocumentBuilder",
                    "org.w3c.dom.Document", "org.w3c.dom.Element", "org.w3c.dom.NamedNodeMap",
                    "org.w3c.dom.Node", "org.w3c.dom.NodeList"),

            // internal.adapter.out.jdbc: no adapter exists yet, so its list stays empty.

            // Tests in a module's packages: JUnit's annotations by name (so @Disabled is not among them), AssertJ,
            // and the JDK types the tests use.
            types(Area.MODULE_TESTS, "JUnit Jupiter: the test and parameterised-test annotations in use",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.params.ParameterizedTest",
                    "org.junit.jupiter.params.provider.CsvSource", "org.junit.jupiter.params.provider.MethodSource",
                    "org.junit.jupiter.params.provider.NullSource", "org.junit.jupiter.params.provider.ValueSource",
                    "org.junit.jupiter.params.provider.Arguments"),
            packages(Area.MODULE_TESTS, "AssertJ's assertions: assertThat returns an assertion type per value",
                    "org.assertj.core.api"),
            types(Area.MODULE_TESTS, "the language's types and exceptions", "java.lang.Object", "java.lang.Record",
                    "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Throwable",
                    "java.lang.AssertionError", "java.lang.IllegalArgumentException",
                    "java.lang.IllegalStateException", "java.lang.NullPointerException", "java.lang.MatchException",
                    "java.lang.InterruptedException", "java.lang.Character"),
            types(Area.MODULE_TESTS, "values, money and fixed times", "java.math.BigDecimal",
                    "java.math.RoundingMode", "java.util.Currency", "java.util.UUID", "java.util.Objects",
                    "java.util.Optional", "java.time.Clock", "java.time.Duration", "java.time.Instant",
                    "java.time.ZoneOffset"),
            types(Area.MODULE_TESTS, "collections, streams and deterministic ID sequences", "java.util.List",
                    "java.util.ArrayList", "java.util.Map", "java.util.LinkedHashMap", "java.util.Iterator",
                    "java.util.stream.Stream", "java.util.stream.IntStream",
                    "java.util.concurrent.atomic.AtomicLong", "java.util.concurrent.atomic.AtomicReference",
                    "java.util.function.Predicate"),
            types(Area.MODULE_TESTS, "the expected SHA-256 values of RequestFingerprintTest",
                    "java.security.MessageDigest", "java.security.NoSuchAlgorithmException", "java.util.HexFormat",
                    "java.nio.charset.StandardCharsets"),
            types(Area.MODULE_TESTS, "fixed-seed samples of the property tests", "java.util.SplittableRandom",
                    "java.util.random.RandomGenerator"),
            types(Area.MODULE_TESTS, "a second thread, for ThreadTenantContextTest", "java.lang.Thread",
                    "java.lang.Thread$Builder$OfPlatform"),

            types(Area.MODULE_TESTS, "DemoTokenAuthenticationTest: verify tenant binding and propagation of "
                    + "servlet-chain failures",
                    "jakarta.servlet.FilterChain", "jakarta.servlet.ServletException"),
            types(Area.MODULE_TESTS, "DemoTokenAuthenticationTest: assert checked I/O failures leave tenant "
                    + "scope correctly",
                    "java.io.IOException"),
            types(Area.MODULE_TESTS, "Module HTTP tests: propagate checked MockMvc failures to JUnit",
                    "java.lang.Exception"),
            types(Area.MODULE_TESTS, "QuotesHttpContractTest: compare returned offer collections by service level",
                    "java.util.Collection", "java.util.TreeMap"),
            types(Area.MODULE_TESTS, "Module HTTP tests: reset fixtures and run nested profile cases with "
                    + "captured startup output",
                    "org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.Nested",
                    "org.junit.jupiter.api.extension.ExtendWith"),
            types(Area.MODULE_TESTS, "Module HTTP tests: inject the application context and test doubles",
                    "org.springframework.beans.factory.annotation.Autowired"),
            types(Area.MODULE_TESTS, "Module test doubles: define Spring configurations for the unimplemented "
                    + "database ports",
                    "org.springframework.boot.test.context.TestConfiguration"),
            types(Area.MODULE_TESTS, "DemoTokenAuthenticationTest: assert startup never logs a generated "
                    + "security password",
                    "org.springframework.boot.test.system.CapturedOutput",
                    "org.springframework.boot.test.system.OutputCaptureExtension"),
            types(Area.MODULE_TESTS, "Module test doubles: register in-memory ports and deterministic quote IDs "
                    + "as test beans",
                    "org.springframework.context.annotation.Bean", "org.springframework.context.annotation.Primary"),
            types(Area.MODULE_TESTS, "Module HTTP tests: send and assert the contract media types",
                    "org.springframework.http.MediaType"),
            types(Area.MODULE_TESTS, "Module HTTP tests: inspect servlet responses and exercise authentication "
                    + "filter requests",
                    "org.springframework.mock.web.MockHttpServletRequest",
                    "org.springframework.mock.web.MockHttpServletResponse"),
            types(Area.MODULE_TESTS, "DemoTokenAuthenticationTest: assert custom authentication and absence of "
                    + "generated users",
                    "org.springframework.security.authentication.AuthenticationManager",
                    "org.springframework.security.core.context.SecurityContextHolder",
                    "org.springframework.security.core.userdetails.UserDetailsService"),
            types(Area.MODULE_TESTS, "DemoTokenAuthenticationTest: reset the context after testing a failing request",
                    "org.springframework.test.annotation.DirtiesContext",
                    "org.springframework.test.annotation.DirtiesContext$ClassMode"),
            types(Area.MODULE_TESTS, "DemoTokenAuthenticationTest: isolate nested demo and non-demo application "
                    + "contexts",
                    "org.springframework.test.context.NestedTestConfiguration",
                    "org.springframework.test.context.NestedTestConfiguration$EnclosingConfiguration"),
            types(Area.MODULE_TESTS, "Module HTTP tests: exercise quote and membership operations through MockMvc",
                    "org.springframework.test.web.servlet.MockMvc", "org.springframework.test.web.servlet.MvcResult",
                    "org.springframework.test.web.servlet.ResultActions",
                    "org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder",
                    "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"),
            types(Area.MODULE_TESTS, "Module HTTP tests: build MockMvc from the application web context",
                    "org.springframework.web.context.WebApplicationContext"),
            types(Area.MODULE_TESTS, "Module HTTP tests: inspect response JSON and construct invalid request members",
                    "tools.jackson.databind.JsonNode", "tools.jackson.databind.node.ObjectNode"),

            // Shared HTTP-edge tests and their contract harness: exact classes, including assertion types.
            types(Area.HTTP_TESTS, "Contract: validate responses against local OpenAPI schemas with "
                    + "classpath-only schema loading",
                    "com.networknt.schema.AbsoluteIri", "com.networknt.schema.InputFormat",
                    "com.networknt.schema.Schema", "com.networknt.schema.SchemaLocation",
                    "com.networknt.schema.SchemaRegistry", "com.networknt.schema.SchemaRegistry$Builder",
                    "com.networknt.schema.SchemaRegistryConfig", "com.networknt.schema.SchemaRegistryConfig$Builder",
                    "com.networknt.schema.dialect.Dialects", "com.networknt.schema.resource.SchemaLoader$Builder"),
            types(Area.HTTP_TESTS, "Contract and Http: read the local contract and servlet response bodies with "
                    + "explicit I/O failures",
                    "java.io.IOException", "java.io.InputStream", "java.io.UncheckedIOException",
                    "java.io.UnsupportedEncodingException"),
            types(Area.HTTP_TESTS, "ContractTest: construct synthetic servlet response bodies",
                    "java.io.PrintWriter"),
            types(Area.HTTP_TESTS, "HTTP tests: contract helper records, assertion failures and response value "
                    + "conversion",
                    "java.lang.AssertionError", "java.lang.Boolean", "java.lang.Exception",
                    "java.lang.IllegalStateException", "java.lang.Integer", "java.lang.Object", "java.lang.Record",
                    "java.lang.String", "java.lang.StringBuilder", "java.lang.Throwable"),
            types(Area.HTTP_TESTS, "Contract: locate the local contract through its class loader",
                    "java.lang.Class", "java.lang.ClassLoader"),
            types(Area.HTTP_TESTS, "TestClock: refuse any zone other than UTC",
                    "java.lang.UnsupportedOperationException"),
            types(Area.HTTP_TESTS, "ApiTest: compose a runtime test annotation targeting test classes",
                    "java.lang.annotation.Annotation", "java.lang.annotation.ElementType",
                    "java.lang.annotation.Retention", "java.lang.annotation.RetentionPolicy",
                    "java.lang.annotation.Target"),
            types(Area.HTTP_TESTS, "Contract and problem tests: validate and resolve local schema and problem URIs",
                    "java.net.URI", "java.net.URISyntaxException"),
            types(Area.HTTP_TESTS, "HTTP tests: decode contract and response text explicitly as UTF-8",
                    "java.nio.charset.StandardCharsets"),
            types(Area.HTTP_TESTS, "Http: write exercised operation coverage to the test report directory",
                    "java.nio.file.Files", "java.nio.file.Path"),
            types(Area.HTTP_TESTS, "TestClock: expose a mutable fixed UTC clock for deterministic HTTP tests",
                    "java.time.Clock", "java.time.Instant", "java.time.ZoneId", "java.time.ZoneOffset"),
            types(Area.HTTP_TESTS, "Contract and Http: parse paths, compare schemas and collect deterministic coverage",
                    "java.util.Collection", "java.util.Iterator", "java.util.List", "java.util.Locale",
                    "java.util.Map", "java.util.Map$Entry", "java.util.Objects", "java.util.Set", "java.util.TreeMap",
                    "java.util.TreeSet", "java.util.regex.Matcher", "java.util.regex.Pattern",
                    "java.util.stream.Stream"),
            types(Area.HTTP_TESTS, "TestClock: hold its controllable current instant",
                    "java.util.concurrent.atomic.AtomicReference"),
            types(Area.HTTP_TESTS, "HTTP tests: assert contract responses and rejection of malformed synthetic "
                    + "responses",
                    "org.assertj.core.api.AbstractBooleanAssert", "org.assertj.core.api.AbstractCollectionAssert",
                    "org.assertj.core.api.AbstractIntegerAssert", "org.assertj.core.api.AbstractStringAssert",
                    "org.assertj.core.api.AbstractThrowableAssert", "org.assertj.core.api.Assertions",
                    "org.assertj.core.api.IterableAssert", "org.assertj.core.api.ListAssert",
                    "org.assertj.core.api.MapAssert", "org.assertj.core.api.ObjectAssert"),
            types(Area.HTTP_TESTS, "HTTP tests: run contract cases, parameterized problem cases and "
                    + "captured-output assertions",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.api.extension.ExtendWith",
                    "org.junit.jupiter.params.ParameterizedTest", "org.junit.jupiter.params.provider.Arguments",
                    "org.junit.jupiter.params.provider.MethodSource",
                    "org.junit.jupiter.params.provider.ValueSource"),
            types(Area.HTTP_TESTS, "ProblemDetailsHttpContractTest: assert the correlation ID leaves the "
                    + "logging context",
                    "org.slf4j.MDC"),
            types(Area.HTTP_TESTS, "ProblemDetailsHttpContractTest: inject the application web context",
                    "org.springframework.beans.factory.annotation.Autowired"),
            types(Area.HTTP_TESTS, "ApiTest: exclude architecture fixtures from component scanning",
                    "org.springframework.boot.context.TypeExcludeFilter"),
            types(Area.HTTP_TESTS, "ApiTest: start the application for HTTP contract tests without a servlet container",
                    "org.springframework.boot.test.context.SpringBootTest"),
            types(Area.HTTP_TESTS, "TestClock: supply a deterministic clock configuration to the HTTP tests",
                    "org.springframework.boot.test.context.TestConfiguration"),
            types(Area.HTTP_TESTS, "ApiTest: register the architecture-fixture exclusion filter",
                    "org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters"),
            types(Area.HTTP_TESTS, "ProblemDetailsHttpContractTest: inspect logs of synthetic failures and "
                    + "correlation IDs",
                    "org.springframework.boot.test.system.CapturedOutput",
                    "org.springframework.boot.test.system.OutputCaptureExtension"),
            types(Area.HTTP_TESTS, "Http: discover registered servlet filters and apply their configured "
                    + "ordering to MockMvc",
                    "org.springframework.boot.web.servlet.AbstractFilterRegistrationBean",
                    "org.springframework.boot.web.servlet.ServletContextInitializerBeans"),
            types(Area.HTTP_TESTS, "TestClock: override the production clock with a primary test bean",
                    "org.springframework.context.annotation.Bean", "org.springframework.context.annotation.Primary"),
            types(Area.HTTP_TESTS, "ApiTest: alias the composed annotation profile setting",
                    "org.springframework.core.annotation.AliasFor"),
            types(Area.HTTP_TESTS, "ApiTest: identify architecture fixtures from class metadata without loading them",
                    "org.springframework.core.type.ClassMetadata",
                    "org.springframework.core.type.classreading.MetadataReader",
                    "org.springframework.core.type.classreading.MetadataReaderFactory"),
            types(Area.HTTP_TESTS, "Contract and problem tests: check HTTP methods and contract media types",
                    "org.springframework.http.HttpMethod", "org.springframework.http.MediaType"),
            types(Area.HTTP_TESTS, "Contract and Http: inspect and build synthetic servlet requests and responses",
                    "org.springframework.mock.web.MockHttpServletRequest",
                    "org.springframework.mock.web.MockHttpServletResponse"),
            types(Area.HTTP_TESTS, "ApiTest: choose the demo profile for HTTP test contexts",
                    "org.springframework.test.context.ActiveProfiles"),
            types(Area.HTTP_TESTS, "Http and problem tests: build MockMvc with real filters and exercise "
                    + "contract responses",
                    "org.springframework.test.web.servlet.MockMvc", "org.springframework.test.web.servlet.MvcResult",
                    "org.springframework.test.web.servlet.ResultActions",
                    "org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder",
                    "org.springframework.test.web.servlet.request.MockMvcRequestBuilders",
                    "org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder",
                    "org.springframework.test.web.servlet.setup.MockMvcBuilders"),
            types(Area.HTTP_TESTS, "Http and problem tests: use the started application web context",
                    "org.springframework.web.context.WebApplicationContext"),
            types(Area.HTTP_TESTS, "Contract and Http: parse response trees and serialize synthetic request bodies",
                    "tools.jackson.databind.JsonNode", "tools.jackson.databind.ObjectWriter",
                    "tools.jackson.databind.json.JsonMapper", "tools.jackson.databind.json.JsonMapper$Builder"),
            types(Area.HTTP_TESTS, "Contract: read the local OpenAPI document with the default YAML factory",
                    "tools.jackson.dataformat.yaml.YAMLMapper", "tools.jackson.dataformat.yaml.YAMLMapper$Builder"),

            // Scenario drivers and support: exact classes only, in the two exact test packages below.
            types(Area.SCENARIO_TESTS, "Coverage: inspect compiled dependencies and exact access-site descriptors",
                    "com.tngtech.archunit.core.domain.AccessTarget$CodeUnitAccessTarget",
                    "com.tngtech.archunit.core.domain.AccessTarget$FieldAccessTarget",
                    "com.tngtech.archunit.core.domain.Dependency", "com.tngtech.archunit.core.domain.JavaClass",
                    "com.tngtech.archunit.core.domain.JavaClasses", "com.tngtech.archunit.core.domain.JavaCodeUnit",
                    "com.tngtech.archunit.core.domain.JavaCodeUnitAccess",
                    "com.tngtech.archunit.core.domain.JavaFieldAccess",
                    "com.tngtech.archunit.core.domain.SourceCodeLocation"),
            types(Area.SCENARIO_TESTS, "ScenarioModel and ScenarioRun: cross-check modules against the kit's public API",
                    "io.github.stigjohnny.c4contractkit.api.ComponentResult",
                    "io.github.stigjohnny.c4contractkit.api.ComponentStatus",
                    "io.github.stigjohnny.c4contractkit.api.Conformance",
                    "io.github.stigjohnny.c4contractkit.api.Report"),
            types(Area.SCENARIO_TESTS, "Scenario files: propagate failures reading local inputs or writing reports",
                    "java.io.IOException", "java.io.UncheckedIOException"),
            types(Area.SCENARIO_TESTS, "Scenario records, enums, boxed report values and exhaustive outcome switches",
                    "java.lang.Object", "java.lang.Record", "java.lang.Enum", "java.lang.String",
                    "java.lang.Boolean", "java.lang.Integer", "java.lang.Long", "java.lang.MatchException"),
            types(Area.SCENARIO_TESTS, "Scenario validation and assertions: fail malformed inputs and retain failures",
                    "java.lang.AssertionError", "java.lang.Exception", "java.lang.IllegalArgumentException",
                    "java.lang.IllegalStateException", "java.lang.RuntimeException", "java.lang.Throwable"),
            types(Area.SCENARIO_TESTS, "ValueTypes: recognize boxed primitive and exact numeric value leaves",
                    "java.lang.Byte", "java.lang.Short", "java.lang.Float", "java.lang.Double",
                    "java.math.BigDecimal", "java.math.BigInteger", "java.util.Currency"),
            types(Area.SCENARIO_TESTS, "ScenarioModel and Sidecar: validate text characters and value types",
                    "java.lang.Character"),
            types(Area.SCENARIO_TESTS, "ScenarioRun: close each isolated application context after recording",
                    "java.lang.AutoCloseable"),
            types(Area.SCENARIO_TESTS, "ScenarioRun: retain outcome assertions until recording ends",
                    "java.lang.Runnable"),
            types(Area.SCENARIO_TESTS, "ScenarioRun: read the revision supplied by Gradle",
                    "java.lang.System"),
            types(Area.SCENARIO_TESTS, "Scenario text: construct bounded sidecar diagnostics and JSON/Markdown reports",
                    "java.lang.StringBuilder"),
            types(Area.SCENARIO_TESTS, "Comparison: bound matched interaction counts and optional order positions",
                    "java.lang.Math", "java.util.OptionalInt"),
            types(Area.SCENARIO_TESTS, "Coverage and Sidecar: resolve named classes and refuse unavailable types",
                    "java.lang.Class", "java.lang.ClassNotFoundException", "java.lang.LinkageError"),
            types(Area.SCENARIO_TESTS, "Reachability: report inaccessible fields and failed class initialization",
                    "java.lang.IllegalAccessException", "java.lang.ExceptionInInitializerError",
                    "java.lang.reflect.InaccessibleObjectException"),
            types(Area.SCENARIO_TESTS, "Coverage: retain exact JVM descriptors for ledger comparisons",
                    "java.lang.constant.ClassDesc"),
            types(Area.SCENARIO_TESTS, "Reachability and ValueTypes: inspect fields, arrays and generic value shapes",
                    "java.lang.reflect.Array", "java.lang.reflect.Field", "java.lang.reflect.GenericArrayType",
                    "java.lang.reflect.Modifier", "java.lang.reflect.ParameterizedType",
                    "java.lang.reflect.RecordComponent", "java.lang.reflect.Type"),
            types(Area.SCENARIO_TESTS, "Recorder, Coverage and Sidecar: identify API methods and test provenance",
                    "java.lang.reflect.Method"),
            types(Area.SCENARIO_TESTS, "Recorder: attribute interactions to the first application stack frame",
                    "java.lang.StackWalker", "java.lang.StackWalker$StackFrame"),
            types(Area.SCENARIO_TESTS, "Recorder: track stimulus threads and diagnose newly observed platform threads",
                    "java.lang.Thread", "java.lang.ThreadGroup", "java.lang.ThreadLocal"),
            types(Area.SCENARIO_TESTS, "ValueTypes: inspect scoped containers for value-only type arguments",
                    "java.lang.ScopedValue", "java.util.SortedMap", "java.util.SortedSet"),
            types(Area.SCENARIO_TESTS, "Scenario files: read local model, sidecar and ledger; write UTF-8 reports",
                    "java.nio.charset.StandardCharsets", "java.nio.file.Files", "java.nio.file.Path"),
            types(Area.SCENARIO_TESTS, "ScenarioRun: hash model, sidecar and main class bytes for report provenance",
                    "java.security.MessageDigest", "java.security.NoSuchAlgorithmException", "java.util.HexFormat"),
            types(Area.SCENARIO_TESTS, "Scenario wiring and outcomes: fixed UTC clocks, quote lifetimes and synthetic IDs",
                    "java.time.Clock", "java.time.Duration", "java.time.Instant", "java.time.ZoneOffset",
                    "java.util.UUID"),
            types(Area.SCENARIO_TESTS, "Scenario support: collect and compare steps, interactions, findings and values",
                    "java.util.ArrayDeque", "java.util.ArrayList", "java.util.Arrays", "java.util.Collection",
                    "java.util.Comparator", "java.util.Deque", "java.util.EnumMap", "java.util.HashMap",
                    "java.util.HashSet", "java.util.Iterator", "java.util.LinkedHashMap", "java.util.LinkedHashSet",
                    "java.util.List", "java.util.Locale", "java.util.Map", "java.util.Map$Entry", "java.util.Objects",
                    "java.util.Optional", "java.util.Set", "java.util.TreeMap", "java.util.TreeSet",
                    "java.util.stream.Collectors", "java.util.stream.IntStream", "java.util.stream.Stream"),
            types(Area.SCENARIO_TESTS, "Reachability: track object identity separately for each owning module",
                    "java.util.IdentityHashMap"),
            types(Area.SCENARIO_TESTS, "Coverage: recognize task-capable types so their dispatch remains refused",
                    "java.util.Timer", "java.util.TimerTask", "java.util.concurrent.CompletionStage",
                    "java.util.concurrent.Executor", "java.util.concurrent.ThreadFactory"),
            types(Area.SCENARIO_TESTS, "Recorder: retain stimulus thread identities across interleaved runs",
                    "java.util.concurrent.ConcurrentHashMap"),
            types(Area.SCENARIO_TESTS, "CoverageBypassTest: warm virtual threads and the pool before late-task fixtures",
                    "java.lang.Thread$Builder$OfVirtual", "java.util.concurrent.ForkJoinPool"),
            types(Area.SCENARIO_TESTS, "Scenario concurrency fixtures: bounded waits and propagation of interruption",
                    "java.lang.InterruptedException", "java.util.concurrent.CountDownLatch",
                    "java.util.concurrent.TimeUnit"),
            types(Area.SCENARIO_TESTS, "ScenarioFixturesTest: interleave two isolated runs and await their completion",
                    "java.util.concurrent.ExecutorService", "java.util.concurrent.Executors",
                    "java.util.concurrent.Future"),
            types(Area.SCENARIO_TESTS, "Reachability: identify atomic value holders and inspect reference holders",
                    "java.util.concurrent.atomic.AtomicBoolean", "java.util.concurrent.atomic.AtomicInteger",
                    "java.util.concurrent.atomic.AtomicLong", "java.util.concurrent.atomic.AtomicReference"),
            types(Area.SCENARIO_TESTS, "Recorder and ScenarioRun: execute supplied stimuli and create the multicaster",
                    "java.util.function.Supplier"),
            types(Area.SCENARIO_TESTS, "ScenarioModel and Sidecar: parse restricted DSL syntax and cardinalities",
                    "java.util.regex.Matcher", "java.util.regex.Pattern"),
            types(Area.SCENARIO_TESTS, "Recorder: intercept module API calls and publication attempts",
                    "org.aopalliance.intercept.MethodInterceptor", "org.aopalliance.intercept.MethodInvocation",
                    "org.springframework.aop.Advisor", "org.springframework.aop.framework.ProxyFactory"),
            types(Area.SCENARIO_TESTS, "Coverage and ScenarioRun: inspect observed proxies and their real targets",
                    "org.springframework.aop.TargetSource", "org.springframework.aop.framework.Advised"),
            types(Area.SCENARIO_TESTS, "Scenario tests: assert outcomes, findings, ledger drift and report claims",
                    "org.assertj.core.api.AbstractBooleanAssert", "org.assertj.core.api.AbstractCollectionAssert",
                    "org.assertj.core.api.AbstractComparableAssert", "org.assertj.core.api.AbstractInstantAssert",
                    "org.assertj.core.api.AbstractListAssert", "org.assertj.core.api.AbstractPathAssert",
                    "org.assertj.core.api.AbstractStringAssert", "org.assertj.core.api.AbstractThrowableAssert",
                    "org.assertj.core.api.Assertions", "org.assertj.core.api.ListAssert",
                    "org.assertj.core.api.MapAssert", "org.assertj.core.api.ObjectAssert",
                    "org.assertj.core.api.OptionalAssert"),
            types(Area.SCENARIO_TESTS, "JUnit: run scenarios, name reports from test metadata and isolate file fixtures",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.api.TestInfo", "org.junit.jupiter.api.io.TempDir"),
            types(Area.SCENARIO_TESTS, "CoverageBypassTest: register an unobserved singleton that coverage must refuse",
                    "org.springframework.beans.factory.config.BeanFactoryPostProcessor"),
            types(Area.SCENARIO_TESTS, "Recorder and ScenarioRun: install observers before scenario beans initialize",
                    "org.springframework.beans.factory.config.BeanPostProcessor",
                    "org.springframework.beans.factory.config.ConfigurableListableBeanFactory"),
            types(Area.SCENARIO_TESTS, "Coverage: inspect bean definitions and ready singletons for observer bypasses",
                    "org.springframework.beans.factory.support.DefaultListableBeanFactory"),
            types(Area.SCENARIO_TESTS, "Scenario wiring: explicit test configurations excluded from HTTP component scans",
                    "org.springframework.boot.test.context.TestConfiguration", "org.springframework.context.annotation.Bean"),
            types(Area.SCENARIO_TESTS, "Reachability: distinguish the run's context from unobserved event publishers",
                    "org.springframework.context.ApplicationContext", "org.springframework.context.ApplicationEventPublisher"),
            types(Area.SCENARIO_TESTS, "Recorder: unwrap application event payloads without recording their contents",
                    "org.springframework.context.PayloadApplicationEvent"),
            types(Area.SCENARIO_TESTS, "ScenarioRun and coverage fixtures: create and inspect isolated bean contexts",
                    "org.springframework.context.annotation.AnnotationConfigApplicationContext",
                    "org.springframework.context.support.GenericApplicationContext"),
            types(Area.SCENARIO_TESTS, "ScenarioRun and Recorder: install and observe the context's event multicaster",
                    "org.springframework.context.event.ApplicationEventMulticaster",
                    "org.springframework.context.event.SimpleApplicationEventMulticaster"),
            types(Area.SCENARIO_TESTS, "Coverage: discover listener methods and refuse their unobserved executions",
                    "org.springframework.context.event.EventListener",
                    "org.springframework.core.annotation.AnnotatedElementUtils", "org.springframework.util.ReflectionUtils"),
            types(Area.SCENARIO_TESTS, "Recorder: discover all module API interfaces implemented by each bean",
                    "org.springframework.util.ClassUtils"),
            types(Area.SCENARIO_TESTS, "Sidecar: parse strict local JSON trees and reject duplicate or trailing input",
                    "tools.jackson.core.JacksonException", "tools.jackson.core.StreamReadFeature",
                    "tools.jackson.databind.DeserializationFeature", "tools.jackson.databind.JsonNode",
                    "tools.jackson.databind.json.JsonMapper", "tools.jackson.databind.json.JsonMapper$Builder"),

            // Named scenario fixtures retain module packages for caller attribution; no module area gains these grants.
            types(Area.SCENARIO_FIXTURES, "Scenario fixture records, outcomes and invariant failures",
                    "java.lang.Object", "java.lang.Record", "java.lang.String", "java.lang.MatchException",
                    "java.lang.IllegalStateException", "java.lang.Throwable", "java.util.Objects"),
            types(Area.SCENARIO_FIXTURES, "Altered quote implementations: reuse the injected clock and lookup instant",
                    "java.time.Clock", "java.time.Instant"),
            types(Area.SCENARIO_FIXTURES, "GatedQuoteStore: preserve the real store's optional lookup result",
                    "java.util.Optional"),
            types(Area.SCENARIO_FIXTURES, "Async bypass fixtures: gate late calls and restore interruption on bounded waits",
                    "java.lang.InterruptedException", "java.lang.Thread", "java.util.concurrent.CountDownLatch",
                    "java.util.concurrent.TimeUnit"),
            types(Area.SCENARIO_FIXTURES, "Async bypass fixtures: tolerate the expected missing tenant on their worker",
                    "java.lang.RuntimeException"),
            types(Area.SCENARIO_FIXTURES, "LateTenantQuote: deliberately dispatch a platform thread for coverage to refuse",
                    "java.lang.Thread$Builder$OfPlatform"),
            types(Area.SCENARIO_FIXTURES, "VirtualTenantQuote: deliberately dispatch virtual work for coverage to refuse",
                    "java.lang.Thread$Builder$OfVirtual"),
            types(Area.SCENARIO_FIXTURES, "ExecutorTenantQuote: dispatch to a pre-existing pool for coverage to refuse",
                    "java.util.concurrent.ForkJoinPool"),
            types(Area.SCENARIO_FIXTURES, "CapturedTenantQuote: retain an unwrapped tenant callback the walk must find",
                    "java.util.function.Supplier"),
            types(Area.SCENARIO_FIXTURES, "Altered quote implementations: keep event outcomes while changing interactions",
                    "org.springframework.context.ApplicationEventPublisher"),
            types(Area.SCENARIO_FIXTURES, "UnledgeredSpringAccess: prove a new access to a ledgered owner still fails",
                    "org.springframework.http.ResponseEntity", "org.springframework.http.ResponseEntity$BodyBuilder"),

            // The architecture tests: they read class files, models, the provider index and the allowlist.
            packages(Area.ARCHITECTURE_TESTS, "ArchUnit: importing explicit class directories and the rule API "
                    + "(not the library or freeze packages, which its review excludes)",
                    "com.tngtech.archunit.core.domain", "com.tngtech.archunit.core.importer",
                    "com.tngtech.archunit.lang", "com.tngtech.archunit.lang.syntax",
                    "com.tngtech.archunit.lang.syntax.elements"),
            types(Area.ARCHITECTURE_TESTS, "ArchUnit: a thread-local configuration without classpath resolution",
                    "com.tngtech.archunit.ArchConfiguration"),
            packages(Area.ARCHITECTURE_TESTS, "c4-contract-kit's public API, its only exported package (ADR 0002)",
                    "io.github.stigjohnny.c4contractkit.api"),
            types(Area.ARCHITECTURE_TESTS, "Spring Modulith's module model, verified by ModularityTest",
                    "org.springframework.modulith.core.ApplicationModule",
                    "org.springframework.modulith.core.ApplicationModuleDependencies",
                    "org.springframework.modulith.core.ApplicationModuleIdentifier",
                    "org.springframework.modulith.core.ApplicationModules",
                    "org.springframework.modulith.core.Violations"),
            types(Area.ARCHITECTURE_TESTS, "Jackson's tree model: reading the provider index and the allowlist",
                    "tools.jackson.databind.JsonNode", "tools.jackson.databind.json.JsonMapper",
                    "tools.jackson.databind.json.JsonMapper$Builder", "tools.jackson.core.StreamReadFeature"),
            types(Area.ARCHITECTURE_TESTS, "JSpecify: HexagonalLayeringTest checks @NullMarked", NULL_MARKED),
            types(Area.ARCHITECTURE_TESTS, "JUnit Jupiter: tests and temporary directories",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.api.io.TempDir"),
            packages(Area.ARCHITECTURE_TESTS, "AssertJ's assertions: assertThat returns an assertion type per value",
                    "org.assertj.core.api"),
            types(Area.ARCHITECTURE_TESTS, "the JDK's compiler, for fixtures the build's compiler refuses",
                    "javax.tools.ToolProvider", "javax.tools.JavaCompiler"),
            types(Area.ARCHITECTURE_TESTS, "the JDK's module descriptors: which module holds and exports a package",
                    "java.lang.module.ModuleFinder", "java.lang.module.ModuleReference",
                    "java.lang.module.ModuleDescriptor", "java.lang.module.ModuleDescriptor$Exports",
                    "java.lang.Runtime", "java.lang.Runtime$Version"),
            types(Area.ARCHITECTURE_TESTS, "reading class directories, models and reports as files",
                    "java.io.File", "java.io.IOException", "java.io.UncheckedIOException",
                    "java.io.ByteArrayOutputStream", "java.nio.file.Files", "java.nio.file.Path",
                    "java.nio.charset.StandardCharsets", "java.net.URL", "java.net.URISyntaxException",
                    "java.lang.ClassLoader", "java.lang.Class", "java.lang.System"),
            types(Area.ARCHITECTURE_TESTS, "SHA-256 digests of the reports' inputs", "java.security.MessageDigest",
                    "java.security.NoSuchAlgorithmException", "java.util.HexFormat"),
            types(Area.ARCHITECTURE_TESTS, "the language's types and exceptions", "java.lang.Object",
                    "java.lang.Record", "java.lang.Enum", "java.lang.String", "java.lang.StringBuilder",
                    "java.lang.Integer", "java.lang.Throwable", "java.lang.AssertionError",
                    "java.lang.IllegalStateException", "java.lang.MatchException"),
            types(Area.ARCHITECTURE_TESTS, "collections, streams, text and dates", "java.util.Collection",
                    "java.util.List", "java.util.ArrayList", "java.util.Set", "java.util.HashSet",
                    "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.Map", "java.util.Map$Entry",
                    "java.util.HashMap", "java.util.TreeMap", "java.util.EnumMap", "java.util.Collections",
                    "java.util.Iterator", "java.util.Comparator",
                    "java.util.Objects", "java.util.Optional", "java.util.Locale", "java.util.function.Predicate",
                    "java.util.function.UnaryOperator", "java.util.stream.Stream", "java.util.stream.Collectors",
                    "java.util.regex.Pattern", "java.util.regex.Matcher", "java.time.LocalDate",
                    "java.time.ZoneOffset"),

            // The architecture tests' fixtures. Each class here is what one fixture needs to break one rule, and the
            // fixtures' own tests check them as the main classes they stand for.
            types(Area.FIXTURES, "JacksonTypingTest's fixtures: @JsonTypeInfo with a class id",
                    "com.fasterxml.jackson.annotation.JsonTypeInfo",
                    "com.fasterxml.jackson.annotation.JsonTypeInfo$Id"),
            types(Area.FIXTURES, "HexagonalLayeringTest's fixture: a Spring stereotype on a domain class",
                    "org.springframework.stereotype.Component"),
            types(Area.FIXTURES, "this test's fixtures: Spring in the domain, @Transactional on an api type, and "
                    + "the legitimate application service", "org.springframework.context.ApplicationEventPublisher",
                    "org.springframework.transaction.annotation.Transactional", "java.time.Clock"),
            types(Area.FIXTURES, "this test's fixture of production code that uses a test-only artifact",
                    "org.assertj.core.api.Assertions", "org.assertj.core.api.AbstractIntegerAssert"),
            types(Area.FIXTURES, "records, objects and checks", "java.lang.Object", "java.lang.Record",
                    "java.lang.String", "java.util.Objects")));

    private ExternalImportWhitelists() {
    }

    private static List<Allowed> types(Area area, String reason, String... names) {
        return Stream.of(names).map(name -> new Allowed(area, name, false, reason)).toList();
    }

    private static List<Allowed> packages(Area area, String reason, String... names) {
        return Stream.of(names).map(name -> new Allowed(area, name, true, reason)).toList();
    }

    private static Map<Area, List<Allowed>> whitelists(List<List<Allowed>> lists) {
        Map<Area, List<Allowed>> whitelists = new EnumMap<>(Area.class);
        lists.stream().flatMap(List::stream).forEach(entry -> whitelists
                .computeIfAbsent(entry.area(), area -> new ArrayList<>()).add(entry));
        whitelists.replaceAll((area, entries) -> List.copyOf(entries));
        return Collections.unmodifiableMap(whitelists);
    }

    static List<Allowed> whitelist(Area area) {
        return WHITELISTS.getOrDefault(area, List.of());
    }

    /** Every entry of every area. */
    static List<Allowed> all() {
        return WHITELISTS.values().stream().flatMap(List::stream).toList();
    }

    /**
     * The area for one compiled class, including test fixtures that must retain a module package for the recorder.
     */
    static Optional<Area> areaOfClass(Scope scope, String root, String className) {
        String harness = root + ".database.ReviewedPostgres";
        if (scope == Scope.TEST && (className.equals(harness) || className.startsWith(harness + "$"))) {
            return Optional.of(Area.DATABASE_HARNESS);
        }
        if (scope == Scope.TEST && SCENARIO_FIXTURE_TYPES.stream().anyMatch(type ->
                className.equals(root + "." + type) || className.startsWith(root + "." + type + "$"))) {
            return Optional.of(Area.SCENARIO_FIXTURES);
        }
        return area(scope, root, ExternalImportPolicy.packageOf(className));
    }

    /**
     * The area of a package below {@code root}. Main classes: the root package, a module's root or {@code api}
     * package, and the four internal layers. Test classes: the architecture tests, their fixtures, the exact http
     * package, the exact database, scenarios and scenarios.support packages, and the tests in a module's packages.
     * The reviewed database harness and named scenario fixtures are handled by areaOfClass. Any other package has
     * no area, and every class it uses fails.
     */
    static Optional<Area> area(Scope scope, String root, String packageName) {
        if (scope == Scope.TEST) {
            if (packageName.equals(root + ".database")) {
                return Optional.of(Area.DATABASE_TESTS);
            }
            if (packageName.equals(root + ".scenarios") || packageName.equals(root + ".scenarios.support")) {
                return Optional.of(Area.SCENARIO_TESTS);
            }
            if (packageName.equals(root + ".http")) {
                return Optional.of(Area.HTTP_TESTS);
            }
            if (packageName.equals(root + ".architecture")) {
                return Optional.of(Area.ARCHITECTURE_TESTS);
            }
            if (packageName.startsWith(root + ".architecture.fixtures.")) {
                return Optional.of(Area.FIXTURES);
            }
            return layer(root, packageName).map(layer -> Area.MODULE_TESTS);
        }
        if (packageName.equals(root)) {
            return Optional.of(Area.ROOT);
        }
        return layer(root, packageName);
    }

    private static Optional<Area> layer(String root, String packageName) {
        if (!packageName.startsWith(root + ".")) {
            return Optional.empty();
        }
        String inModule = packageName.substring(root.length() + 1);
        int dot = inModule.indexOf('.');
        return switch (dot < 0 ? "" : inModule.substring(dot + 1)) {
            case "", "api" -> Optional.of(Area.MODULE_ROOT_AND_API);
            case "internal.domain" -> Optional.of(Area.DOMAIN);
            case "internal.application" -> Optional.of(Area.APPLICATION);
            case "internal.adapter.in.web" -> Optional.of(Area.ADAPTER_IN_WEB);
            case "internal.adapter.out.jdbc" -> Optional.of(Area.ADAPTER_OUT_JDBC);
            default -> Optional.empty();
        };
    }
}
