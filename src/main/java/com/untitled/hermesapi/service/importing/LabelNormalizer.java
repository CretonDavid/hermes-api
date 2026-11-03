package com.untitled.hermesapi.service.importing;

import com.untitled.hermesapi.model.enums.PaymentMethod;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalisation des libellés bancaires pour rapprocher des opérations similaires
 * (« CB CARREFOUR 12/10 4321 » et « CB CARREFOUR 03/11 4321 » donnent la même clé).
 */
public final class LabelNormalizer {

    // Mots techniques ajoutés par les banques, sans valeur pour identifier le tiers
    private static final Set<String> NOISE = Set.of(
            "CB", "CARTE", "PAIEMENT", "PAIEMENTS", "PRLV", "PRELEVEMENT", "SEPA", "VIR", "VIREMENT", "INST",
            "RECU", "EMIS", "DE", "DU", "LA", "LE", "LES", "DES", "A", "AU", "EUR", "FACTURE", "ACHAT", "FRAIS",
            "PAR", "POUR", "REF", "ECH", "MDT", "ICS", "RUM", "SA", "SAS", "SARL", "FR", "X");

    private LabelNormalizer() {
    }

    public static String normalize(String label) {
        if (label == null) {
            return "";
        }
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Clé de rapprochement : les trois premiers mots significatifs, sans chiffres ni mots techniques.
     */
    public static String key(String label) {
        return Arrays.stream(normalize(label).split(" "))
                .filter(word -> word.length() > 1)
                .filter(word -> !word.matches(".*\\d.*"))
                .filter(word -> !NOISE.contains(word))
                .limit(3)
                .collect(Collectors.joining(" "));
    }

    public static PaymentMethod guessPaymentMethod(String label) {
        String normalized = " " + normalize(label) + " ";
        if (normalized.contains(" CB ") || normalized.contains(" CARTE ") || normalized.startsWith(" PAIEMENT CB")) {
            return PaymentMethod.CARD;
        }
        if (normalized.contains(" PRLV ") || normalized.contains(" PRELEVEMENT ")) {
            return PaymentMethod.DIRECT_DEBIT;
        }
        if (normalized.contains(" VIR ") || normalized.contains(" VIREMENT ")) {
            return PaymentMethod.TRANSFER;
        }
        if (normalized.contains(" CHQ ") || normalized.contains(" CHEQUE ")) {
            return PaymentMethod.CHECK;
        }
        if (normalized.contains(" RETRAIT ") || normalized.contains(" DAB ")) {
            return PaymentMethod.CASH;
        }
        return null;
    }
}
