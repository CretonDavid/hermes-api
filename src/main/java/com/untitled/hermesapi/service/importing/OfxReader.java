package com.untitled.hermesapi.service.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecteur OFX tolérant : accepte l'OFX 1.x (SGML, balises non fermées) exporté par la plupart des
 * banques françaises comme l'OFX 2.x (XML).
 */
public final class OfxReader {

    public record OfxTransaction(String fitId, String type, LocalDate datePosted, LocalDate dateUser,
                                 BigDecimal amount, String name, String memo, String checkNumber) {
    }

    private static final Pattern TRANSACTION_BLOCK =
            Pattern.compile("<STMTTRN>(.*?)(?:</STMTTRN>|(?=<STMTTRN>)|(?=</BANKTRANLIST>))",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OfxReader() {
    }

    public static boolean looksLikeOfx(String content) {
        String head = content.substring(0, Math.min(content.length(), 2000)).toUpperCase();
        return head.contains("OFXHEADER") || head.contains("<OFX>");
    }

    public static List<OfxTransaction> parse(String content) {
        List<OfxTransaction> transactions = new ArrayList<>();
        Matcher matcher = TRANSACTION_BLOCK.matcher(content);
        while (matcher.find()) {
            String block = matcher.group(1);
            String amount = value(block, "TRNAMT");
            LocalDate posted = date(value(block, "DTPOSTED"));
            if (amount == null || posted == null) {
                continue;
            }
            transactions.add(new OfxTransaction(
                    value(block, "FITID"),
                    value(block, "TRNTYPE"),
                    posted,
                    date(value(block, "DTUSER")),
                    new BigDecimal(amount.replace(",", ".").replace(" ", "")),
                    value(block, "NAME"),
                    value(block, "MEMO"),
                    value(block, "CHECKNUM")));
        }
        return transactions;
    }

    // Valeur d'un élément, qu'il soit fermé (<NAME>x</NAME>) ou non (<NAME>x)
    private static String value(String block, String tag) {
        Matcher matcher = Pattern.compile("<" + tag + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(block);
        if (!matcher.find()) {
            return null;
        }
        String value = decode(matcher.group(1).trim());
        return value.isEmpty() ? null : value;
    }

    private static LocalDate date(String value) {
        if (value == null || value.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 8), OFX_DATE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String decode(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }
}
