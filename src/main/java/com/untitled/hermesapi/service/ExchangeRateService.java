package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.currency.ExchangeRateRequest;
import com.untitled.hermesapi.dto.currency.ExchangeRateResponse;
import com.untitled.hermesapi.dto.currency.ExchangeRateSyncResponse;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.exception.ResourceAlreadyExistsException;
import com.untitled.hermesapi.exception.ResourceNotFoundException;
import com.untitled.hermesapi.mapper.CurrencyMapper;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.Currency;
import com.untitled.hermesapi.model.ExchangeRate;
import com.untitled.hermesapi.model.User;
import com.untitled.hermesapi.repository.AccountRepository;
import com.untitled.hermesapi.repository.ExchangeRateRepository;
import com.untitled.hermesapi.repository.UserRepository;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CurrencyService currencyService;
    private final CurrencyMapper currencyMapper;
    private final CurrentUser currentUser;
    private final RestClient restClient;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository,
                               AccountRepository accountRepository,
                               UserRepository userRepository,
                               CurrencyService currencyService,
                               CurrencyMapper currencyMapper,
                               CurrentUser currentUser,
                               @Value("${application.exchange-rates.provider-url}") String providerUrl) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currencyService = currencyService;
        this.currencyMapper = currencyMapper;
        this.currentUser = currentUser;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().baseUrl(providerUrl).requestFactory(requestFactory).build();
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> findAll(Long currencyId, boolean latestOnly) {
        UUID userId = currentUser.id();
        List<ExchangeRate> rates = currencyId == null
                ? exchangeRateRepository.findAllByOwner(userId)
                : exchangeRateRepository.findAllByOwnerAndCurrency(userId, currencyId);

        if (latestOnly) {
            // Les taux sont triés par date décroissante : on garde le premier de chaque paire
            Map<String, ExchangeRate> latest = new LinkedHashMap<>();
            for (ExchangeRate rate : rates) {
                latest.putIfAbsent(rate.getBaseCurrency().getId() + "-" + rate.getQuoteCurrency().getId(), rate);
            }
            rates = new ArrayList<>(latest.values());
        }
        return rates.stream().map(currencyMapper::toResponse).toList();
    }

    @Transactional
    public ExchangeRateResponse create(ExchangeRateRequest request) {
        UUID userId = currentUser.id();
        Currency base = currencyService.findAccessible(request.baseCurrencyId());
        Currency quote = currencyService.findAccessible(request.quoteCurrencyId());
        validatePair(base, quote);

        if (exchangeRateRepository.findPair(userId, base.getId(), quote.getId(), request.date()).isPresent()) {
            throw new ResourceAlreadyExistsException("Un taux existe déjà pour cette paire à cette date");
        }

        ExchangeRate rate = ExchangeRate.builder()
                .owner(currentUser.reference())
                .baseCurrency(base)
                .quoteCurrency(quote)
                .rate(request.rate())
                .date(request.date())
                .build();
        return currencyMapper.toResponse(exchangeRateRepository.save(rate));
    }

    @Transactional
    public ExchangeRateResponse update(Long id, ExchangeRateRequest request) {
        UUID userId = currentUser.id();
        ExchangeRate rate = exchangeRateRepository.findByIdAndOwner(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Taux de change introuvable"));
        Currency base = currencyService.findAccessible(request.baseCurrencyId());
        Currency quote = currencyService.findAccessible(request.quoteCurrencyId());
        validatePair(base, quote);

        exchangeRateRepository.findPair(userId, base.getId(), quote.getId(), request.date())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResourceAlreadyExistsException("Un taux existe déjà pour cette paire à cette date");
                });

        rate.setBaseCurrency(base);
        rate.setQuoteCurrency(quote);
        rate.setRate(request.rate());
        rate.setDate(request.date());
        return currencyMapper.toResponse(rate);
    }

    @Transactional
    public void delete(Long id) {
        ExchangeRate rate = exchangeRateRepository.findByIdAndOwner(id, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Taux de change introuvable"));
        exchangeRateRepository.delete(rate);
    }

    /**
     * Récupère les taux de référence de la BCE (via Frankfurter) entre la devise de référence et
     * les devises des comptes de l'utilisateur. Sans date de début, seul le dernier taux est importé.
     */
    @Transactional
    public ExchangeRateSyncResponse sync(LocalDate from) {
        UUID userId = currentUser.id();
        User user = userRepository.findWithCurrencyById(userId).orElseThrow();
        Currency reference = user.getReferenceCurrency();
        if (reference == null) {
            throw new BadRequestException("Choisissez d'abord une devise de référence");
        }

        Map<String, Currency> targets = accountRepository.findAllByOwner(userId).stream()
                .map(Account::getCurrency)
                .filter(c -> !c.getId().equals(reference.getId()))
                .collect(Collectors.toMap(Currency::getCode, c -> c, (a, b) -> a, TreeMap::new));
        if (targets.isEmpty()) {
            return new ExchangeRateSyncResponse(0, List.of());
        }

        Set<String> supported = fetchSupportedCurrencies();
        if (!supported.contains(reference.getCode())) {
            throw new BadRequestException("La devise " + reference.getCode() + " n'est pas couverte par la BCE");
        }
        List<String> unsupported = targets.keySet().stream().filter(code -> !supported.contains(code)).toList();
        List<String> symbols = targets.keySet().stream().filter(supported::contains).toList();
        if (symbols.isEmpty()) {
            return new ExchangeRateSyncResponse(0, unsupported);
        }

        Map<LocalDate, Map<String, BigDecimal>> series = fetchRates(reference.getCode(), symbols, from);

        int imported = 0;
        User owner = currentUser.reference();
        for (Map.Entry<LocalDate, Map<String, BigDecimal>> day : series.entrySet()) {
            for (Map.Entry<String, BigDecimal> quote : day.getValue().entrySet()) {
                Currency quoteCurrency = targets.get(quote.getKey());
                if (quoteCurrency == null || quote.getValue() == null || quote.getValue().signum() <= 0) {
                    continue;
                }
                ExchangeRate rate = exchangeRateRepository
                        .findPair(userId, reference.getId(), quoteCurrency.getId(), day.getKey())
                        .orElseGet(() -> ExchangeRate.builder()
                                .owner(owner)
                                .baseCurrency(reference)
                                .quoteCurrency(quoteCurrency)
                                .date(day.getKey())
                                .build());
                rate.setRate(quote.getValue());
                exchangeRateRepository.save(rate);
                imported++;
            }
        }
        log.info("{} exchange rates synchronized for user {}", imported, userId);
        return new ExchangeRateSyncResponse(imported, unsupported);
    }

    @Transactional(readOnly = true)
    public RateTable loadTable(UUID userId) {
        return new RateTable(exchangeRateRepository.findAllByOwner(userId));
    }

    private Set<String> fetchSupportedCurrencies() {
        try {
            Map<String, String> currencies = restClient.get().uri("/currencies")
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, String>>() {
                    });
            return currencies == null ? Set.of() : currencies.keySet();
        } catch (RestClientException exception) {
            log.error("Failed to reach exchange rate provider", exception);
            throw new BadRequestException("Le service des taux de change est injoignable");
        }
    }

    private Map<LocalDate, Map<String, BigDecimal>> fetchRates(String base, List<String> symbols, LocalDate from) {
        String joined = String.join(",", symbols);
        try {
            if (from == null) {
                LatestRates latest = restClient.get()
                        .uri(uri -> uri.path("/latest").queryParam("base", base).queryParam("symbols", joined).build())
                        .retrieve()
                        .body(LatestRates.class);
                return latest == null || latest.rates() == null ? Map.of() : Map.of(latest.date(), latest.rates());
            }

            if (from.isAfter(LocalDate.now())) {
                throw new BadRequestException("La date de début ne peut pas être dans le futur");
            }
            SeriesRates seriesRates = restClient.get()
                    .uri(uri -> uri.path("/" + from + "..").queryParam("base", base).queryParam("symbols", joined).build())
                    .retrieve()
                    .body(SeriesRates.class);
            if (seriesRates == null || seriesRates.rates() == null) {
                return Map.of();
            }
            Map<LocalDate, Map<String, BigDecimal>> result = new TreeMap<>();
            seriesRates.rates().forEach((date, values) -> result.put(LocalDate.parse(date), values));
            return result;
        } catch (RestClientException exception) {
            log.error("Failed to fetch exchange rates", exception);
            throw new BadRequestException("Impossible de récupérer les taux de change");
        }
    }

    private static void validatePair(Currency base, Currency quote) {
        if (base.getId().equals(quote.getId())) {
            throw new BadRequestException("Les deux devises doivent être différentes");
        }
    }

    record LatestRates(String base, LocalDate date, Map<String, BigDecimal> rates) {
    }

    record SeriesRates(String base, Map<String, Map<String, BigDecimal>> rates) {
    }
}
