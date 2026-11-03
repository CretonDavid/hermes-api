package com.untitled.hermesapi.service;

import com.untitled.hermesapi.dto.stats.*;
import com.untitled.hermesapi.dto.stats.BalanceHistoryResponse.AccountSeries;
import com.untitled.hermesapi.dto.stats.BalanceHistoryResponse.BalancePoint;
import com.untitled.hermesapi.dto.stats.SavingsStatsResponse.SavingsMonth;
import com.untitled.hermesapi.exception.BadRequestException;
import com.untitled.hermesapi.model.Account;
import com.untitled.hermesapi.model.Category;
import com.untitled.hermesapi.model.Transaction;
import com.untitled.hermesapi.model.User;
import com.untitled.hermesapi.model.enums.CategoryType;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import com.untitled.hermesapi.repository.AccountRepository;
import com.untitled.hermesapi.repository.CategoryRepository;
import com.untitled.hermesapi.repository.TransactionRepository;
import com.untitled.hermesapi.repository.UserRepository;
import com.untitled.hermesapi.utils.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statistiques calculées en mémoire, converties dans la devise de référence de l'utilisateur.
 * <p>
 * Règles de classement d'une opération (hors virements internes, toujours exclus) :
 * une opération dans une catégorie de dépenses est une dépense (un crédit dans cette catégorie,
 * un remboursement par exemple, vient en diminution) ; une opération dans une catégorie de revenus
 * est un revenu ; une opération non catégorisée est une dépense si c'est un débit, un revenu sinon.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    public enum Granularity {DAY, WEEK, MONTH}

    private static final int MAX_RANGE_DAYS = 366 * 20;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final CurrentUser currentUser;

    private record Context(UUID userId, String currency, RateTable rates, Set<Long> accountIds) {

        boolean includes(Account account) {
            return accountIds.isEmpty() || accountIds.contains(account.getId());
        }

        BigDecimal convert(Transaction t) {
            return rates.convertOrZero(t.getAmount(), t.getAccount().getCurrency().getCode(), currency, t.getOperationDate());
        }

        BigDecimal convert(BigDecimal amount, Account account, LocalDate date) {
            return rates.convertOrZero(amount, account.getCurrency().getCode(), currency, date);
        }
    }

    // ---------- Synthèse ----------

    public StatsSummaryResponse summary(LocalDate from, LocalDate to, List<Long> accountIds) {
        Period period = period(from, to, LocalDate.now().withDayOfMonth(1));
        Context ctx = context(accountIds);

        Totals current = totals(ctx, load(ctx, period.from(), period.to()));
        long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        LocalDate previousTo = period.from().minusDays(1);
        Totals previous = totals(ctx, load(ctx, previousTo.minusDays(days - 1), previousTo));

        LocalDate today = LocalDate.now();
        BalanceCalculator balances = balances(ctx, today, today);
        BigDecimal netWorth = BigDecimal.ZERO;
        for (Account account : balances.accounts()) {
            netWorth = netWorth.add(ctx.convert(balances.balanceAt(account, today), account, today));
        }

        return new StatsSummaryResponse(
                ctx.currency(), period.from(), period.to(),
                round(current.income()), round(current.expenses()),
                round(current.income().subtract(current.expenses())),
                round(current.savings()),
                rate(current.income(), current.expenses()),
                round(previous.income()), round(previous.expenses()),
                round(netWorth),
                ctx.rates().missingCurrencies());
    }

    // ---------- Répartition par catégorie ----------

    /**
     * Répartition des dépenses (ou des revenus) au niveau situé juste sous parentId
     * (catégories principales si parentId est null).
     */
    public StatsListResponse<CategoryStat> byCategory(LocalDate from, LocalDate to, CategoryType type, Long parentId,
                                                      List<Long> accountIds) {
        Period period = period(from, to, LocalDate.now().withDayOfMonth(1));
        Context ctx = context(accountIds);
        Map<Long, Category> categories = categories(ctx.userId());
        if (parentId != null && !categories.containsKey(parentId)) {
            throw new BadRequestException("Catégorie introuvable");
        }
        Set<Long> parentsWithChildren = categories.values().stream()
                .filter(c -> c.getParent() != null)
                .map(c -> c.getParent().getId())
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        Map<Long, Long> counts = new HashMap<>();
        BigDecimal uncategorized = BigDecimal.ZERO;
        long uncategorizedCount = 0;
        BigDecimal direct = BigDecimal.ZERO;
        long directCount = 0;

        for (Transaction t : load(ctx, period.from(), period.to())) {
            CategoryType bucket = bucket(t);
            if (bucket != type) {
                continue;
            }
            BigDecimal value = contribution(t, bucket, ctx.convert(t));

            if (t.getCategory() == null) {
                if (parentId == null) {
                    uncategorized = uncategorized.add(value);
                    uncategorizedCount++;
                }
                continue;
            }

            List<Long> path = path(t.getCategory().getId(), categories);
            Long target;
            if (parentId == null) {
                target = path.getLast();
            } else {
                int index = path.indexOf(parentId);
                if (index < 0) {
                    continue;
                }
                if (index == 0) {
                    direct = direct.add(value);
                    directCount++;
                    continue;
                }
                target = path.get(index - 1);
            }
            amounts.merge(target, value, BigDecimal::add);
            counts.merge(target, 1L, Long::sum);
        }

        List<CategoryStat> items = new ArrayList<>();
        amounts.forEach((id, amount) -> {
            Category category = categories.get(id);
            items.add(new CategoryStat(id, category.getName(), color(category, categories), icon(category, categories),
                    amount, null, counts.get(id), parentsWithChildren.contains(id), false));
        });
        if (direct.signum() > 0) {
            Category parent = categories.get(parentId);
            items.add(new CategoryStat(parentId, parent.getName(), color(parent, categories), icon(parent, categories),
                    direct, null, directCount, false, true));
        }
        if (uncategorized.signum() > 0) {
            items.add(new CategoryStat(null, "Non catégorisé", "#9E9E9E", "help", uncategorized, null,
                    uncategorizedCount, false, false));
        }

        // Seuls les montants positifs ont un sens dans une répartition
        List<CategoryStat> positive = items.stream().filter(i -> i.amount().signum() > 0).toList();
        BigDecimal total = positive.stream().map(CategoryStat::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryStat> result = positive.stream()
                .map(i -> new CategoryStat(i.categoryId(), i.name(), i.color(), i.icon(), round(i.amount()),
                        total.signum() == 0 ? BigDecimal.ZERO : i.amount().divide(total, 4, RoundingMode.HALF_EVEN),
                        i.count(), i.hasChildren(), i.direct()))
                .sorted(Comparator.comparing(CategoryStat::amount).reversed())
                .toList();
        return new StatsListResponse<>(ctx.currency(), ctx.rates().missingCurrencies(), result);
    }

    // ---------- Evolution mensuelle ----------

    public StatsListResponse<MonthlyStat> monthly(LocalDate from, LocalDate to, List<Long> accountIds) {
        Period period = period(from, to, defaultYearStart());
        Context ctx = context(accountIds);

        Map<YearMonth, List<Transaction>> byMonth = load(ctx, period.from(), period.to()).stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getOperationDate())));

        List<MonthlyStat> items = new ArrayList<>();
        for (YearMonth month : months(period)) {
            Totals totals = totals(ctx, byMonth.getOrDefault(month, List.of()));
            items.add(new MonthlyStat(month, round(totals.income()), round(totals.expenses()),
                    round(totals.income().subtract(totals.expenses())), round(totals.savings())));
        }
        return new StatsListResponse<>(ctx.currency(), ctx.rates().missingCurrencies(), items);
    }

    public StatsListResponse<CategoryTrendStat> categoryTrend(Long categoryId, LocalDate from, LocalDate to,
                                                             List<Long> accountIds) {
        Period period = period(from, to, defaultYearStart());
        Context ctx = context(accountIds);
        Map<Long, Category> categories = categories(ctx.userId());
        Category category = categories.get(categoryId);
        if (category == null) {
            throw new BadRequestException("Catégorie introuvable");
        }

        Map<YearMonth, BigDecimal> amounts = new HashMap<>();
        for (Transaction t : load(ctx, period.from(), period.to())) {
            if (t.getCategory() == null || t.isTransfer() || !path(t.getCategory().getId(), categories).contains(categoryId)) {
                continue;
            }
            amounts.merge(YearMonth.from(t.getOperationDate()), contribution(t, category.getType(), ctx.convert(t)), BigDecimal::add);
        }
        List<CategoryTrendStat> items = months(period).stream()
                .map(month -> new CategoryTrendStat(month, round(amounts.getOrDefault(month, BigDecimal.ZERO))))
                .toList();
        return new StatsListResponse<>(ctx.currency(), ctx.rates().missingCurrencies(), items);
    }

    // ---------- Principaux tiers ----------

    public StatsListResponse<PayeeStat> topPayees(LocalDate from, LocalDate to, int limit, List<Long> accountIds) {
        Period period = period(from, to, LocalDate.now().withDayOfMonth(1));
        Context ctx = context(accountIds);

        Map<String, BigDecimal> amounts = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Transaction t : load(ctx, period.from(), period.to())) {
            if (bucket(t) != CategoryType.EXPENSE) {
                continue;
            }
            String name = t.getPayee() != null ? t.getPayee() : t.getLabel();
            String key = t.getPayee() != null
                    ? t.getPayee().trim().toLowerCase()
                    : "label:" + com.untitled.hermesapi.service.importing.LabelNormalizer.key(t.getLabel());
            names.putIfAbsent(key, name);
            amounts.merge(key, contribution(t, CategoryType.EXPENSE, ctx.convert(t)), BigDecimal::add);
            counts.merge(key, 1L, Long::sum);
        }
        List<PayeeStat> items = amounts.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(Math.clamp(limit, 1, 100))
                .map(e -> new PayeeStat(names.get(e.getKey()), round(e.getValue()), counts.get(e.getKey())))
                .toList();
        return new StatsListResponse<>(ctx.currency(), ctx.rates().missingCurrencies(), items);
    }

    // ---------- Evolution des soldes ----------

    public BalanceHistoryResponse balanceHistory(LocalDate from, LocalDate to, Granularity granularity, List<Long> accountIds) {
        Period period = period(from, to, defaultYearStart());
        Context ctx = context(accountIds);
        List<LocalDate> dates = points(period, granularity);

        BalanceCalculator balances = balances(ctx, period.from(), period.to());
        List<AccountSeries> series = new ArrayList<>();
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();

        for (Account account : balances.accounts()) {
            List<BalancePoint> points = new ArrayList<>();
            for (LocalDate date : dates) {
                BigDecimal balance = balances.balanceAt(account, date);
                points.add(new BalancePoint(date, round(balance)));
                totals.merge(date, ctx.convert(balance, account, date), BigDecimal::add);
            }
            series.add(new AccountSeries(account.getId(), account.getName(), account.getCurrency().getCode(),
                    account.getAccountType().isSavings(), points));
        }
        List<BalancePoint> total = dates.stream()
                .map(date -> new BalancePoint(date, round(totals.getOrDefault(date, BigDecimal.ZERO))))
                .toList();
        return new BalanceHistoryResponse(ctx.currency(), ctx.rates().missingCurrencies(), total, series);
    }

    // ---------- Epargne ----------

    public SavingsStatsResponse savings(LocalDate from, LocalDate to) {
        Period period = period(from, to, defaultYearStart());
        Context ctx = context(List.of());

        Map<YearMonth, List<Transaction>> byMonth = load(ctx, period.from(), period.to()).stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getOperationDate())));
        BalanceCalculator balances = balances(ctx, period.from(), period.to());
        List<Account> savingsAccounts = balances.accounts().stream()
                .filter(a -> a.getAccountType().isSavings())
                .toList();

        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        List<SavingsMonth> months = new ArrayList<>();

        for (YearMonth month : months(period)) {
            BigDecimal deposits = BigDecimal.ZERO;
            BigDecimal withdrawals = BigDecimal.ZERO;
            List<Transaction> transactions = byMonth.getOrDefault(month, List.of());
            for (Transaction t : transactions) {
                SavingsFlow flow = savingsFlow(t);
                if (flow == SavingsFlow.DEPOSIT) {
                    deposits = deposits.add(ctx.convert(t));
                } else if (flow == SavingsFlow.WITHDRAWAL) {
                    withdrawals = withdrawals.add(ctx.convert(t));
                }
            }
            Totals totals = totals(ctx, transactions);
            totalIncome = totalIncome.add(totals.income());
            totalExpenses = totalExpenses.add(totals.expenses());
            totalDeposits = totalDeposits.add(deposits);
            totalWithdrawals = totalWithdrawals.add(withdrawals);

            LocalDate end = month.atEndOfMonth().isAfter(period.to()) ? period.to() : month.atEndOfMonth();
            BigDecimal balance = BigDecimal.ZERO;
            for (Account account : savingsAccounts) {
                balance = balance.add(ctx.convert(balances.balanceAt(account, end), account, end));
            }
            months.add(new SavingsMonth(month, round(deposits), round(withdrawals), round(deposits.subtract(withdrawals)),
                    round(balance), rate(totals.income(), totals.expenses())));
        }

        LocalDate today = LocalDate.now();
        BalanceCalculator current = balances(ctx, today, today);
        BigDecimal currentSavings = BigDecimal.ZERO;
        for (Account account : current.accounts()) {
            if (account.getAccountType().isSavings()) {
                currentSavings = currentSavings.add(ctx.convert(current.balanceAt(account, today), account, today));
            }
        }

        return new SavingsStatsResponse(ctx.currency(), ctx.rates().missingCurrencies(),
                round(totalDeposits), round(totalWithdrawals), round(totalDeposits.subtract(totalWithdrawals)),
                round(currentSavings), rate(totalIncome, totalExpenses), months);
    }

    // ---------- Calculs communs ----------

    private record Totals(BigDecimal income, BigDecimal expenses, BigDecimal savings) {
    }

    private enum SavingsFlow {DEPOSIT, WITHDRAWAL}

    private Totals totals(Context ctx, List<Transaction> transactions) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal savings = BigDecimal.ZERO;
        for (Transaction t : transactions) {
            BigDecimal value = ctx.convert(t);
            SavingsFlow flow = savingsFlow(t);
            if (flow == SavingsFlow.DEPOSIT) {
                savings = savings.add(value);
            } else if (flow == SavingsFlow.WITHDRAWAL) {
                savings = savings.subtract(value);
            }

            CategoryType bucket = bucket(t);
            if (bucket == CategoryType.INCOME) {
                income = income.add(contribution(t, bucket, value));
            } else if (bucket == CategoryType.EXPENSE) {
                expenses = expenses.add(contribution(t, bucket, value));
            }
        }
        return new Totals(income, expenses, savings);
    }

    // null pour un virement interne
    private static CategoryType bucket(Transaction t) {
        if (t.isTransfer()) {
            return null;
        }
        if (t.getCategory() != null) {
            return t.getCategory().getType();
        }
        return t.getDirection() == TransactionDirection.DEBIT ? CategoryType.EXPENSE : CategoryType.INCOME;
    }

    private static BigDecimal contribution(Transaction t, CategoryType bucket, BigDecimal value) {
        boolean natural = bucket == CategoryType.EXPENSE
                ? t.getDirection() == TransactionDirection.DEBIT
                : t.getDirection() == TransactionDirection.CREDIT;
        return natural ? value : value.negate();
    }

    /**
     * Un virement d'un compte courant vers un compte d'épargne est un dépôt (compté sur l'opération
     * de crédit), l'inverse un retrait (compté sur l'opération de débit). Les virements entre deux
     * comptes d'épargne ou entre deux comptes courants ne comptent pas.
     */
    private static SavingsFlow savingsFlow(Transaction t) {
        if (!t.isTransfer()) {
            return null;
        }
        boolean accountSavings = t.getAccount().getAccountType().isSavings();
        boolean peerSavings = t.getTransferPeer().getAccount().getAccountType().isSavings();
        if (accountSavings == peerSavings || !accountSavings) {
            return null;
        }
        return t.getDirection() == TransactionDirection.CREDIT ? SavingsFlow.DEPOSIT : SavingsFlow.WITHDRAWAL;
    }

    private List<Transaction> load(Context ctx, LocalDate from, LocalDate to) {
        return transactionRepository.findForStats(ctx.userId(), from, to).stream()
                .filter(t -> ctx.includes(t.getAccount()))
                .toList();
    }

    private Context context(List<Long> accountIds) {
        UUID userId = currentUser.id();
        User user = userRepository.findWithCurrencyById(userId).orElseThrow();
        String currency = user.getReferenceCurrency() == null ? UserService.DEFAULT_REFERENCE_CURRENCY
                : user.getReferenceCurrency().getCode();
        return new Context(userId, currency, exchangeRateService.loadTable(userId),
                accountIds == null ? Set.of() : Set.copyOf(accountIds));
    }

    private Map<Long, Category> categories(UUID userId) {
        return categoryRepository.findAllByOwner(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    // Chemin d'une catégorie jusqu'à la racine : [catégorie, parent, ..., racine]
    private static List<Long> path(Long categoryId, Map<Long, Category> categories) {
        List<Long> path = new ArrayList<>();
        Long current = categoryId;
        while (current != null && !path.contains(current)) {
            path.add(current);
            Category category = categories.get(current);
            current = category == null || category.getParent() == null ? null : category.getParent().getId();
        }
        return path;
    }

    // Les sous-catégories sans couleur ni icône héritent de celles de leur parent
    private static String color(Category category, Map<Long, Category> categories) {
        for (Long id : path(category.getId(), categories)) {
            String color = categories.get(id).getColor();
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static String icon(Category category, Map<Long, Category> categories) {
        for (Long id : path(category.getId(), categories)) {
            String icon = categories.get(id).getIcon();
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    /**
     * Soldes des comptes à n'importe quelle date de [from, to] : solde initial + opérations antérieures
     * à from + opérations de la période jusqu'à la date demandée.
     */
    private BalanceCalculator balances(Context ctx, LocalDate from, LocalDate to) {
        List<Account> accounts = accountRepository.findAllByOwner(ctx.userId()).stream()
                .filter(ctx::includes)
                .toList();
        Map<Long, BigDecimal> before = transactionRepository.sumByAccountBefore(ctx.userId(), from).stream()
                .collect(Collectors.toMap(TransactionRepository.AccountSumView::getAccountId,
                        v -> v.getTotal() == null ? BigDecimal.ZERO : v.getTotal()));

        Map<Long, NavigableMap<LocalDate, BigDecimal>> daily = new HashMap<>();
        for (Transaction t : transactionRepository.findForStats(ctx.userId(), from, to)) {
            daily.computeIfAbsent(t.getAccount().getId(), k -> new TreeMap<>())
                    .merge(t.getOperationDate(), t.signedAmount(), BigDecimal::add);
        }
        // Cumul jour par jour
        Map<Long, NavigableMap<LocalDate, BigDecimal>> cumulative = new HashMap<>();
        daily.forEach((accountId, days) -> {
            NavigableMap<LocalDate, BigDecimal> running = new TreeMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<LocalDate, BigDecimal> day : days.entrySet()) {
                sum = sum.add(day.getValue());
                running.put(day.getKey(), sum);
            }
            cumulative.put(accountId, running);
        });
        return new BalanceCalculator(accounts, before, cumulative);
    }

    private record BalanceCalculator(List<Account> accounts, Map<Long, BigDecimal> before,
                                     Map<Long, NavigableMap<LocalDate, BigDecimal>> cumulative) {

        BigDecimal balanceAt(Account account, LocalDate date) {
            // Le compte n'existait pas encore : il ne compte pas dans le patrimoine
            if (date.isBefore(account.getOpeningDate())) {
                return BigDecimal.ZERO;
            }
            BigDecimal balance = account.getInitialBalance().add(before.getOrDefault(account.getId(), BigDecimal.ZERO));
            NavigableMap<LocalDate, BigDecimal> running = cumulative.get(account.getId());
            if (running != null) {
                Map.Entry<LocalDate, BigDecimal> entry = running.floorEntry(date);
                if (entry != null) {
                    balance = balance.add(entry.getValue());
                }
            }
            return balance;
        }
    }

    private record Period(LocalDate from, LocalDate to) {
    }

    private static Period period(LocalDate from, LocalDate to, LocalDate defaultFrom) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? defaultFrom : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("La date de début doit précéder la date de fin");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BadRequestException("La période demandée est trop longue");
        }
        return new Period(start, end);
    }

    private static LocalDate defaultYearStart() {
        return LocalDate.now().withDayOfMonth(1).minusMonths(11);
    }

    private static List<YearMonth> months(Period period) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(period.from()); !m.isAfter(YearMonth.from(period.to())); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private static List<LocalDate> points(Period period, Granularity granularity) {
        long days = ChronoUnit.DAYS.between(period.from(), period.to());
        Granularity effective = granularity != null ? granularity
                : days <= 62 ? Granularity.DAY : days <= 200 ? Granularity.WEEK : Granularity.MONTH;

        List<LocalDate> dates = new ArrayList<>();
        switch (effective) {
            case DAY -> {
                for (LocalDate d = period.from(); !d.isAfter(period.to()); d = d.plusDays(1)) {
                    dates.add(d);
                }
            }
            case WEEK -> {
                for (LocalDate d = period.from(); !d.isAfter(period.to()); d = d.plusWeeks(1)) {
                    dates.add(d);
                }
            }
            case MONTH -> {
                for (YearMonth m : months(period)) {
                    LocalDate end = m.atEndOfMonth();
                    dates.add(end.isAfter(period.to()) ? period.to() : end);
                }
            }
        }
        if (!dates.getLast().equals(period.to())) {
            dates.add(period.to());
        }
        return dates.size() > 2000 ? dates.subList(dates.size() - 2000, dates.size()) : dates;
    }

    private static BigDecimal rate(BigDecimal income, BigDecimal expenses) {
        if (income.signum() <= 0) {
            return null;
        }
        return income.subtract(expenses).divide(income, 4, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
