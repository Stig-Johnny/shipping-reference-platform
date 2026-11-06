package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.Workspace;
import com.structurizr.dsl.IdentifiersRegister;

/** A parsed workspace with the DSL identifiers its elements were given. */
record ParsedModel(Workspace workspace, IdentifiersRegister identifiers) {
}
