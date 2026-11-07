package io.github.stigjohnny.shipping.architecture.fixtures.externalimports.testonly.quotes.internal.application;

import org.assertj.core.api.Assertions;

/** A negative fixture of ExternalImportPolicyTest: production code that guards with AssertJ, a test-only artifact. */
public final class QuoteChecks {

    private QuoteChecks() {
    }

    public static void requirePositive(int amount) {
        Assertions.assertThat(amount).isPositive();
    }
}
