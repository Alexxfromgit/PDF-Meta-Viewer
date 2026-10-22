package support;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Isolates desktop smoke tests from the developer's real OS preferences. */
public final class InMemoryPreferencesFactory implements PreferencesFactory {
    private final Preferences root = new MemoryPreferences(null, "");

    @Override
    public Preferences systemRoot() {
        return root;
    }

    @Override
    public Preferences userRoot() {
        return root;
    }

    private static final class MemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, MemoryPreferences> children = new HashMap<>();

        MemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        protected void putSpi(String key, String value) { values.put(key, value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() { values.clear(); children.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        protected String[] childrenNamesSpi() { return children.keySet().toArray(String[]::new); }
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, child -> new MemoryPreferences(this, child));
        }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }
}
