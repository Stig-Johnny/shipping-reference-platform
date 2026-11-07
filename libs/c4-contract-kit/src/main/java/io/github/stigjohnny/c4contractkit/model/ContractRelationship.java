package io.github.stigjohnny.c4contractkit.model;

/** A relationship between two components of the checked container, by {@code contract.id}. */
public record ContractRelationship(String source, String target, RelationshipMode mode) {
}
