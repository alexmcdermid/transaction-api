package com.transactionapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.constants.AssetType;
import com.transactionapi.constants.Currency;
import com.transactionapi.constants.OptionType;
import com.transactionapi.constants.TaxYearPassStatus;
import com.transactionapi.constants.TaxYearReportType;
import com.transactionapi.constants.TradeDirection;
import com.transactionapi.dto.TradeRequest;
import com.transactionapi.dto.TradeResponse;
import com.transactionapi.model.Account;
import com.transactionapi.model.TaxYearPass;
import com.transactionapi.model.User;
import com.transactionapi.repository.AccountRepository;
import com.transactionapi.repository.TaxYearPassRepository;
import com.transactionapi.service.TradeService;
import com.transactionapi.service.UserService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsControllerTest {

    private static final int PAID_YEAR = 2025;
    private static final String PASS_REQUIRED = "Tax year pass required for " + PAID_YEAR;
    private static final String TRADE_LEDGER_EXPORT = "{\"reportType\":\"TRADE_LEDGER\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TaxYearPassRepository taxYearPassRepository;

    @Autowired
    private UserService userService;

    @Test
    void overviewListsTradeYearsPlusTheCurrentYearNewestFirst() throws Exception {
        String authId = "reports-overview-user";
        int currentTaxYear = Year.now().getValue();
        recordWin(authId, "AAPL", null, LocalDate.of(2023, 3, 1));
        recordWin(authId, "MSFT", null, LocalDate.of(2023, 9, 1));
        recordWin(authId, "NVDA", null, LocalDate.of(2024, 5, 1));
        recordWin("reports-overview-other-user", "TSLA", null, LocalDate.of(2022, 5, 1));
        grantPass(authId, 2024, TaxYearPassStatus.PENDING);
        grantPass(authId, 2024, TaxYearPassStatus.PAID);
        grantPass(authId, 2023, TaxYearPassStatus.REFUNDED);

        mockMvc.perform(get(ApiPaths.REPORTS_TAX_YEARS).header("X-User-Id", authId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTaxYear").value(currentTaxYear))
                .andExpect(jsonPath("$.checkoutEnabled").value(false))
                .andExpect(jsonPath("$.price").value(nullValue()))
                .andExpect(jsonPath("$.years.length()").value(3))
                .andExpect(jsonPath("$.years[0].taxYear").value(currentTaxYear))
                .andExpect(jsonPath("$.years[0].closedTradeCount").value(0))
                .andExpect(jsonPath("$.years[0].passStatus").value(nullValue()))
                .andExpect(jsonPath("$.years[0].unlocked").value(false))
                .andExpect(jsonPath("$.years[0].purchasable").value(false))
                .andExpect(jsonPath("$.years[1].taxYear").value(2024))
                .andExpect(jsonPath("$.years[1].closedTradeCount").value(1))
                .andExpect(jsonPath("$.years[1].passStatus").value("PAID"))
                .andExpect(jsonPath("$.years[1].unlocked").value(true))
                .andExpect(jsonPath("$.years[1].purchasable").value(false))
                .andExpect(jsonPath("$.years[2].taxYear").value(2023))
                .andExpect(jsonPath("$.years[2].closedTradeCount").value(2))
                .andExpect(jsonPath("$.years[2].passStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.years[2].unlocked").value(false))
                .andExpect(jsonPath("$.years[2].purchasable").value(false));
    }

    @Test
    void overviewForANewUserListsOnlyTheCurrentYear() throws Exception {
        mockMvc.perform(get(ApiPaths.REPORTS_TAX_YEARS).header("X-User-Id", "reports-overview-new-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years.length()").value(1))
                .andExpect(jsonPath("$.years[0].taxYear").value(Year.now().getValue()))
                .andExpect(jsonPath("$.years[0].closedTradeCount").value(0));
    }

    @Test
    void summaryAndExportsRequireAPaidPassForThatYear() throws Exception {
        String authId = "reports-no-pass-user";
        recordWin(authId, "AAPL", null, LocalDate.of(PAID_YEAR, 4, 1));
        grantPass(authId, PAID_YEAR - 1, TaxYearPassStatus.PAID);

        postSummary(authId, PAID_YEAR, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PASS_REQUIRED));
        postExport(authId, PAID_YEAR, TRADE_LEDGER_EXPORT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PASS_REQUIRED));
    }

    @ParameterizedTest
    @EnumSource(value = TaxYearPassStatus.class, names = {"PENDING", "PROCESSING", "FAILED", "REFUNDED"})
    void passesThatAreNotPaidDoNotUnlockReports(TaxYearPassStatus status) throws Exception {
        String authId = "reports-unpaid-" + status.name().toLowerCase();
        grantPass(authId, PAID_YEAR, status);

        postSummary(authId, PAID_YEAR, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PASS_REQUIRED));
        postExport(authId, PAID_YEAR, TRADE_LEDGER_EXPORT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PASS_REQUIRED));
    }

    @Test
    void summaryTotalsThePaidYearPerCurrency() throws Exception {
        String authId = "reports-summary-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        recordStock(authId, "AAPL", Currency.USD, TradeDirection.LONG, "100.00", "110.00", null,
                LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 3));
        recordStock(authId, "SHOP", Currency.CAD, TradeDirection.LONG, "50.00", "45.00", null,
                LocalDate.of(2025, 12, 30), LocalDate.of(2025, 12, 30));
        recordWin(authId, "MSFT", null, LocalDate.of(2024, 6, 1));

        postSummary(authId, PAID_YEAR, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxYear").value(PAID_YEAR))
                .andExpect(jsonPath("$.tradeCount").value(2))
                .andExpect(jsonPath("$.firstClosedOn").value("2025-01-03"))
                .andExpect(jsonPath("$.lastClosedOn").value("2025-12-30"))
                .andExpect(jsonPath("$.repurchaseFlagCount").value(0))
                .andExpect(jsonPath("$.totals.length()").value(2))
                .andExpect(jsonPath("$.totals[0].key").value("ALL"))
                .andExpect(jsonPath("$.totals[0].label").value("All trades"))
                .andExpect(jsonPath("$.totals[0].currency").value("CAD"))
                .andExpect(jsonPath("$.totals[0].taxFree").value(nullValue()))
                .andExpect(jsonPath("$.totals[0].losingTrades").value(1))
                .andExpect(jsonPath("$.totals[0].losses").value(-50.0))
                .andExpect(jsonPath("$.totals[0].netRealizedPnl").value(-50.0))
                .andExpect(jsonPath("$.totals[1].currency").value("USD"))
                .andExpect(jsonPath("$.totals[1].winningTrades").value(1))
                .andExpect(jsonPath("$.totals[1].gains").value(100.0))
                .andExpect(jsonPath("$.months[0].key").value("2025-01"))
                .andExpect(jsonPath("$.months[1].key").value("2025-12"))
                .andExpect(jsonPath("$.accounts[0].key").value("UNASSIGNED"))
                .andExpect(jsonPath("$.accounts[0].taxFree").value(false))
                .andExpect(jsonPath("$.symbols[0].key").value("AAPL"))
                .andExpect(jsonPath("$.assetTypes[0].label").value("Stocks"))
                .andExpect(jsonPath("$.taxTreatment[0].key").value("TAXABLE"));

        postSummary(authId, PAID_YEAR, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(2));
    }

    @Test
    void taxYearsOutsideTheSupportedRangeAreBadRequestsBeforeThePassCheck() throws Exception {
        String authId = "reports-invalid-year-user";
        int currentTaxYear = Year.now().getValue();
        String expectedMessage = "Tax year must be between 2000 and " + currentTaxYear;

        for (int taxYear : List.of(1999, currentTaxYear + 1)) {
            postSummary(authId, taxYear, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(expectedMessage));
            postExport(authId, taxYear, TRADE_LEDGER_EXPORT)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(expectedMessage));
        }
    }

    @Test
    void unknownOrMissingReportTypeIsABadRequest() throws Exception {
        String authId = "reports-report-type-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);

        postExport(authId, PAID_YEAR, "{\"reportType\":\"TAX_SLIP\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Report Type has an invalid value"));
        postExport(authId, PAID_YEAR, "{\"includeNotes\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Report Type must not be null"));
    }

    @Test
    void oversizedFiltersAreBadRequests() throws Exception {
        String authId = "reports-filter-validation-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);

        postSummary(authId, PAID_YEAR, "{\"symbols\":[\"ABCDEFGHIJKLM\"]}")
                .andExpect(status().isBadRequest());
        postExport(authId, PAID_YEAR, "{\"reportType\":\"TRADE_LEDGER\",\"filters\":{\"symbols\":[\"ABCDEFGHIJKLM\"]}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountIdsMustAllBelongToTheCaller() throws Exception {
        String authId = "reports-account-owner";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        UUID ownAccount = createAccount(authId, "IBKR", false);
        UUID foreignAccount = createAccount("reports-account-stranger", "Stranger", false);
        String foreignFilters = "{\"accountIds\":[\"" + ownAccount + "\",\"" + foreignAccount + "\"]}";
        String unknownFilters = "{\"accountIds\":[\"" + UUID.randomUUID() + "\"]}";

        postSummary(authId, PAID_YEAR, foreignFilters)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account access denied"));
        postSummary(authId, PAID_YEAR, unknownFilters)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account access denied"));
        postExport(authId, PAID_YEAR, "{\"reportType\":\"DISPOSITIONS\",\"filters\":" + foreignFilters + "}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account access denied"));
    }

    @Test
    void passIsCheckedBeforeAccountOwnership() throws Exception {
        UUID foreignAccount = createAccount("reports-account-stranger-2", "Stranger", false);

        postSummary("reports-no-pass-foreign-account-user", PAID_YEAR, "{\"accountIds\":[\"" + foreignAccount + "\"]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(PASS_REQUIRED));
    }

    @ParameterizedTest
    @EnumSource(TaxYearReportType.class)
    void exportsDownloadAsUncachedCsvAttachments(TaxYearReportType reportType) throws Exception {
        String authId = "reports-export-" + reportType.slug();
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        recordWin(authId, "AAPL", null, LocalDate.of(PAID_YEAR, 4, 1));

        String csv = postExport(authId, PAID_YEAR, "{\"reportType\":\"" + reportType.name() + "\"}")
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().stringValues(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tradelog-2025-" + reportType.slug() + ".csv\""
                ))
                .andExpect(header().stringValues("X-Content-Type-Options", "nosniff"))
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String expectedHeaderStart = switch (reportType) {
            case TRADE_LEDGER -> "trade_id,account_id,account_name,";
            case DISPOSITIONS -> "trade_id,description,symbol,";
            case ANNUAL_SUMMARY -> "section,key,label,";
            case REPURCHASE_REVIEW -> "loss_trade_id,symbol,loss_closed_on,";
        };
        assertThat(csv).startsWith(expectedHeaderStart);
    }

    @Test
    void exportsStillReturnCsvWhenTheClientAcceptsJson() throws Exception {
        String authId = "reports-export-accept-json-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);

        mockMvc.perform(post(ApiPaths.REPORTS_TAX_YEARS + "/" + PAID_YEAR + "/exports")
                        .header("X-User-Id", authId)
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TRADE_LEDGER_EXPORT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }

    @Test
    void reportsNeverIncludeAnotherUsersTrades() throws Exception {
        String authId = "reports-isolation-user";
        String otherAuthId = "reports-isolation-other-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        grantPass(otherAuthId, PAID_YEAR, TaxYearPassStatus.PAID);
        TradeResponse ownLoss = recordStock(authId, "AAPL", Currency.USD, TradeDirection.LONG, "110.00", "100.00", null,
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 15));
        TradeResponse otherLoss = recordStock(otherAuthId, "AAPL", Currency.USD, TradeDirection.LONG, "110.00", "100.00",
                null, LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 15));
        TradeResponse otherRebuy = recordStock(otherAuthId, "AAPL", Currency.USD, TradeDirection.LONG, "100.00", "105.00",
                null, LocalDate.of(2025, 6, 16), LocalDate.of(2025, 6, 20));

        List<CSVRecord> ledger = parseCsv(exportCsv(authId, TRADE_LEDGER_EXPORT));
        List<CSVRecord> review = parseCsv(exportCsv(authId, "{\"reportType\":\"REPURCHASE_REVIEW\"}"));

        assertThat(ledger).extracting(record -> record.get("trade_id")).containsExactly(ownLoss.id().toString());
        assertThat(review).isEmpty();
        postSummary(authId, PAID_YEAR, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(1))
                .andExpect(jsonPath("$.repurchaseFlagCount").value(0));
        assertThat(parseCsv(exportCsv(otherAuthId, "{\"reportType\":\"REPURCHASE_REVIEW\"}")))
                .extracting(record -> record.get("loss_trade_id"), record -> record.get("match_trade_id"))
                .containsExactly(tuple(otherLoss.id().toString(), otherRebuy.id().toString()));
    }

    @Test
    void includeUnassignedDefaultsToTrueAndFalseExcludesUnassignedTrades() throws Exception {
        String authId = "reports-unassigned-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        UUID ibkr = createAccount(authId, "IBKR", false);
        TradeResponse assigned = recordWin(authId, "AAPL", ibkr, LocalDate.of(2025, 2, 1));
        recordWin(authId, "MSFT", null, LocalDate.of(2025, 2, 2));

        assertSummaryTradeCount(authId, "{}", 2);
        assertSummaryTradeCount(authId, "{\"includeUnassigned\":null}", 2);
        assertSummaryTradeCount(authId, "{\"includeUnassigned\":false}", 1);
        assertSummaryTradeCount(authId, "{\"accountIds\":[\"" + ibkr + "\"]}", 2);
        assertSummaryTradeCount(authId, "{\"accountIds\":[\"" + ibkr + "\"],\"includeUnassigned\":false}", 1);
        assertSummaryTradeCount(authId, "{\"accountIds\":[],\"includeUnassigned\":true}", 2);

        List<CSVRecord> ledger = parseCsv(exportCsv(authId,
                "{\"reportType\":\"TRADE_LEDGER\",\"filters\":{\"includeUnassigned\":false}}"));
        assertThat(ledger).extracting(record -> record.get("trade_id")).containsExactly(assigned.id().toString());
    }

    @Test
    void filtersNarrowBySymbolAssetTypeCurrencyAndDirection() throws Exception {
        String authId = "reports-filter-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        recordWin(authId, "AAPL", null, LocalDate.of(2025, 3, 3));
        recordStock(authId, "SHOP", Currency.CAD, TradeDirection.SHORT, "80.00", "75.00", null,
                LocalDate.of(2025, 3, 4), LocalDate.of(2025, 3, 4));
        tradeService.createTrade(new TradeRequest(
                "AAPL", AssetType.OPTION, Currency.USD, TradeDirection.LONG, new BigDecimal("1"),
                new BigDecimal("2.00"), new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                OptionType.CALL, new BigDecimal("150"), LocalDate.of(2025, 3, 21),
                LocalDate.of(2025, 3, 5), LocalDate.of(2025, 3, 5), null
        ), authId);

        assertSummaryTradeCount(authId, "{\"symbols\":[\" aapl \"]}", 2);
        assertSummaryTradeCount(authId, "{\"assetTypes\":[\"OPTION\"]}", 1);
        assertSummaryTradeCount(authId, "{\"currencies\":[\"CAD\"]}", 1);
        assertSummaryTradeCount(authId, "{\"directions\":[\"SHORT\"]}", 1);
        assertSummaryTradeCount(authId, "{\"symbols\":[\"AAPL\"],\"assetTypes\":[\"STOCK\"]}", 1);
        assertSummaryTradeCount(authId, "{\"symbols\":[],\"assetTypes\":[],\"currencies\":[],\"directions\":[]}", 3);
    }

    @Test
    void repurchaseReviewMatchesAcrossTheYearBoundaryInAnyAccountIgnoringFilters() throws Exception {
        String authId = "reports-repurchase-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        UUID ibkr = createAccount(authId, "IBKR", false);
        UUID tfsa = createAccount(authId, "TFSA", true);
        TradeResponse decemberLoss = recordStock(authId, "AAPL", Currency.USD, TradeDirection.LONG, "110.00", "100.00",
                ibkr, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 20));
        TradeResponse januaryRebuy = recordStock(authId, "AAPL", Currency.USD, TradeDirection.LONG, "100.00", "104.00",
                tfsa, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15));
        recordStock(authId, "AAPL", Currency.USD, TradeDirection.LONG, "100.00", "104.00", tfsa,
                LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 25));
        String ibkrOnly = "{\"accountIds\":[\"" + ibkr + "\"],\"includeUnassigned\":false}";

        postSummary(authId, PAID_YEAR, ibkrOnly)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(1))
                .andExpect(jsonPath("$.repurchaseFlagCount").value(1));
        List<CSVRecord> review = parseCsv(exportCsv(authId,
                "{\"reportType\":\"REPURCHASE_REVIEW\",\"filters\":" + ibkrOnly + "}"));

        assertThat(review).singleElement().satisfies(record -> {
            assertThat(record.get("loss_trade_id")).isEqualTo(decemberLoss.id().toString());
            assertThat(record.get("loss_amount")).isEqualTo("-100.00");
            assertThat(record.get("loss_account_name")).isEqualTo("IBKR");
            assertThat(record.get("match_trade_id")).isEqualTo(januaryRebuy.id().toString());
            assertThat(record.get("match_opened_on")).isEqualTo("2026-01-10");
            assertThat(record.get("match_account_name")).isEqualTo("TFSA");
            assertThat(record.get("match_account_tax_free")).isEqualTo("true");
            assertThat(record.get("days_from_loss")).isEqualTo("21");
            assertThat(record.get("possible_same_position")).isEqualTo("false");
        });
    }

    @Test
    void tradeLedgerIncludesNotesOnlyWhenRequested() throws Exception {
        String authId = "reports-notes-user";
        grantPass(authId, PAID_YEAR, TaxYearPassStatus.PAID);
        tradeService.createTrade(new TradeRequest(
                "AAPL", AssetType.STOCK, Currency.USD, TradeDirection.LONG, new BigDecimal("1"),
                new BigDecimal("100.00"), new BigDecimal("101.00"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                null, null, null, LocalDate.of(2025, 3, 3), LocalDate.of(2025, 3, 3), "=private note"
        ), authId);

        CSVRecord withoutNotes = parseCsv(exportCsv(authId, TRADE_LEDGER_EXPORT)).getFirst();
        CSVRecord withNotes = parseCsv(exportCsv(authId,
                "{\"reportType\":\"TRADE_LEDGER\",\"includeNotes\":true}")).getFirst();

        assertThat(withoutNotes.get("notes")).isEmpty();
        assertThat(withNotes.get("notes")).isEqualTo("'=private note");
        assertThat(withNotes.get("quantity")).isEqualTo("1");
        assertThat(withNotes.get("recorded_realized_pnl")).isEqualTo("1.00");
    }

    private void assertSummaryTradeCount(String authId, String filters, int expectedTradeCount) throws Exception {
        postSummary(authId, PAID_YEAR, filters)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(expectedTradeCount));
    }

    private ResultActions postSummary(String authId, int taxYear, String body) throws Exception {
        MockHttpServletRequestBuilder request = post(ApiPaths.REPORTS_TAX_YEARS + "/" + taxYear + "/summary")
                .header("X-User-Id", authId);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions postExport(String authId, int taxYear, String body) throws Exception {
        return mockMvc.perform(post(ApiPaths.REPORTS_TAX_YEARS + "/" + taxYear + "/exports")
                .header("X-User-Id", authId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String exportCsv(String authId, String body) throws Exception {
        return postExport(authId, PAID_YEAR, body)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<CSVRecord> parseCsv(String csv) throws IOException {
        try (CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.builder().setHeader().get())) {
            return parser.getRecords();
        }
    }

    private TradeResponse recordWin(String authId, String symbol, UUID accountId, LocalDate closedOn) {
        return recordStock(authId, symbol, Currency.USD, TradeDirection.LONG, "100.00", "110.00", accountId,
                closedOn, closedOn);
    }

    private TradeResponse recordStock(
            String authId,
            String symbol,
            Currency currency,
            TradeDirection direction,
            String entryPrice,
            String exitPrice,
            UUID accountId,
            LocalDate openedOn,
            LocalDate closedOn
    ) {
        return tradeService.createTrade(new TradeRequest(
                symbol, AssetType.STOCK, currency, direction, new BigDecimal("10"),
                new BigDecimal(entryPrice), new BigDecimal(exitPrice), BigDecimal.ZERO, BigDecimal.ZERO, accountId,
                null, null, null, openedOn, closedOn, null
        ), authId);
    }

    private UUID createAccount(String authId, String name, boolean taxFree) {
        Account account = new Account();
        account.setUserId(authId);
        account.setName(name);
        account.setTaxFree(taxFree);
        return accountRepository.save(account).getId();
    }

    private void grantPass(String authId, int taxYear, TaxYearPassStatus status) {
        User user = userService.getOrCreateUser(authId, null);
        TaxYearPass pass = new TaxYearPass();
        pass.setUserId(user.getId());
        pass.setTaxYear(taxYear);
        pass.setStatus(status);
        taxYearPassRepository.save(pass);
    }
}
