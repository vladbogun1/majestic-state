package com.majesticstate.bot.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

@Service
public class DiscordService {
    private final JDA jda;

    public DiscordService(JDA jda) {
        this.jda = jda;
    }

    public List<Guild> listGuilds() {
        return jda.getGuilds().stream()
                .sorted(Comparator.comparing(Guild::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<TextChannel> listTextChannels(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return List.of();
        }
        return guild.getTextChannels().stream()
                .sorted(Comparator.comparing(TextChannel::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<Role> listRoles(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return List.of();
        }
        return guild.getRoles().stream()
                .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }
}
