package com.transactionapi.constants;

/** Tax year CSV exports. The slug names the downloaded file: tradelog-{year}-{slug}.csv. */
public enum TaxYearReportType {
    TRADE_LEDGER("trade-ledger"),
    DISPOSITIONS("dispositions"),
    ANNUAL_SUMMARY("annual-summary"),
    REPURCHASE_REVIEW("repurchase-review");

    private final String slug;

    TaxYearReportType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
