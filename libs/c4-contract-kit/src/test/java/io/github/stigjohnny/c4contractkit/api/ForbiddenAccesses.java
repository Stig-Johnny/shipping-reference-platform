package io.github.stigjohnny.c4contractkit.api;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fixture classes that break the review conditions, for {@link ReviewConditionsTest}. They are compiled at test time
 * against stand-ins of the forbidden targets (a rule matches a target by its owner and name only) and are never
 * loaded. {@code fx.forbidden.Calls} calls every forbidden target; {@code fx.forbidden.References} only refers to each
 * one with a method or constructor reference. Both also use two targets that the rules allow.
 */
final class ForbiddenAccesses {

    /** The target in a reported access or dependency, such as {@code calls method <java.lang.System.getenv(...)>}. */
    private static final Pattern TARGET = Pattern.compile("(?:calls|references) (?:method|constructor) <(.+?)\\(");

    private static final JavaClasses CLASSES = new ClassFileImporter().importPath(FixtureCompiler.compile(
            new FixtureSource("com/tngtech/archunit/core/domain/JavaClass.java", """
                    package com.tngtech.archunit.core.domain;

                    public class JavaClass {
                        public Class<?> reflect() {
                            return Object.class;
                        }
                    }
                    """),
            new FixtureSource("com/tngtech/archunit/core/importer/ClassFileImporter.java", """
                    package com.tngtech.archunit.core.importer;

                    public class ClassFileImporter {
                        public Object importClasspath() {
                            return this;
                        }

                        public Object importPackages(String... packages) {
                            return this;
                        }

                        public Object importUrl(java.net.URL url) {
                            return this;
                        }

                        public Object importPath(String path) {
                            return this;
                        }
                    }
                    """),
            new FixtureSource("com/structurizr/dsl/StructurizrDslParser.java", """
                    package com.structurizr.dsl;

                    public class StructurizrDslParser {
                        public com.structurizr.http.HttpClient getHttpClient() {
                            return new com.structurizr.http.HttpClient();
                        }

                        public Features getFeatures() {
                            return new Features();
                        }

                        public void setRestricted(boolean restricted) {
                        }
                    }
                    """),
            new FixtureSource("com/structurizr/dsl/Features.java", """
                    package com.structurizr.dsl;

                    public class Features {
                        public void enable(String feature) {
                        }

                        public void disable(String feature) {
                        }

                        public void configure(String feature, boolean enabled) {
                        }
                    }
                    """),
            new FixtureSource("com/structurizr/util/Features.java", """
                    package com.structurizr.util;

                    public class Features {
                        public void enable(String feature) {
                        }

                        public void configure(String feature, boolean enabled) {
                        }
                    }
                    """),
            new FixtureSource("com/structurizr/http/HttpClient.java", """
                    package com.structurizr.http;

                    public class HttpClient {
                        public void allow(String regex) {
                        }
                    }
                    """),
            new FixtureSource("fx/forbidden/Calls.java", """
                    package fx.forbidden;

                    import com.structurizr.dsl.StructurizrDslParser;
                    import com.tngtech.archunit.core.domain.JavaClass;
                    import com.tngtech.archunit.core.importer.ClassFileImporter;
                    import java.io.IOException;
                    import java.io.InputStream;
                    import java.io.ObjectInputStream;

                    class Calls {

                        Object reflect(JavaClass type) {
                            return type.reflect();
                        }

                        void imports(ClassFileImporter importer) {
                            importer.importClasspath();
                            importer.importPackages("fx");
                            importer.importUrl(null);
                            importer.importPath("build/classes");
                        }

                        Object parser() {
                            return new StructurizrDslParser();
                        }

                        void reopen(StructurizrDslParser parser, com.structurizr.util.Features features) {
                            parser.getHttpClient().allow(".*");
                            parser.setRestricted(true);
                            parser.getFeatures().enable("ENVIRONMENT");
                            parser.getFeatures().configure("ENVIRONMENT", true);
                            parser.getFeatures().disable("ENVIRONMENT");
                            features.enable("ENVIRONMENT");
                            features.configure("ENVIRONMENT", true);
                        }

                        void escape() throws Exception {
                            Class.forName("fx.Other");
                            ClassLoader.getSystemClassLoader().loadClass("fx.Other");
                            System.getenv("HOME");
                            Runtime.getRuntime().exec(new String[] {"true"});
                        }

                        Object deserialize(InputStream in) throws IOException {
                            return new ObjectInputStream(in);
                        }

                        Object process() {
                            return new ProcessBuilder("true");
                        }
                    }
                    """),
            new FixtureSource("fx/forbidden/References.java", """
                    package fx.forbidden;

                    import com.structurizr.dsl.StructurizrDslParser;
                    import com.tngtech.archunit.core.domain.JavaClass;
                    import com.tngtech.archunit.core.importer.ClassFileImporter;
                    import java.io.InputStream;
                    import java.io.ObjectInputStream;
                    import java.net.URL;
                    import java.util.List;
                    import java.util.function.Consumer;
                    import java.util.function.Function;
                    import java.util.function.Supplier;

                    class References {

                        interface Throwing<T, R> {
                            R apply(T argument) throws Exception;
                        }

                        interface Configure {
                            void apply(String feature, boolean enabled);
                        }

                        Object reflect() {
                            Function<JavaClass, Class<?>> reflect = JavaClass::reflect;
                            return reflect;
                        }

                        List<Object> imports(ClassFileImporter importer) {
                            Supplier<Object> classpath = importer::importClasspath;
                            Function<String[], Object> packages = importer::importPackages;
                            Function<URL, Object> url = importer::importUrl;
                            Function<String, Object> path = importer::importPath;
                            return List.of(classpath, packages, url, path);
                        }

                        Object parser() {
                            Supplier<StructurizrDslParser> parsers = StructurizrDslParser::new;
                            return parsers;
                        }

                        List<Object> reopen(StructurizrDslParser parser, com.structurizr.util.Features features) {
                            Consumer<String> allow = parser.getHttpClient()::allow;
                            Consumer<Boolean> restrict = parser::setRestricted;
                            Consumer<String> enable = parser.getFeatures()::enable;
                            Configure configure = parser.getFeatures()::configure;
                            Consumer<String> disable = parser.getFeatures()::disable;
                            Consumer<String> enableUtil = features::enable;
                            Configure configureUtil = features::configure;
                            return List.of(allow, restrict, enable, configure, disable, enableUtil, configureUtil);
                        }

                        List<Object> escape() {
                            Throwing<String, Class<?>> forName = Class::forName;
                            Throwing<String, Class<?>> loadClass = ClassLoader.getSystemClassLoader()::loadClass;
                            Function<String, String> getenv = System::getenv;
                            Throwing<String[], Process> exec = Runtime.getRuntime()::exec;
                            return List.of(forName, loadClass, getenv, exec);
                        }

                        Object deserialize() {
                            Throwing<InputStream, Object> open = ObjectInputStream::new;
                            return open;
                        }

                        Object process() {
                            Function<String[], Object> process = ProcessBuilder::new;
                            return process;
                        }
                    }
                    """)));

    private ForbiddenAccesses() {
    }

    /** The targets that {@code rule} reports in the fixture class {@code simpleName}, as {@code owner.name}. */
    static Set<String> reported(ArchRule rule, String simpleName) {
        return rule.evaluate(CLASSES.that(JavaClass.Predicates.simpleName(simpleName))).getFailureReport()
                .getDetails().stream()
                .flatMap(detail -> TARGET.matcher(detail).results())
                .map(match -> match.group(1))
                .collect(Collectors.toSet());
    }
}
