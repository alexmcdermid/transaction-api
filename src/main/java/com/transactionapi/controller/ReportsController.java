package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import com.transactionapi.dto.AnnualSummaryResponse;
import com.transactionapi.dto.ReportFilters;
import com.transactionapi.dto.TaxYearExportRequest;
import com.transactionapi.dto.TaxYearOverviewResponse;
import com.transactionapi.model.User;
import com.transactionapi.security.UserIdResolver;
import com.transactionapi.service.TaxYearReportService;
import com.transactionapi.service.UserService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.REPORTS_TAX_YEARS)
public class ReportsController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final TaxYearReportService taxYearReportService;
    private final UserIdResolver userIdResolver;
    private final UserService userService;

    public ReportsController(
            TaxYearReportService taxYearReportService,
            UserIdResolver userIdResolver,
            UserService userService
    ) {
        this.taxYearReportService = taxYearReportService;
        this.userIdResolver = userIdResolver;
        this.userService = userService;
    }

    @GetMapping
    public TaxYearOverviewResponse overview(Authentication authentication) {
        return taxYearReportService.overview(resolveUser(authentication));
    }

    @PostMapping("/{taxYear}/summary")
    public AnnualSummaryResponse summary(
            Authentication authentication,
            @PathVariable int taxYear,
            @RequestBody(required = false) @Valid ReportFilters filters
    ) {
        return taxYearReportService.summary(resolveUser(authentication), taxYear, filters);
    }

    @PostMapping("/{taxYear}/exports")
    public ResponseEntity<byte[]> export(
            Authentication authentication,
            @PathVariable int taxYear,
            @Valid @RequestBody TaxYearExportRequest request
    ) {
        byte[] csv = taxYearReportService.export(resolveUser(authentication), taxYear, request);
        String filename = "tradelog-" + taxYear + "-" + request.reportType().slug() + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(csv);
    }

    private User resolveUser(Authentication authentication) {
        String authId = userIdResolver.requireUserId(authentication);
        return userService.getOrCreateUser(authId, userIdResolver.resolveEmail(authentication));
    }
}
