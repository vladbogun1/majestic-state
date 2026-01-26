package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.ReportConfig;
import com.majesticstate.bot.repository.ReportConfigRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportConfigRepository repository;
    private final ReportService reportService;
    private final DiscordBotManager botManager;

    public ReportScheduler(ReportConfigRepository repository, ReportService reportService, DiscordBotManager botManager) {
        this.repository = repository;
        this.reportService = reportService;
        this.botManager = botManager;
    }

    @Scheduled(fixedDelayString = "${app.report.scheduler-interval-ms:60000}")
    public void runReports() {
        if (!botManager.isRunning()) {
            return;
        }
        List<ReportConfig> configs = repository.findAll();
        Instant now = Instant.now();
        for (ReportConfig config : configs) {
            if (!config.isEnabled()) {
                continue;
            }
            if (shouldRun(config, now)) {
                try {
                    reportService.publishReport(config);
                } catch (Exception ex) {
                    log.warn("Failed to publish report {}", config.getId(), ex);
                }
            }
        }
    }

    private boolean shouldRun(ReportConfig config, Instant now) {
        Instant lastRun = config.getLastRunAt();
        if (lastRun == null) {
            return true;
        }
        Duration elapsed = Duration.between(lastRun, now);
        return elapsed.toMinutes() >= config.getIntervalMinutes();
    }
}
