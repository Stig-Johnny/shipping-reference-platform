package io.github.stigjohnny.shipping.architecture.fixtures.externalimports.wronglayer.quotes.api;

import org.springframework.transaction.annotation.Transactional;

/**
 * A negative fixture of ExternalImportPolicyTest: an api type that uses {@code @Transactional}. spring-tx is reviewed
 * for the API's main classes, and internal.application may use it; the api package may not.
 */
public interface QuoteCommands {

    @Transactional
    void issue();
}
