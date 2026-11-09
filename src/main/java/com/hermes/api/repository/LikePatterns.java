package com.hermes.api.repository;

import java.util.Locale;

/**
 * Motifs LIKE construits à partir d'une saisie libre : les jokers % et _ saisis par l'utilisateur
 * sont cherchés tels quels. À utiliser avec le caractère d'échappement {@link #ESCAPE}.
 */
public final class LikePatterns {

    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /**
     * Motif « contient », en minuscules, pour une comparaison avec lower(colonne).
     */
    public static String containsIgnoreCase(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
