package io.github.stigjohnny.docs.markdown;

/** Decides what each relative or external link of a document becomes; in-page anchors the renderer checks itself. */
@FunctionalInterface
public interface LinkResolver {

    LinkDecision resolve(String destination);
}
