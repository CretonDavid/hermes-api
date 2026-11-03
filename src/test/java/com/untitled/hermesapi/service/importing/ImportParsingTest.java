package com.untitled.hermesapi.service.importing;

import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportParsingTest {

    @Test
    void parsesBankAmounts() {
        assertThat(ImportService.parseAmount("-1 234,56", (char) 0)).isEqualByComparingTo("-1234.56");
        assertThat(ImportService.parseAmount("1.234,56", (char) 0)).isEqualByComparingTo("1234.56");
        assertThat(ImportService.parseAmount("1,234.56", (char) 0)).isEqualByComparingTo("1234.56");
        assertThat(ImportService.parseAmount("12,50 €", (char) 0)).isEqualByComparingTo("12.50");
        assertThat(ImportService.parseAmount("12.50-", (char) 0)).isEqualByComparingTo("-12.50");
        assertThat(ImportService.parseAmount("+3,00", ',')).isEqualByComparingTo("3.00");
        assertThat(ImportService.parseAmount("1 000", ',')).isEqualByComparingTo("1000");
        assertThat(ImportService.parseAmount("", (char) 0)).isNull();
        assertThatThrownBy(() -> ImportService.parseAmount("abc", (char) 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsQuotedCsvAndDetectsSeparator() {
        String csv = "date;label;amount\n01/01/2026;\"Resto \"\"Chez Paul\"\"; Paris\";-12,5\r\n\n02/01/2026;Café;-2\n";
        assertThat(CsvReader.detectSeparator(csv)).isEqualTo(';');

        List<List<String>> rows = CsvReader.parse(csv, ';');
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsExactly("01/01/2026", "Resto \"Chez Paul\"; Paris", "-12,5");
        assertThat(CsvReader.detectSeparator("a,b,c\n1,2,3\n")).isEqualTo(',');
    }

    @Test
    void readsXmlOfx() {
        String ofx = """
                <?xml version="1.0"?><OFX><BANKTRANLIST>
                <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260210</DTPOSTED><DTUSER>20260208</DTUSER>
                <TRNAMT>-9.99</TRNAMT><FITID>X1</FITID><NAME>NETFLIX &amp; CO</NAME></STMTTRN>
                </BANKTRANLIST></OFX>
                """;
        List<OfxReader.OfxTransaction> transactions = OfxReader.parse(ofx);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().name()).isEqualTo("NETFLIX & CO");
        assertThat(transactions.getFirst().amount()).isEqualByComparingTo(new BigDecimal("-9.99"));
        assertThat(transactions.getFirst().dateUser()).isEqualTo(LocalDate.of(2026, 2, 8));
    }

    @Test
    void normalizesBankLabels() {
        assertThat(LabelNormalizer.key("CB CARREFOUR 12/10 4321")).isEqualTo(LabelNormalizer.key("CB CARREFOUR 03/11 4321"));
        assertThat(LabelNormalizer.key("PRLV SEPA EDF CLIENTS PARTICULIERS")).isEqualTo("EDF CLIENTS PARTICULIERS");
        assertThat(LabelNormalizer.normalize("Café Élysée")).isEqualTo("CAFE ELYSEE");
        assertThat(LabelNormalizer.guessPaymentMethod("CB AMAZON")).isEqualTo(PaymentMethod.CARD);
        assertThat(LabelNormalizer.guessPaymentMethod("PRLV SEPA FREE")).isEqualTo(PaymentMethod.DIRECT_DEBIT);
        assertThat(LabelNormalizer.guessPaymentMethod("Courses")).isNull();
    }

    @Test
    void recurrenceDoesNotDriftAtEndOfMonth() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertThat(RecurrenceFrequency.MONTHLY.occurrence(start, 1, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(RecurrenceFrequency.MONTHLY.occurrence(start, 1, 2)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(RecurrenceFrequency.MONTHLY.occurrence(start, 3, 1)).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(RecurrenceFrequency.WEEKLY.occurrence(start, 2, 1)).isEqualTo(LocalDate.of(2026, 2, 14));
    }
}
