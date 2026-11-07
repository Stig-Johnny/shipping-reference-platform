package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.dsl.IdentifiersRegister;
import com.structurizr.model.Element;
import com.structurizr.model.Relationship;
import java.util.regex.Pattern;

/**
 * Names elements in problems the way model authors find them: by DSL identifier. An element without an identifier
 * gets a random one from the parser, which would make problems differ between runs; it is named by its canonical name.
 */
final class ElementNames {

    private static final Pattern GENERATED =
            Pattern.compile("(.*\\.)?\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    private final IdentifiersRegister identifiers;

    ElementNames(IdentifiersRegister identifiers) {
        this.identifiers = identifiers;
    }

    String of(Element element) {
        String identifier = identifiers.findIdentifier(element);
        return identifier == null || GENERATED.matcher(identifier).matches() ? element.getCanonicalName() : identifier;
    }

    String of(Relationship relationship) {
        return of(relationship.getSource()) + " -> " + of(relationship.getDestination());
    }
}
