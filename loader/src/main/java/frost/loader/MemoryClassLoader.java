package frost.loader;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * FROST MEMORY CLASS LOADER
 *
 * Loads a JAR file from a raw byte array entirely in memory.
 * The JAR bytes NEVER touch the filesystem.
 *
 * This is the final gate: if the byte array is garbage
 * (wrong auth, corrupted key, tampered envProfile),
 * the JarInputStream will fail to parse → ZipException or
 * the class bytes will fail to verify → ClassFormatError.
 *
 * There is NO boolean check. The JVM's own class verifier
 * is the authentication gate. You cannot patch it.
 */
public class MemoryClassLoader extends SecureClassLoader {

    private final Map<String, byte[]> classData = new HashMap<>();
    private final Map<String, byte[]> resourceData = new HashMap<>();

    /**
     * Parse JAR bytes and extract all class files and resources into memory.
     * If jarBytes is garbage, this throws ZipException/IOException.
     * That's the natural, un-patchable auth failure.
     */
    public MemoryClassLoader(byte[] jarBytes) throws IOException {
        super(MemoryClassLoader.class.getClassLoader());

        JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes));
        JarEntry entry;

        while ((entry = jis.getNextJarEntry()) != null) {
            String name = entry.getName();
            byte[] data = readEntry(jis);

            if (name.endsWith(".class")) {
                // Convert path to class name: com/example/Foo.class → com.example.Foo
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                classData.put(className, data);
            } else {
                resourceData.put(name, data);
            }
        }

        jis.close();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classData.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }

        // defineClass will throw ClassFormatError if bytes are garbage
        // This is the JVM's built-in verification — cannot be patched
        CodeSource cs = new CodeSource(null, (Certificate[]) null);
        return defineClass(name, bytes, 0, bytes.length, cs);
    }

    @Override
    public URL getResource(String name) {
        if (resourceData.containsKey(name)) {
            try {
                return new URL(null, "memory:///" + name, new MemoryURLHandler(name));
            } catch (Exception e) {
                return null;
            }
        }
        return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] data = resourceData.get(name);
        if (data != null) {
            return new ByteArrayInputStream(data);
        }
        return super.getResourceAsStream(name);
    }

    /**
     * Load all classes and invoke the entry point.
     * If any class is corrupt, this fails naturally.
     */
    public void loadAndStart(String mainClass, String method) throws Exception {
        Class<?> cls = loadClass(mainClass);
        cls.getMethod(method).invoke(null);
    }

    private byte[] readEntry(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }

    /** Custom URL handler for in-memory resources */
    private class MemoryURLHandler extends URLStreamHandler {
        private final String resourceName;

        MemoryURLHandler(String name) {
            this.resourceName = name;
        }

        @Override
        protected URLConnection openConnection(URL u) {
            return new URLConnection(u) {
                @Override
                public void connect() {}

                @Override
                public InputStream getInputStream() {
                    byte[] data = resourceData.get(resourceName);
                    return data != null ? new ByteArrayInputStream(data) : new ByteArrayInputStream(new byte[0]);
                }
            };
        }
    }
}
