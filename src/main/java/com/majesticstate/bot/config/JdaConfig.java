package com.majesticstate.bot.config;

import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
public class JdaConfig {

    @Bean
    public JDA jda(DiscordProperties properties) throws LoginException, InterruptedException {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw new IllegalStateException("DISCORD_TOKEN is required to start the bot");
        }
        return JDABuilder.createDefault(properties.getToken())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .build()
                .awaitReady();
    }
}
