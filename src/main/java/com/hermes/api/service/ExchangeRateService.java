package com.hermes.api.service;

import com.hermes.api.dto.currency.ExchangeRateRequest;
import com.hermes.api.dto.currency.ExchangeRateResponse;
import com.hermes.api.dto.currency.ExchangeRateSyncResponse;
import com.hermes.api.exception.BadRequestException;
import com.hermes.api.exception.ResourceAlreadyExistsException;
import com.hermes.api.exception.ResourceNotFoundException;
import com.hermes.api.mapper.CurrencyMapper;
import com.hermes.api.model.Account;
import com.hermes.api.model.Currency;
import com.hermes.api.model.ExchangeRate;
import com.hermes.api.model.User;
import com.hermes.api.repository.AccountRepository;
import com.hermes.api.repository.ExchangeRateRepository;
import com.hermes.api.repository.UserRepository;
import com.hermes.api.utils.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository,
                               AccountRepository accountRepository,
                               UserRepository userRepository,
                               CurrencyService currencyService,
                               CurrencyMapper currencyMapper,
                               CurrentUser currentUser,
                               PlatformTransactionManager transactionManager,
                               @Value("${application.exchange-rates.provider-url}") String providerUrl) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currencyService = currencyService;
        this.currencyMapper = currencyMapper;
        this.currentUser = currentUser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
    // Les appels au fournisseur (jusqu'à 25 s) se font hors transaction : aucune connexion à la
    // base n'est monopolisée pendant l'attente
    public ExchangeRateSyncResponse sync(LocalDate from) {
        UUID userId = currentUser.id();
        if (from != null && from.isAfter(LocalDate.now())) {
            throw new BadRequestException("La date de début ne peut pas être dans le futur");
        }
        SyncPlan plan = Objects.requireNonNull(transactionTemplate.execute(status -> plan(userId)));
        if (plan.targets().isEmpty()) {
            return new ExchangeRateSyncResponse(0, List.of());
        }

        Set<String> supported = fetchSupportedCurrencies();
        if (!supported.contains(plan.referenceCode())) {
            throw new BadRequestException("La devise " + plan.referenceCode() + " n'est pas couverte par la BCE");
        }
        List<String> unsupported = plan.targets().keySet().stream().filter(code -> !supported.contains(code)).toList();
        List<String> symbols = plan.targets().keySet().stream().filter(supported::contains).toList();
        if (symbols.isEmpty()) {
            return new ExchangeRateSyncResponse(0, unsupported);
        }

        Map<LocalDate, Map<String, BigDecimal>> series = fetchRates(plan.referenceCode(), symbols, from);
        int imported = Objects.requireNonNull(transactionTemplate.execute(status -> save(userId, plan, series)));
        log.info("{} exchange rates synchronized for user {}", imported, userId);
        return new ExchangeRateSyncResponse(imported, unsupported);
    }

    // Devise de référence et devises des comptes à convertir, par code
    private record SyncPlan(Long referenceId, String referenceCode, Map<String, Long> targets) {
    }

    private SyncPlan plan(UUID userId) {
        User user = userRepository.findWithCurrencyById(userId).orElseThrow();
        Currency reference = user.getReferenceCurrency();
        if (reference == null) {
            throw new BadRequestException("Choisissez d'abord une devise de référence");
        }
        Map<String, Long> targets = accountRepository.findAllByOwner(userId).stream()
                .map(Account::getCurrency)
                .filter(c -> !c.getId().equals(reference.getId()))
                .collect(Collectors.toMap(Currency::getCode, Currency::getId, (a, b) -> a, TreeMap::new));
        return new SyncPlan(reference.getId(), reference.getCode(), targets);
    }

    private int save(UUID userId, SyncPlan plan, Map<LocalDate, Map<String, BigDecimal>> series) {
        Currency reference = currencyService.findAccessible(plan.referenceId());
        Map<String, Currency> targets = new HashMap<>();
        plan.targets().forEach((code, id) -> targets.put(code, currencyService.findAccessible(id)));
        User owner = currentUser.reference();

        int imported = 0;
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
        return imported;
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
