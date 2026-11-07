package io.github.stigjohnny.shipping.architecture.fixtures.jackson.quotes.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** A negative fixture of JacksonTypingTest: a type whose JSON names the class to create. */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface Payload {
}
