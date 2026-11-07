package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Relationship;

/** The one container with {@code contract.root}, and how its components and their relationships are recognised. */
record CheckedContainer(Container container, String name) {

    boolean isComponent(Element element) {
        return element instanceof Component component && container.equals(component.getContainer());
    }

    boolean connectsTwoComponents(Relationship relationship) {
        return isComponent(relationship.getSource()) && isComponent(relationship.getDestination());
    }
}
