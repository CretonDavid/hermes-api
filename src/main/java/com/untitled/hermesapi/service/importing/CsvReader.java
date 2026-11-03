package com.untitled.hermesapi.service.importing;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecteur CSV minimal (RFC 4180) : gère les guillemets, les guillemets doublés et les retours
 * à la ligne à l'intérieur des champs.
 */
public final class CsvReader {

    private static final char[] CANDIDATE_SEPARATORS = {';', ',', '\t', '|'};

    private CsvReader() {
    }

    public static List<List<String>> parse(String content, char separator) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == separator) {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString().trim());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString().trim());
            addRow(rows, row);
        }
        return rows;
    }

    /**
     * Devine le séparateur à partir des premières lignes : celui qui donne le nombre de colonnes
     * le plus élevé et le plus régulier.
     */
    public static char detectSeparator(String content) {
        String[] lines = content.lines().filter(l -> !l.isBlank()).limit(20).toArray(String[]::new);
        char best = ';';
        double bestScore = -1;
        for (char candidate : CANDIDATE_SEPARATORS) {
            int min = Integer.MAX_VALUE;
            int total = 0;
            for (String line : lines) {
                int count = countOutsideQuotes(line, candidate);
                min = Math.min(min, count);
                total += count;
            }
            if (lines.length == 0 || total == 0) {
                continue;
            }
            // On privilégie un séparateur présent sur toutes les lignes
            double score = min * 10.0 + (double) total / lines.length;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static int countOutsideQuotes(String line, char separator) {
        int count = 0;
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separator && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean empty = row.stream().allMatch(String::isEmpty);
        if (!empty) {
            rows.add(row);
        }
    }
}
