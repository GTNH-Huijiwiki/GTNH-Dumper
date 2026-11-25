package com.iouter.gtnhdumper.common.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A debug selection of entries by name. Each term matches a whole candidate name ignoring case, with {@code *} standing
 * for any text. An empty selection matches everything. Terms that never matched are reported, so typos do not pass
 * silently.
 */
public final class NameSelection {

    /** Plain names, looked up directly: a selection may list thousands of keys against the whole item list. */
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();
    private final Set<String> unmatched = new LinkedHashSet<>();

    public NameSelection(Collection<String> selected) {
        for (String term : selected) {
            if (term.indexOf('*') < 0) names.put(fold(term), term);
            else patterns.put(
                term,
                Pattern.compile(
                    Arrays.stream(term.split("\\*", -1))
                        .map(literal -> literal.isEmpty() ? "" : Pattern.quote(literal))
                        .collect(Collectors.joining(".*")),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            unmatched.add(term);
        }
    }

    /** True when any term matches any of the candidate's names; null names are ignored. */
    public boolean matches(String... candidates) {
        if (names.isEmpty() && patterns.isEmpty()) return true;
        boolean matched = false;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String term = names.get(fold(candidate));
            if (term != null) {
                unmatched.remove(term);
                matched = true;
            }
            for (Map.Entry<String, Pattern> pattern : patterns.entrySet()) {
                if (!pattern.getValue()
                    .matcher(candidate)
                    .matches()) continue;
                unmatched.remove(pattern.getKey());
                matched = true;
            }
        }
        return matched;
    }

    public Set<String> unmatched() {
        return unmatched;
    }

    private static String fold(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
