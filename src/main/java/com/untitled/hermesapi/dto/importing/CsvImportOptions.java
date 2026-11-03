package com.untitled.hermesapi.dto.importing;

import java.util.List;

/**
 * Mapping des colonnes d'un CSV (index à partir de 0). Le montant est soit dans une seule colonne
 * signée (amountColumn), soit réparti entre une colonne débit et une colonne crédit.
 */
public record CsvImportOptions(
        String encoding,
        String separator,
        Integer skipLines,
        Boolean hasHeader,
        Integer dateColumn,
        String dateFormat,
        Integer valueDateColumn,
        List<Integer> labelColumns,
        Integer payeeColumn,
        Integer amountColumn,
        Integer debitColumn,
        Integer creditColumn,
        String decimalSeparator,
        Boolean negateAmounts
) {
}
