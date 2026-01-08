package com.transactionapi.scheduler;

import com.transactionapi.service.ShareLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShareLinkCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkCleanupScheduler.class);
    private final ShareLinkService shareLinkService;

    public ShareLinkCleanupScheduler(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    // Run daily at 3:00 AM
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredShareLinks() {
        log.info("Starting cleanup of expired share links");
        int deleted = shareLinkService.deleteExpiredShares();
        log.info("Deleted {} expired share links", deleted);
    }
}
