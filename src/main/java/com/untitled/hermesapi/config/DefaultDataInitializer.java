package com.untitled.hermesapi.config;

import com.untitled.hermesapi.model.Currency;
import com.untitled.hermesapi.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Insère au démarrage les devises les plus courantes, partagées par tous les utilisateurs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDataInitializer implements ApplicationRunner {

    private static final List<String[]> DEFAULT_CURRENCIES = List.of(
            new String[]{"EUR", "€", "Euro"},
            new String[]{"USD", "$", "Dollar américain"},
            new String[]{"GBP", "£", "Livre sterling"},
            new String[]{"CHF", "CHF", "Franc suisse"},
            new String[]{"JPY", "¥", "Yen japonais"},
            new String[]{"CAD", "CA$", "Dollar canadien"},
            new String[]{"AUD", "A$", "Dollar australien"},
            new String[]{"NZD", "NZ$", "Dollar néo-zélandais"},
            new String[]{"CNY", "CN¥", "Yuan chinois"},
            new String[]{"HKD", "HK$", "Dollar de Hong Kong"},
            new String[]{"SGD", "S$", "Dollar de Singapour"},
            new String[]{"SEK", "kr", "Couronne suédoise"},
            new String[]{"NOK", "kr", "Couronne norvégienne"},
            new String[]{"DKK", "kr", "Couronne danoise"},
            new String[]{"PLN", "zł", "Zloty polonais"},
            new String[]{"CZK", "Kč", "Couronne tchèque"},
            new String[]{"HUF", "Ft", "Forint hongrois"},
            new String[]{"RON", "lei", "Leu roumain"},
            new String[]{"TRY", "₺", "Livre turque"},
            new String[]{"INR", "₹", "Roupie indienne"},
            new String[]{"BRL", "R$", "Réal brésilien"},
            new String[]{"MXN", "MX$", "Peso mexicain"},
            new String[]{"ZAR", "R", "Rand sud-africain"},
            new String[]{"KRW", "₩", "Won sud-coréen"},
            new String[]{"THB", "฿", "Baht thaïlandais"},
            new String[]{"ILS", "₪", "Shekel israélien"},
            new String[]{"MAD", "DH", "Dirham marocain"},
            new String[]{"XOF", "CFA", "Franc CFA (BCEAO)"},
            new String[]{"XPF", "F", "Franc pacifique"}
    );

    private final CurrencyRepository currencyRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existing = currencyRepository.findByOwnerIsNull().stream()
                .map(Currency::getCode)
                .collect(Collectors.toSet());

        List<Currency> missing = DEFAULT_CURRENCIES.stream()
                .filter(c -> !existing.contains(c[0]))
                .map(c -> Currency.builder().code(c[0]).symbol(c[1]).name(c[2]).build())
                .toList();

        if (!missing.isEmpty()) {
            currencyRepository.saveAll(missing);
            log.info("{} default currencies created", missing.size());
        }
    }
}
