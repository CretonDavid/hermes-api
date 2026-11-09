package com.hermes.api.dto.importing;

import java.util.List;

/**
 * Aperçu brut d'un fichier CSV, pour permettre à l'utilisateur de choisir le mapping des colonnes.
 */
public record CsvInspectResponse(
        String encoding,
        String separator,
        int columnCount,
        List<List<String>> rows
) {
}
