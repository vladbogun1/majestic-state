package com.majesticstate.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiscordReportBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscordReportBotApplication.class, args);
    }
}
