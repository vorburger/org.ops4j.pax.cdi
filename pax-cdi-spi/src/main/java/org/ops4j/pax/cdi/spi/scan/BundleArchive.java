/*
 * Copyright 2015 Harald Wellmann.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.cdi.spi.scan;

import static org.osgi.framework.Constants.BUNDLE_CLASSPATH;
import static org.osgi.framework.wiring.BundleRevision.BUNDLE_NAMESPACE;
import static org.osgi.framework.wiring.BundleRevision.PACKAGE_NAMESPACE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.xbean.finder.archive.Archive;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleArchive implements Archive {

    private static Logger log = LoggerFactory.getLogger(BundleArchive.class);

    private static final String CLASS_EXT = ".class";
    private static final String CLASS_PATTERN = "*" + CLASS_EXT;

    private Bundle bundle;
    private Map<String, Entry> entries;
    private HashSet<String> scannedPackages;

    private BundleFilter filter;

    private static class BundleArchiveEntry implements Entry {

        private Bundle provider;
        private URL path;
        private String name;

        public BundleArchiveEntry(Bundle provider, URL path, String name) {
            this.provider = provider;
            this.path = path;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public InputStream getBytecode() throws IOException {
            return path.openStream();
        }

        public Bundle getProvider() {
            return provider;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof BundleArchiveEntry)) {
                return false;
            }
            BundleArchiveEntry other = (BundleArchiveEntry) obj;
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            }
            else if (!name.equals(other.name)) {
                return false;
            }
            return true;
        }
    }

    public BundleArchive(Bundle bundle) {
        this(bundle, new DefaultBundleFilter());
    }

    public BundleArchive(Bundle bundle, BundleFilter filter) {
        this.bundle = bundle;
        this.entries = new HashMap<>();
        this.filter = filter;
    }

    @Override
    public Iterator<Entry> iterator() {
        entries = new HashMap<>();
        scannedPackages = new HashSet<String>();
        scanOwnBundle();
        scanImportedPackages();
        scanWiredBundles(BUNDLE_NAMESPACE);
        return entries.values().iterator();
    }

    @Override
    public InputStream getBytecode(String className) throws IOException, ClassNotFoundException {
        Entry entry = entries.get(className);
        if (entry == null) {
            return null;
        }
        return entry.getBytecode();
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return bundle.loadClass(className);
    }

    public Bundle getProvider(String className) {
        BundleArchiveEntry entry = (BundleArchiveEntry) entries.get(className);
        if (entry == null) {
            return null;
        }
        return entry.getProvider();
    }

    private void scanOwnBundle() {
        String[] classPathElements;

        String bundleClassPath = bundle.getHeaders().get(BUNDLE_CLASSPATH);
        if (bundleClassPath == null) {
            classPathElements = new String[] { "/" };
        }
        else {
            classPathElements = bundleClassPath.split(",");
        }

        for (String cp : classPathElements) {
            String classPath = cp;
            if (classPath.equals(".")) {
                classPath = "/";
            }

            if (classPath.endsWith(".jar") || classPath.endsWith(".zip")) {
                scanZip(classPath);
            }
            else {
                scanDirectory(classPath);
            }
        }
    }

    private void scanDirectory(String classPath) {
        Enumeration<URL> e = bundle.findEntries(classPath, CLASS_PATTERN, true);
        while (e != null && e.hasMoreElements()) {
            URL url = e.nextElement();
            String klass = toClassName(classPath, url);
            if (filter.accept(bundle, klass)) {
                BundleArchiveEntry entry = new BundleArchiveEntry(bundle, url, klass);
                entries.put(klass, entry);
            }
        }
    }

    private void scanZip(String zipName) {
        URL zipEntry = bundle.getEntry(zipName);
        if (zipEntry == null) {
            return;
        }
        try (ZipInputStream in = new ZipInputStream(zipEntry.openStream())) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(CLASS_EXT)) {
                    String klass = toClassName("", name);
                    if (filter.accept(bundle, klass)) {
                        URL url = new URL("jar:" + zipEntry.toExternalForm() + "!/" + name);
                        BundleArchiveEntry archiveEntry = new BundleArchiveEntry(bundle, url, klass);
                        entries.put(klass, archiveEntry);
                    }
                }
            }
        }
        catch (IOException exc) {
            log.warn("error scanning zip file " + zipName, exc);
        }
    }

    private String toClassName(String classPath, URL url) {
        return toClassName(classPath, url.getFile());
    }

    private String toClassName(String classPath, String file) {
        String klass = null;
        String[] parts = file.split("!");
        if (parts.length > 1) {
            klass = parts[1];
        }
        else {
            klass = file;
        }
        if (klass.charAt(0) == '/') {
            klass = klass.substring(1);
        }

        String prefix = classPath;
        if (classPath.length() > 1) {
            if (classPath.charAt(0) == '/') {
                prefix = classPath.substring(1);
            }
            assert klass.startsWith(prefix);
            int startIndex = prefix.length();
            if (!prefix.endsWith("/")) {
                startIndex++;
            }
            klass = klass.substring(startIndex);
        }

        klass = klass.replace("/", ".").replace(CLASS_EXT, "");
        log.trace("file = {}, class = {}", file, klass);
        return klass;
    }

    private void scanImportedPackages() {
        BundleWiring wiring = bundle.adapt(BundleWiring.class);
        List<BundleWire> wires = wiring.getRequiredWires(PACKAGE_NAMESPACE);
        for (BundleWire wire : wires) {
            log.debug("scanning imported package [{}]", wire);
            scanForClasses(wire);
        }
    }

    private void scanForClasses(BundleWire wire) {
        BundleWiring wiring = wire.getProviderWiring();
        scanExportedPackage(wiring, wire.getCapability());
    }

    private void scanExportedPackage(BundleWiring wiring, BundleCapability capability) {
        String pkg = (String) capability.getAttributes().get(PACKAGE_NAMESPACE);
        if (scannedPackages.contains(pkg)) {
            return;
        }

        log.debug("scanning exported package [{}]", pkg);
        scannedPackages.add(pkg);
        Collection<String> resources = wiring.listResources(toPath(pkg), CLASS_PATTERN,
            BundleWiring.LISTRESOURCES_LOCAL);
        Bundle owner = wiring.getBundle();
        for (String resource : resources) {
            String klass = toClassName("", resource);
            if (filter.accept(owner, klass)) {
                BundleArchiveEntry archiveEntry = new BundleArchiveEntry(owner,
                    owner.getEntry(resource), klass);
                entries.put(klass, archiveEntry);
            }
        }
    }

    private String toPath(String pkg) {
        return pkg.replaceAll("\\.", "/");
    }

    private void scanWiredBundles(String namespace) {
        BundleWiring wiring = bundle.adapt(BundleWiring.class);
        List<BundleWire> wires = wiring.getRequiredWires(namespace);
        for (BundleWire wire : wires) {
            BundleWiring providerWiring = wire.getProviderWiring();
            Bundle providerBundle = providerWiring.getBundle();
            log.debug("scanning bundle [{}] wired for namespace [{}]", providerBundle, namespace);
            List<BundleCapability> capabilities = providerWiring.getCapabilities(PACKAGE_NAMESPACE);
            for (BundleCapability pkgCapability : capabilities) {
                scanExportedPackage(providerWiring, pkgCapability);
            }
        }
    }
}
