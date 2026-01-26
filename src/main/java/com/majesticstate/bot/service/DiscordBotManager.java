package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.BotSettings;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
public class DiscordBotManager {
    private static final Logger log = LoggerFactory.getLogger(DiscordBotManager.class);

    private final BotSettingsService settingsService;
    private final BotLogService botLogService;
    private JDA jda;

    public DiscordBotManager(BotSettingsService settingsService, BotLogService botLogService) {
        this.settingsService = settingsService;
        this.botLogService = botLogService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        BotSettings settings = settingsService.getSettings();
        if (settings.isEnabled()) {
            startBot(settings.getToken());
        }
    }

    public synchronized Optional<JDA> getJda() {
        return Optional.ofNullable(jda);
    }

    public synchronized boolean isRunning() {
        return jda != null;
    }

    public synchronized void startBot(String token) {
        if (jda != null) {
            botLogService.log("WARN", "Bot already running");
            return;
        }
        if (token == null || token.isBlank()) {
            botLogService.log("ERROR", "Discord token is empty, cannot start bot");
            return;
        }
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .build()
                    .awaitReady();
            botLogService.log("INFO", "Bot started and connected to Discord");
        } catch (LoginException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            botLogService.log("ERROR", "Failed to start bot: " + ex.getMessage());
            log.error("Failed to start bot", ex);
        }
    }

    public synchronized void stopBot() {
        if (jda == null) {
            botLogService.log("WARN", "Bot is not running");
            return;
        }
        jda.shutdown();
        jda = null;
        botLogService.log("INFO", "Bot stopped");
    }

    @PreDestroy
    public void onShutdown() {
        stopBot();
    }
}
