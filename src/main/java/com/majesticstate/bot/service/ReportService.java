package com.majesticstate.bot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majesticstate.bot.domain.ReportConfig;
import com.majesticstate.bot.repository.ReportConfigRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final JDA jda;
    private final ObjectMapper objectMapper;
    private final ReportConfigRepository reportConfigRepository;

    public ReportService(JDA jda, ObjectMapper objectMapper, ReportConfigRepository reportConfigRepository) {
        this.jda = jda;
        this.objectMapper = objectMapper;
        this.reportConfigRepository = reportConfigRepository;
    }

    public String buildReportContent(ReportConfig config) {
        List<ReportSection> sections = parseSections(config.getRulesJson());
        ReportFormat format = parseFormat(config.getFormatJson());

        Guild guild = jda.getGuildById(config.getGuildId());
        if (guild == null) {
            return "⚠️ Guild not found for config " + config.getName();
        }

        StringBuilder builder = new StringBuilder();
        for (ReportSection section : sections) {
            builder.append("**").append(section.getTitle()).append("**\n");
            List<Role> roles = resolveRoles(guild, section.getRoleIds());
            Map<Member, List<String>> memberRoles = collectMembersWithRoles(guild, roles);
            int index = 1;
            for (Map.Entry<Member, List<String>> entry : memberRoles.entrySet()) {
                Member member = entry.getKey();
                List<String> matchedRoleNames = entry.getValue();
                builder.append(index).append(". ");
                if (format.isShowMention()) {
                    builder.append(member.getAsMention()).append(" ");
                }
                List<String> parts = new ArrayList<>();
                if (format.isShowRoleName()) {
                    parts.add(String.join(", ", matchedRoleNames));
                }
                parts.add(member.getEffectiveName());
                if (format.isShowUserId()) {
                    parts.add(member.getId());
                }
                builder.append(String.join(" | ", parts));
                builder.append("\n");
                index++;
            }
            if (memberRoles.isEmpty()) {
                builder.append("_Нет участников с указанными ролями_\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    @Transactional
    public void publishReport(ReportConfig config) {
        String content = buildReportContent(config);
        TextChannel channel = resolveChannel(config);
        if (channel == null) {
            log.warn("Channel not found for report config {}", config.getId());
            updateRunTimestamps(config, null);
            return;
        }

        String messageId = config.getLastMessageId();
        if (messageId != null && !messageId.isBlank()) {
            try {
                channel.editMessageById(messageId, content).queue(
                        success -> updateRunTimestamps(config, messageId),
                        error -> {
                            log.warn("Failed to edit message {}, sending new one", messageId, error);
                            sendNewMessage(channel, config, content);
                        }
                );
                return;
            } catch (Exception ex) {
                log.warn("Error editing message, sending new one", ex);
            }
        }
        sendNewMessage(channel, config, content);
    }

    private void sendNewMessage(TextChannel channel, ReportConfig config, String content) {
        channel.sendMessage(content).queue(message -> updateRunTimestamps(config, message.getId()));
    }

    private void updateRunTimestamps(ReportConfig config, String messageId) {
        ReportConfig managed = reportConfigRepository.findById(config.getId()).orElse(config);
        managed.setLastRunAt(Instant.now());
        managed.setUpdatedAt(Instant.now());
        if (messageId != null) {
            managed.setLastMessageId(messageId);
        }
        reportConfigRepository.save(managed);
    }

    private TextChannel resolveChannel(ReportConfig config) {
        Guild guild = jda.getGuildById(config.getGuildId());
        if (guild == null) {
            return null;
        }
        return guild.getTextChannelById(config.getChannelId());
    }

    private List<ReportSection> parseSections(String rulesJson) {
        try {
            return objectMapper.readValue(rulesJson, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse rules JSON", ex);
        }
    }

    private ReportFormat parseFormat(String formatJson) {
        try {
            return objectMapper.readValue(formatJson, ReportFormat.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse format JSON", ex);
        }
    }

    private List<Role> resolveRoles(Guild guild, List<String> roleIds) {
        List<Role> roles = new ArrayList<>();
        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    private Map<Member, List<String>> collectMembersWithRoles(Guild guild, List<Role> roles) {
        Map<Member, List<String>> members = new LinkedHashMap<>();
        Set<Member> orderedMembers = new LinkedHashSet<>();
        for (Role role : roles) {
            orderedMembers.addAll(guild.getMembersWithRoles(role));
        }
        for (Member member : orderedMembers) {
            List<String> matched = new ArrayList<>();
            for (Role role : roles) {
                if (member.getRoles().contains(role)) {
                    matched.add(role.getName());
                }
            }
            members.put(member, matched);
        }
        return members;
    }
}
