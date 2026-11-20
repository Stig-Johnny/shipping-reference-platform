package io.github.stigjohnny.docs.check;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * The names a Java source declares for classes, records, interfaces, enums and annotation types that read as test
 * classes, nested and local ones included. The JDK's own parser reads the source, so Unicode escapes, comments,
 * literals, text blocks and identifiers are read exactly as the compiler reads them; nothing is compiled or run. A
 * source that does not parse without errors declares nothing, so it can never make an unknown name pass. Results are
 * kept by source text, because the documentation tests read the same sources in many copies of the repository.
 */
final class JavaDeclarations {

    private static final Map<String, Set<String>> PARSED = new ConcurrentHashMap<>();

    private JavaDeclarations() {
    }

    static Set<String> testClasses(String source) {
        return PARSED.computeIfAbsent(source, JavaDeclarations::parse);
    }

    private static Set<String> parse(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new DocsException("the test sources cannot be read: this Java runtime has no compiler; run the "
                    + "documentation tool on the reviewed JDK");
        }
        URI uri = URI.create("string:///Source.java");
        JavaFileObject file = new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null,
                List.of(file));
        Set<String> names = new TreeSet<>();
        try {
            for (CompilationUnitTree unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitClass(ClassTree node, Void unused) {
                        String name = node.getSimpleName().toString();
                        if (References.TEST.matcher(name).matches()) {
                            names.add(name);
                        }
                        return super.visitClass(node, null);
                    }
                }.scan(unit, null);
            }
        } catch (IOException e) {
            throw new DocsException("a test source cannot be parsed: " + e.getClass().getSimpleName(), e);
        }
        boolean errors = diagnostics.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR);
        return errors ? Set.of() : Collections.unmodifiableSet(names);
    }
}
