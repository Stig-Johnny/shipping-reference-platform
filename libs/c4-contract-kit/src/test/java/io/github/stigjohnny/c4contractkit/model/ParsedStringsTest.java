package io.github.stigjohnny.c4contractkit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.structurizr.Workspace;
import com.structurizr.configuration.Role;
import com.structurizr.dsl.IdentifiersRegister;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.ContainerInstance;
import com.structurizr.model.CustomElement;
import com.structurizr.model.DeploymentNode;
import com.structurizr.model.HttpHealthCheck;
import com.structurizr.model.InfrastructureNode;
import com.structurizr.model.Model;
import com.structurizr.model.Person;
import com.structurizr.model.Perspective;
import com.structurizr.model.Relationship;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.model.StaticStructureElementInstance;
import com.structurizr.view.ComponentView;
import com.structurizr.view.Configuration;
import com.structurizr.view.DeploymentView;
import com.structurizr.view.DynamicView;
import com.structurizr.view.ElementStyle;
import com.structurizr.view.FilterMode;
import com.structurizr.view.RelationshipStyle;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.Terminology;
import com.structurizr.view.ViewSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The check after parsing, on workspaces built in code: a <code>${</code> in any text that {@link ParsedStrings}
 * reads is found, and named by its place. No model file is read, so the check before parsing does not run.
 *
 * <p>Structurizr 6.2.4 validates some of the texts that the walk reads, and none of those can hold <code>${</code>:
 * URLs (of elements, relationships, perspectives, relationships in views and health checks), colours, and the number
 * of instances of a deployment node. The enterprise term has no public setter. The walk reads them all the same.
 */
class ParsedStringsTest {

    private static final String DOLLAR_BRACE = "${";

    /** One object of every kind that the walk reads, with a text in every field it reads, and no ${ anywhere. */
    record Fixture(Workspace workspace, IdentifiersRegister identifiers, SoftwareSystem shop, Container app,
            Component orders, Perspective security, Person clerk, CustomElement custom, Relationship uses,
            Perspective cost, DeploymentNode server, InfrastructureNode gateway, ContainerInstance appInstance,
            HttpHealthCheck health, ComponentView components, DynamicView flow, RelationshipView step,
            DeploymentView live, ElementStyle elementStyle, RelationshipStyle relationshipStyle) {

        static Fixture create() {
            Workspace workspace = new Workspace("Shop", "Sells things");
            workspace.setVersion("1");
            workspace.setThumbnail("thumbnail");
            workspace.setLastModifiedUser("owner");
            workspace.setLastModifiedAgent("agent");
            workspace.addProperty("owner", "team");
            workspace.getConfiguration().addUser("owner@example.com", Role.ReadWrite);
            Model model = workspace.getModel();
            model.addProperty("m", "value");
            SoftwareSystem shop = model.addSoftwareSystem("Shop", "Sells things");
            Container app = shop.addContainer("App", "Takes orders", "Java");
            app.setUrl("https://example.com/app");
            Component orders = app.addComponent("Orders", "Keeps orders", "Java");
            orders.setGroup("Core");
            orders.addTags("Core");
            orders.addProperty("contract.id", "orders");
            Perspective security = orders.addPerspective("Security", "Checked", "high");
            security.setUrl("https://example.com/security");
            Person clerk = model.addPerson("Clerk", "Sells");
            CustomElement custom = model.addCustomElement("Custom", "Box", "A box");
            Relationship uses = clerk.uses(app, "Uses", "HTTPS");
            uses.setUrl("https://example.com/uses");
            Perspective cost = uses.addPerspective("Cost", "Cheap", "low");
            DeploymentNode server = model.addDeploymentNode("Live", "Server", "A server", "Linux");
            server.setInstances(2);
            server.addDeploymentGroup("Blue");
            InfrastructureNode gateway = server.addInfrastructureNode("Gateway", "Routes", "nginx");
            ContainerInstance appInstance = server.add(app, "Blue");
            HttpHealthCheck health = addHealthCheck(appInstance, "Health");
            health.addHeader("Accept", "text/plain");
            ViewSet views = workspace.getViews();
            ComponentView components = views.createComponentView(app, "Components", "Every component");
            components.setTitle("Components of the app");
            components.addProperty("shown", "yes");
            DynamicView flow = views.createDynamicView(shop, "Flow", "The flow");
            RelationshipView step = flow.add(clerk, "Asks", app);
            step.setUrl("https://example.com/step");
            step.addProperty("p", "v");
            DeploymentView live = views.createDeploymentView(shop, "LiveDeployment", "Live");
            live.setEnvironment("Live");
            live.add(server);
            views.createFilteredView(components, "Filtered", "Some", FilterMode.Include, "Element");
            Configuration configuration = views.getConfiguration();
            configuration.setDefaultView(components);
            configuration.addProperty("set", "yes");
            configuration.getTerminology().setPerson("Actor");
            ElementStyle elementStyle = configuration.getStyles().addElementStyle("Element");
            elementStyle.setBackground("#ffffff");
            elementStyle.setStroke("#000000");
            elementStyle.setColor("#000000");
            elementStyle.setIcon("https://example.com/icon.png");
            elementStyle.addProperty("p", "v");
            RelationshipStyle relationshipStyle = configuration.getStyles().addRelationshipStyle("Relationship");
            relationshipStyle.setColor("#000000");
            relationshipStyle.addProperty("p", "v");
            IdentifiersRegister identifiers = RestrictedDslParser.newParser().getIdentifiersRegister();
            identifiers.register("shop", shop);
            identifiers.register("app", app);
            identifiers.register("orders", orders);
            identifiers.register("clerk", clerk);
            identifiers.register("custom", custom);
            identifiers.register("server", server);
            identifiers.register("gateway", gateway);
            identifiers.register("appInstance", appInstance);
            return new Fixture(workspace, identifiers, shop, app, orders, security, clerk, custom, uses, cost, server,
                    gateway, appInstance, health, components, flow, step, live, elementStyle, relationshipStyle);
        }

        ElementNames names() {
            return new ElementNames(identifiers);
        }

        ViewSet views() {
            return workspace.getViews();
        }

        Terminology terms() {
            return views().getConfiguration().getTerminology();
        }
    }

    @Test
    void theFixtureHoldsNoDollarBrace() {
        assertThat(placesWithDollarBrace(Fixture.create())).isEmpty();
    }

    /**
     * Every text that the walk reads and Structurizr lets a ${ into.
     *
     * @return the text, how to put a ${ into it, and the places the walk names then
     */
    static Stream<Arguments> everyTextTheWalkReads() {
        return Stream.of(
                text("the name of the workspace", f -> f.workspace().setName(DOLLAR_BRACE), "workspace"),
                text("the description of the workspace", f -> f.workspace().setDescription(DOLLAR_BRACE),
                        "workspace"),
                text("the version of the workspace", f -> f.workspace().setVersion(DOLLAR_BRACE), "workspace"),
                text("the thumbnail of the workspace", f -> f.workspace().setThumbnail(DOLLAR_BRACE), "workspace"),
                text("the user who last modified the workspace",
                        f -> f.workspace().setLastModifiedUser(DOLLAR_BRACE), "workspace"),
                text("the agent that last modified the workspace",
                        f -> f.workspace().setLastModifiedAgent(DOLLAR_BRACE), "workspace"),
                text("the name of a property of the workspace", f -> f.workspace().addProperty(DOLLAR_BRACE, "v"),
                        "workspace"),
                text("the value of a property of the workspace", f -> f.workspace().addProperty("k", DOLLAR_BRACE),
                        "workspace"),
                text("a user of the workspace",
                        f -> f.workspace().getConfiguration().addUser(DOLLAR_BRACE, Role.ReadOnly), "configuration"),
                text("a property of the model", f -> f.workspace().getModel().addProperty("k", DOLLAR_BRACE),
                        "model"),
                text("the name of an element",
                        f -> f.identifiers().register("named", f.workspace().getModel().addPerson(DOLLAR_BRACE)),
                        "named"),
                text("the description of an element", f -> f.clerk().setDescription(DOLLAR_BRACE), "clerk"),
                text("a tag of an element", f -> f.clerk().addTags(DOLLAR_BRACE), "clerk"),
                text("the name of a property of an element", f -> f.clerk().addProperty(DOLLAR_BRACE, "v"), "clerk"),
                text("the value of a property of an element", f -> f.clerk().addProperty("k", DOLLAR_BRACE), "clerk"),
                text("the name of a perspective of an element", f -> f.orders().addPerspective(DOLLAR_BRACE, "d"),
                        "orders"),
                text("the description of a perspective of an element",
                        f -> f.security().setDescription(DOLLAR_BRACE), "orders"),
                text("the value of a perspective of an element", f -> f.security().setValue(DOLLAR_BRACE), "orders"),
                text("the group of an element", f -> f.orders().setGroup(DOLLAR_BRACE), "orders"),
                text("the technology of a container", f -> f.app().setTechnology(DOLLAR_BRACE), "app"),
                text("the technology of a component", f -> f.orders().setTechnology(DOLLAR_BRACE), "orders"),
                text("the metadata of a custom element", f -> f.custom().setMetadata(DOLLAR_BRACE), "custom"),
                text("the technology of a deployment node", f -> f.server().setTechnology(DOLLAR_BRACE), "server"),
                text("a deployment group of a deployment node", f -> f.server().addDeploymentGroup(DOLLAR_BRACE),
                        "server"),
                text("the technology of an infrastructure node", f -> f.gateway().setTechnology(DOLLAR_BRACE),
                        "gateway"),
                text("the environment of a deployment node, an infrastructure node and an instance", f -> {
                    DeploymentNode node = f.workspace().getModel().addDeploymentNode(DOLLAR_BRACE, "Other", "", "");
                    f.identifiers().register("otherServer", node);
                    f.identifiers().register("otherGateway", node.addInfrastructureNode("Other gateway"));
                    f.identifiers().register("otherInstance", node.add(f.app()));
                }, "otherGateway", "otherInstance", "otherServer"),
                text("a deployment group of an instance",
                        f -> f.identifiers().register("shopInstance", f.server().add(f.shop(), DOLLAR_BRACE)),
                        "shopInstance"),
                text("the name of a health check", f -> addHealthCheck(f.appInstance(), DOLLAR_BRACE), "appInstance"),
                text("the name of a header of a health check", f -> f.health().addHeader(DOLLAR_BRACE, "v"),
                        "appInstance"),
                text("the value of a header of a health check", f -> f.health().addHeader("X-Check", DOLLAR_BRACE),
                        "appInstance"),
                text("the description of a relationship", f -> f.clerk().uses(f.custom(), DOLLAR_BRACE),
                        "clerk -> custom"),
                text("the technology of a relationship", f -> f.clerk().uses(f.custom(), "Reads", DOLLAR_BRACE),
                        "clerk -> custom"),
                text("a tag of a relationship", f -> f.uses().addTags(DOLLAR_BRACE), "clerk -> app"),
                text("a property of a relationship", f -> f.uses().addProperty("k", DOLLAR_BRACE), "clerk -> app"),
                text("a perspective of a relationship", f -> f.cost().setValue(DOLLAR_BRACE), "clerk -> app"),
                text("the key of a view", f -> f.views().createSystemLandscapeView(DOLLAR_BRACE, "d"), "view ${"),
                text("the title of a view", f -> f.components().setTitle(DOLLAR_BRACE), "view Components"),
                text("the description of a view", f -> f.components().setDescription(DOLLAR_BRACE),
                        "view Components"),
                text("a property of a view", f -> f.components().addProperty("k", DOLLAR_BRACE), "view Components"),
                text("the description of a relationship in a view", f -> f.flow().add(f.clerk(), DOLLAR_BRACE, f.app()),
                        "view Flow"),
                text("the order of a relationship in a view", f -> f.step().setOrder(DOLLAR_BRACE), "view Flow"),
                text("a property of a relationship in a view", f -> f.step().addProperty("k", DOLLAR_BRACE),
                        "view Flow"),
                text("the environment of a deployment view", f -> f.live().setEnvironment(DOLLAR_BRACE),
                        "view LiveDeployment"),
                text("the base view of a filtered view", f -> f.views().createFilteredView(
                        f.views().createSystemLandscapeView(DOLLAR_BRACE, "d"), "Other", "d", FilterMode.Include,
                        "Element"), "view ${", "view Other"),
                text("a tag of a filtered view", f -> f.views().createFilteredView(f.components(), "Tagged", "d",
                        FilterMode.Include, DOLLAR_BRACE), "view Tagged"),
                text("the default view", f -> f.views().getConfiguration().setDefaultView(
                        f.views().createSystemLandscapeView(DOLLAR_BRACE, "d")), "view ${", "views"),
                text("the term for a person", f -> f.terms().setPerson(DOLLAR_BRACE), "views"),
                text("the term for a software system", f -> f.terms().setSoftwareSystem(DOLLAR_BRACE), "views"),
                text("the term for a container", f -> f.terms().setContainer(DOLLAR_BRACE), "views"),
                text("the term for a component", f -> f.terms().setComponent(DOLLAR_BRACE), "views"),
                text("the term for code", f -> f.terms().setCode(DOLLAR_BRACE), "views"),
                text("the term for a deployment node", f -> f.terms().setDeploymentNode(DOLLAR_BRACE), "views"),
                text("the term for an infrastructure node", f -> f.terms().setInfrastructureNode(DOLLAR_BRACE),
                        "views"),
                text("the term for a relationship", f -> f.terms().setRelationship(DOLLAR_BRACE), "views"),
                text("a property of the views", f -> f.views().getConfiguration().addProperty("k", DOLLAR_BRACE),
                        "views"),
                text("the tag of an element style",
                        f -> f.views().getConfiguration().getStyles().addElementStyle(DOLLAR_BRACE), "views"),
                text("a property of an element style", f -> f.elementStyle().addProperty("k", DOLLAR_BRACE), "views"),
                text("the icon of an element style",
                        f -> f.elementStyle().setIcon("https://example.com/" + DOLLAR_BRACE + ".png"), "views"),
                text("the tag of a relationship style",
                        f -> f.views().getConfiguration().getStyles().addRelationshipStyle(DOLLAR_BRACE), "views"),
                text("a property of a relationship style", f -> f.relationshipStyle().addProperty("k", DOLLAR_BRACE),
                        "views"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyTextTheWalkReads")
    void aDollarBraceInAnyTextThatTheWalkReadsIsFoundAtItsPlace(String text, Consumer<Fixture> put,
            List<String> places) {
        Fixture fixture = Fixture.create();
        put.accept(fixture);

        assertThat(placesWithDollarBrace(fixture)).containsExactlyElementsOf(places);
    }

    @Test
    void eachPlaceIsNamedOnceAndThePlacesAreSorted() {
        Fixture fixture = Fixture.create();
        fixture.workspace().setName(DOLLAR_BRACE);
        fixture.orders().setGroup("outer" + DOLLAR_BRACE + "X}");
        fixture.clerk().setDescription(DOLLAR_BRACE);
        fixture.clerk().addTags(DOLLAR_BRACE);

        assertThat(placesWithDollarBrace(fixture)).containsExactly("clerk", "orders", "workspace");
    }

    /** Structurizr 6.2.4 deprecates health checks in its API, but its DSL still adds them. */
    @SuppressWarnings("deprecation")
    private static HttpHealthCheck addHealthCheck(StaticStructureElementInstance instance, String name) {
        return instance.addHealthCheck(name, "https://example.com/health", 60, 1000);
    }

    private static Arguments text(String text, Consumer<Fixture> put, String... places) {
        return arguments(text, put, List.of(places));
    }

    private static List<String> placesWithDollarBrace(Fixture fixture) {
        return ParsedStrings.placesWithSubstitutionSyntax(fixture.workspace(), fixture.names());
    }
}
