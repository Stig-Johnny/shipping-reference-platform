package io.github.stigjohnny.c4contractkit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.stigjohnny.c4contractkit.testing.Serialization;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelExceptionTest {

    @Test
    void theProblemsSurviveSerialization(@TempDir Path dir) throws Exception {
        Path model = Files.writeString(dir.resolve("workspace.dsl"), "workspace {\n    model {\n    }\n}\n",
                StandardCharsets.UTF_8);
        ModelException thrown = catchThrowableOfType(ModelException.class, () -> Conformance.check(model, List.of()));

        ModelException copy = Serialization.roundTrip(thrown, ModelException.class);

        assertThat(copy.problems()).containsExactly("model: no container has contract.root; exactly one must");
        assertThat(copy).hasMessage(thrown.getMessage());
        assertThatThrownBy(() -> copy.problems().set(0, "changed")).isInstanceOf(UnsupportedOperationException.class);
    }
}
