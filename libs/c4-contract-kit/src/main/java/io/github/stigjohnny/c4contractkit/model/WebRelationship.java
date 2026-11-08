package io.github.stigjohnny.c4contractkit.model;

/** A relationship between two components of the web container, by {@code web.id}, and its {@code web.mode}. */
public record WebRelationship(String source, String target, RelationshipMode mode) {
}
