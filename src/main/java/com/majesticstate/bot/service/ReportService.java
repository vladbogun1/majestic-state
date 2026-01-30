package com.majesticstate.bot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.majesticstate.bot.domain.ReportConfig;
import com.majesticstate.bot.repository.ReportConfigRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
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

    @Transactional
    public void publishReport(ReportConfig config) {
        List<MessageEmbed> embeds = buildReportEmbeds(config);
        TextChannel channel = resolveChannel(config);
        if (channel == null) {
            log.warn("Channel not found for report config {}", config.getId());
            botLogService.log("WARN", "Channel not found for report config " + config.getName());
            updateRunTimestamps(config, (String) null);
            return;
        }

        String headerText = buildHeaderText(config);
        String footerText = buildFooterText(config);
        List<List<MessageEmbed>> batches = batchEmbeds(embeds, 10);
        int expectedMessages = 2 + batches.size();
        List<String> previousMessageIds = parseMessageIds(config.getLastMessageIds(), config.getLastMessageId());
        if (canEditMessages(previousMessageIds, expectedMessages)) {
            try {
                editExistingMessages(channel, config, previousMessageIds, headerText, footerText, batches, 0);
                return;
            } catch (Exception ex) {
                log.warn("Error editing messages, sending new ones", ex);
                botLogService.log("WARN", "Failed to edit report messages for " + config.getName());
            }
        }
        if (!previousMessageIds.isEmpty()) {
            deleteOldMessages(channel, previousMessageIds);
        }
        sendNewMessages(channel, config, headerText, footerText, batches);
    }

    private boolean canEditMessages(List<String> messageIds, int expectedMessages) {
        return !messageIds.isEmpty() && messageIds.size() == expectedMessages;
    }

    private void editExistingMessages(TextChannel channel,
                                      ReportConfig config,
                                      List<String> messageIds,
                                      String headerText,
                                      String footerText,
                                      List<List<MessageEmbed>> batches,
                                      int index) {
        String messageId = messageIds.get(index);
        if (index == 0) {
            channel.editMessageById(messageId, headerText).queue(
                    success -> editExistingMessages(channel, config, messageIds, headerText, footerText, batches, index + 1),
                    error -> {
                        handleEditFailure("header", messageId, error);
                        sendNewMessages(channel, config, headerText, footerText, batches);
                    }
            );
            return;
        }
        int batchIndex = index - 1;
        if (batchIndex == batches.size()) {
            channel.editMessageById(messageId, footerText).queue(
                    success -> updateRunTimestamps(config, messageIds),
                    error -> {
                        handleEditFailure("footer", messageId, error);
                        sendNewMessages(channel, config, headerText, footerText, batches);
                    }
            );
            return;
        }
        List<MessageEmbed> batch = batches.get(batchIndex);
        channel.editMessageEmbedsById(messageId, batch).queue(
                success -> {
                    editExistingMessages(channel, config, messageIds, headerText, footerText, batches, index + 1);
                },
                error -> {
                    handleEditFailure("batch", messageId, error);
                    sendNewMessages(channel, config, headerText, footerText, batches);
                }
        );
    }

    private void handleEditFailure(String label, String messageId, Throwable error) {
        if (error instanceof ErrorResponseException responseException
                && responseException.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
            log.info("Report message {} ({}) no longer exists, sending new ones", messageId, label);
            return;
        }
        log.warn("Failed to edit {} message {}, sending new ones", label, messageId, error);
    }

    private void deleteOldMessages(TextChannel channel, List<String> messageIds) {
        for (String messageId : messageIds) {
            channel.deleteMessageById(messageId).queue();
        }
    }

    private void sendNewMessages(TextChannel channel,
                                 ReportConfig config,
                                 String headerText,
                                 String footerText,
                                 List<List<MessageEmbed>> batches) {
        if (batches.isEmpty()) {
            updateRunTimestamps(config, (String) null);
            return;
        }
        List<String> messageIds = new ArrayList<>();
        channel.sendMessage(headerText).queue(message -> {
            messageIds.add(message.getId());
            sendEmbedBatch(channel, config, batches, 0, messageIds, footerText);
        });
    }

    private void sendEmbedBatch(TextChannel channel,
                                ReportConfig config,
                                List<List<MessageEmbed>> batches,
                                int index,
                                List<String> messageIds,
                                String footerText) {
        List<MessageEmbed> batch = batches.get(index);
        channel.sendMessageEmbeds(batch).queue(message -> {
            messageIds.add(message.getId());
            if (index == batches.size() - 1) {
                channel.sendMessage(footerText).queue(footerMessage -> {
                    messageIds.add(footerMessage.getId());
                    updateRunTimestamps(config, messageIds);
                });
            } else {
                sendEmbedBatch(channel, config, batches, index + 1, messageIds, footerText);
            }
        });
    }

    private void updateRunTimestamps(ReportConfig config, String messageId) {
        ReportConfig managed = reportConfigRepository.findById(config.getId()).orElse(config);
        managed.setLastRunAt(Instant.now());
        managed.setUpdatedAt(Instant.now());
        if (messageId != null) {
            managed.setLastMessageId(messageId);
            managed.setLastMessageIds(messageId);
        } else {
            managed.setLastMessageIds(null);
        }
        reportConfigRepository.save(managed);
    }

    private void updateRunTimestamps(ReportConfig config, List<String> messageIds) {
        ReportConfig managed = reportConfigRepository.findById(config.getId()).orElse(config);
        managed.setLastRunAt(Instant.now());
        managed.setUpdatedAt(Instant.now());
        String joined = String.join(",", messageIds);
        managed.setLastMessageIds(joined);
        managed.setLastMessageId(messageIds.isEmpty() ? null : messageIds.get(messageIds.size() - 1));
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

    private Map<Member, List<String>> collectMembersWithRoles(Guild guild, List<Role> roles, List<Role> excludedRoles) {
        Map<Member, List<String>> members = new LinkedHashMap<>();
        Set<Member> orderedMembers = new LinkedHashSet<>();
        for (Role role : roles) {
            orderedMembers.addAll(guild.getMembersWithRoles(role));
        }
        for (Member member : orderedMembers) {
            if (!excludedRoles.isEmpty() && excludedRoles.stream().anyMatch(member.getRoles()::contains)) {
                continue;
            }
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

    private List<MessageEmbed> buildReportEmbeds(ReportConfig config) {
        List<MessageEmbed> embeds = new ArrayList<>();
        if (botManager.getJda().isEmpty()) {
            embeds.add(new EmbedBuilder().setDescription("⚠️ Bot is offline").build());
            return embeds;
        }
        List<ReportSection> sections = parseSections(config.getRulesJson());
        ReportFormat format = parseFormat(config.getFormatJson());

        Guild guild = botManager.getJda().get().getGuildById(config.getGuildId());
        if (guild == null) {
            embeds.add(new EmbedBuilder().setDescription("⚠️ Guild not found for config " + config.getName()).build());
            return embeds;
        }

        for (ReportSection section : sections) {
            List<Role> roles = resolveRoles(guild, section.getRoleIds());
            List<String> excludeIds = section.getExcludeRoleIds() != null ? section.getExcludeRoleIds() : List.of();
            List<Role> excludedRoles = resolveRoles(guild, excludeIds);
            Map<Member, List<String>> memberRoles = collectMembersWithRoles(guild, roles, excludedRoles);
            List<String> lines = new ArrayList<>();
            int index = 1;
            for (Map.Entry<Member, List<String>> entry : memberRoles.entrySet()) {
                Member member = entry.getKey();
                List<String> matchedRoleNames = entry.getValue();
                StringBuilder line = new StringBuilder();
                line.append(index).append(". ");
                if (format.isShowMention()) {
                    line.append(member.getAsMention()).append(" ");
                }
                List<String> parts = new ArrayList<>();
                if (format.isShowRoleName()) {
                    parts.add(String.join(", ", matchedRoleNames));
                }
                parts.add(member.getEffectiveName());
                if (format.isShowUserId()) {
                    parts.add(member.getId());
                }
                line.append(String.join(" | ", parts));
                lines.add(line.toString());
                index++;
            }
            if (lines.isEmpty()) {
                lines.add("_Нет участников с указанными ролями_");
            }
            List<String> descriptionChunks = splitDescriptionLines(lines, 4096);
            for (int i = 0; i < descriptionChunks.size(); i++) {
                String title = i == 0 ? section.getTitle() : section.getTitle() + " (продолжение)";
                EmbedBuilder builder = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(descriptionChunks.get(i));
                embeds.add(builder.build());
            }
        }
        return embeds;
    }

    private List<String> splitDescriptionLines(List<String> lines, int maxLength) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
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

    private List<List<MessageEmbed>> batchEmbeds(List<MessageEmbed> embeds, int batchSize) {
        List<List<MessageEmbed>> batches = new ArrayList<>();
        for (int i = 0; i < embeds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, embeds.size());
            batches.add(embeds.subList(i, end));
        }
        return batches;
    }

    private List<String> parseMessageIds(String storedIds, String fallbackId) {
        List<String> ids = new ArrayList<>();
        if (storedIds != null && !storedIds.isBlank()) {
            for (String raw : storedIds.split(",")) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(trimmed);
                }
            }
        }
        if (ids.isEmpty() && fallbackId != null && !fallbackId.isBlank()) {
            ids.add(fallbackId);
        }
        return ids;
    }

    private String buildFooterText(ReportConfig config) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.systemDefault());
        String updated = formatter.format(Instant.now());
        return "_Обновлено: " + updated + "_";
    }

    private String buildHeaderText(ReportConfig config) {
        return "**" + config.getName() + "**";
    }
}
