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

    private final DiscordBotManager botManager;
    private final ObjectMapper objectMapper;
    private final ReportConfigRepository reportConfigRepository;
    private final BotLogService botLogService;

    public ReportService(DiscordBotManager botManager,
                         ObjectMapper objectMapper,
                         ReportConfigRepository reportConfigRepository,
                         BotLogService botLogService) {
        this.botManager = botManager;
        this.objectMapper = objectMapper;
        this.reportConfigRepository = reportConfigRepository;
        this.botLogService = botLogService;
    }

    public String buildReportContent(ReportConfig config) {
        if (botManager.getJda().isEmpty()) {
            return "⚠️ Bot is offline";
        }
        List<ReportSection> sections = parseSections(config.getRulesJson());
        ReportFormat format = parseFormat(config.getFormatJson());

        Guild guild = botManager.getJda().get().getGuildById(config.getGuildId());
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
        List<String> chunks = splitMessage(content, 1900);
        TextChannel channel = resolveChannel(config);
        if (channel == null) {
            log.warn("Channel not found for report config {}", config.getId());
            botLogService.log("WARN", "Channel not found for report config " + config.getName());
            updateRunTimestamps(config, null);
            return;
        }

        String messageId = config.getLastMessageId();
        if (messageId != null && !messageId.isBlank() && chunks.size() == 1) {
            try {
                channel.editMessageById(messageId, chunks.getFirst()).queue(
                        success -> updateRunTimestamps(config, messageId),
                        error -> {
                            log.warn("Failed to edit message {}, sending new one", messageId, error);
                            sendNewMessages(channel, config, chunks);
                        }
                );
                return;
            } catch (Exception ex) {
                log.warn("Error editing message, sending new one", ex);
                botLogService.log("WARN", "Failed to edit report message for " + config.getName());
            }
        }
        sendNewMessages(channel, config, chunks);
    }

    private void sendNewMessages(TextChannel channel, ReportConfig config, List<String> chunks) {
        if (chunks.isEmpty()) {
            updateRunTimestamps(config, null);
            return;
        }
        sendChunk(channel, config, chunks, 0);
    }

    private void sendChunk(TextChannel channel, ReportConfig config, List<String> chunks, int index) {
        channel.sendMessage(chunks.get(index)).queue(message -> {
            if (index == chunks.size() - 1) {
                updateRunTimestamps(config, message.getId());
            } else {
                sendChunk(channel, config, chunks, index + 1);
            }
        });
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
        if (botManager.getJda().isEmpty()) {
            return null;
        }
        Guild guild = botManager.getJda().get().getGuildById(config.getGuildId());
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

    private List<String> splitMessage(String content, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n")) {
            String append = line + "\n";
            if (current.length() + append.length() > maxLength) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                if (append.length() > maxLength) {
                    int start = 0;
                    while (start < append.length()) {
                        int end = Math.min(start + maxLength, append.length());
                        chunks.add(append.substring(start, end));
                        start = end;
                    }
                } else {
                    current.append(append);
                }
            } else {
                current.append(append);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
