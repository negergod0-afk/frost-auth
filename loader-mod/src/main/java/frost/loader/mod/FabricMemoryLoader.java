package frost.loader.mod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * FROST FABRIC MEMORY LOADER
 *
 * Parses a JAR (ZIP) from raw bytes, extracts all .class files,
 * and loads them into the JVM without writing anything to disk.
 *
 * Designed for use inside a Fabric mod: the parent classloader is
 * Fabric's KnotClassLoader so loaded classes can access Minecraft/Fabric APIs.
 *
 * Security property: a wrong composite key → garbage bytes → ZipException
 * or ClassFormatError.  No branch on those outcomes — the JVM handles it.
 */
public class FabricMemoryLoader extends ClassLoader {

    // Map of binary class name → raw .class bytes (e.g. "com/zenya/ZenyaClient" → bytes)
    private final Map<String, byte[]> classes = new HashMap<>();
    // Map of resource path → raw bytes (assets, json, etc.)
    private final Map<String, byte[]> resources = new HashMap<>();

    /**
     * @param jarBytes   Raw bytes of the decrypted mod JAR.
     * @param parent     Should be the Fabric/Knot classloader so the loaded
     *                   classes can resolve Minecraft/Fabric dependencies.
     */
    public FabricMemoryLoader(byte[] jarBytes, ClassLoader parent) throws IOException {
        super(parent);
        parseJar(jarBytes);
    }

    private void parseJar(byte[] jarBytes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                byte[] data = zip.readAllBytes();
                String name = entry.getName();

                if (name.endsWith(".class")) {
                    // Convert path "com/zenya/Foo.class" → binary name "com.zenya.Foo"
                    String binaryName = name.substring(0, name.length() - 6).replace('/', '.');
                    classes.put(binaryName, data);
                } else {
                    resources.put(name, data);
                }
                zip.closeEntry();
            }
        }
        // Empty JAR → parseJar succeeds but findClass will always throw CNFE.
        // Garbage bytes → ZipInputStream throws ZipException immediately.
        // Both are correct silent-fail paths.
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] data = classes.get(name);
        if (data == null) throw new ClassNotFoundException(name);
        // defineClass() throws ClassFormatError on malformed bytecode → correct silent fail.
        return defineClass(name, data, 0, data.length);
    }

    @Override
    public java.io.InputStream getResourceAsStream(String name) {
        byte[] data = resources.get(name);
        if (data != null) return new ByteArrayInputStream(data);
        return super.getResourceAsStream(name);
    }
}
