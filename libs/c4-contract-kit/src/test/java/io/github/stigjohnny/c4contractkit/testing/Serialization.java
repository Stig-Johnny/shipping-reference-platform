package io.github.stigjohnny.c4contractkit.testing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Java serialization round trips of objects that a test created itself. Only those trusted bytes are deserialized
 * (security/allowlist.json: no deserialization of untrusted data in a JVM that loads ArchUnit), and a filter admits
 * only the classes that an exception of the kit is made of.
 */
public final class Serialization {

    private static final ObjectInputFilter KIT_EXCEPTIONS = ObjectInputFilter.Config.createFilter(
            "maxdepth=5;io.github.stigjohnny.c4contractkit.**;java.lang.*;java.util.Collections$EmptyList;!*");

    private Serialization() {
    }

    public static <T extends Serializable> T roundTrip(T object, Class<T> type)
            throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setObjectInputFilter(KIT_EXCEPTIONS);
            return type.cast(in.readObject());
        }
    }
}
