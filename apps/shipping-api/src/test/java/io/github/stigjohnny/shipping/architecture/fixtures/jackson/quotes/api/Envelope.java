package io.github.stigjohnny.shipping.architecture.fixtures.jackson.quotes.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A negative fixture of JacksonTypingTest: a member whose JSON names the class to create. */
public record Envelope(@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS) Object content) {
}
