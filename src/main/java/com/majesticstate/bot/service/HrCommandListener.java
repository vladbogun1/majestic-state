package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.HrChannelSettings;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.springframework.stereotype.Component;

@Component
public class HrCommandListener extends net.dv8tion.jda.api.hooks.ListenerAdapter {
    private static final String SENIOR_ROLE_ID = "1382610959712911364";
    private static final String CONFIG_COMMAND = "настроить-кадровые-каналы";
    private static final String REQUEST_PROMOTION_COMMAND = "запрос-на-повышение";
    private static final String APPROVE_BUTTON_PREFIX = "hr:approve:";
    private static final String REJECT_BUTTON_PREFIX = "hr:reject:";
    private static final String REJECT_MODAL_PREFIX = "hr:reject-modal:";
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
    private static final DateTimeFormatter MOSCOW_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm '(по МСК)'", new Locale("ru", "RU"));

    private final HrChannelSettingsService hrChannelSettingsService;
    private final BotLogService botLogService;

    public HrCommandListener(HrChannelSettingsService hrChannelSettingsService, BotLogService botLogService) {
        this.hrChannelSettingsService = hrChannelSettingsService;
        this.botLogService = botLogService;
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        registerCommands(event.getGuild());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Эта команда доступна только на сервере.").setEphemeral(true).queue();
            return;
        }
        String commandName = event.getName();
        if (CONFIG_COMMAND.equals(commandName)) {
            handleSetupCommand(event);
            return;
        }
        if (REQUEST_PROMOTION_COMMAND.equals(commandName)) {
            handlePromotionRequestForm(event);
            return;
        }
        for (HrActionType actionType : EnumSet.allOf(HrActionType.class)) {
            if (actionType.getCommandName().equals(commandName)) {
                handleAuditCommand(event, actionType);
                return;
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Кнопки доступны только на сервере.").setEphemeral(true).queue();
            return;
        }
        String componentId = event.getComponentId();
        if (componentId.startsWith(APPROVE_BUTTON_PREFIX)) {
            if (!hasSeniorRole(event.getMember())) {
                event.reply("Только @Старший состав может обрабатывать запросы.").setEphemeral(true).queue();
                return;
            }
            event.deferEdit().queue(
                    success -> applyPromotionDecision(event.getMessage(), event.getMember(), true, null, null),
                    error -> botLogService.log("WARN", "Failed to defer promotion approval interaction: " + error.getMessage())
            );
            return;
        }
        if (componentId.startsWith(REJECT_BUTTON_PREFIX)) {
            if (!hasSeniorRole(event.getMember())) {
                event.reply("Только @Старший состав может обрабатывать запросы.").setEphemeral(true).queue();
                return;
            }
            String messageId = event.getMessageId();
            String channelId = event.getChannel().getId();
            TextInput reasonInput = TextInput.create("reason", TextInputStyle.PARAGRAPH)
                    .setRequired(true)
                    .setMinLength(3)
                    .setMaxLength(400)
                    .build();
            Modal modal = Modal.create(REJECT_MODAL_PREFIX + channelId + ":" + messageId, "Отклонение запроса")
                    .addComponents(Label.of("Причина отклонения", reasonInput))
                    .build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.reply("Форма доступна только на сервере.").setEphemeral(true).queue();
            return;
        }
        String modalId = event.getModalId();
        if (REQUEST_PROMOTION_COMMAND.equals(modalId)) {
            submitPromotionRequest(event);
            return;
        }
        if (modalId.startsWith(REJECT_MODAL_PREFIX)) {
            if (!hasSeniorRole(event.getMember())) {
                event.reply("Только @Старший состав может обрабатывать запросы.").setEphemeral(true).queue();
                return;
            }
            String payload = modalId.substring(REJECT_MODAL_PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                event.reply("Не удалось обработать отклонение.").setEphemeral(true).queue();
                return;
            }
            String channelId = parts[0];
            String messageId = parts[1];
            ModalMapping reason = event.getValue("reason");
            TextChannel channel = event.getGuild().getTextChannelById(channelId);
            if (channel == null) {
                event.reply("Канал с заявкой не найден.").setEphemeral(true).queue();
                return;
            }
            event.deferReply(true).queue(
                    hook -> channel.retrieveMessageById(messageId).queue(
                            message -> updatePromotionRequestMessage(message, event.getMember(), false, reason == null ? "Не указана" : reason.getAsString(), hook),
                            error -> hook.editOriginal("Сообщение с заявкой не найдено.").queue()
                    ),
                    error -> botLogService.log("WARN", "Failed to defer promotion rejection interaction: " + error.getMessage())
            );
        }
    }

    private void handleSetupCommand(SlashCommandInteractionEvent event) {
        if (!hasSeniorRole(event.getMember())) {
            event.reply("Команда доступна только роли @Старший состав.").setEphemeral(true).queue();
            return;
        }
        String hireChannelId = requireTextChannelId(event.getOption("канал-принятия"));
        String fireChannelId = requireTextChannelId(event.getOption("канал-увольнений"));
        String promoteChannelId = requireTextChannelId(event.getOption("канал-повышений"));
        String demoteChannelId = requireTextChannelId(event.getOption("канал-понижений"));
        String promotionRequestChannelId = requireTextChannelId(event.getOption("канал-запросов-на-повышение"));
        hrChannelSettingsService.save(
                event.getGuild().getId(),
                hireChannelId,
                fireChannelId,
                promoteChannelId,
                demoteChannelId,
                promotionRequestChannelId
        );
        event.reply("Кадровые каналы сохранены. Запросы на повышение будут идти в "
                        + formatChannelReference(promotionRequestChannelId != null ? promotionRequestChannelId : promoteChannelId)
                        + ".")
                .setEphemeral(true)
                .queue();
    }

    private void handleAuditCommand(SlashCommandInteractionEvent event, HrActionType actionType) {
        HrChannelSettings settings = hrChannelSettingsService.findByGuildId(event.getGuild().getId()).orElse(null);
        TextChannel targetChannel = resolveAuditTargetChannel(event.getGuild(), settings, actionType, event.getChannel().getId());
        if (targetChannel == null) {
            event.reply("Не удалось определить канал для отправки кадрового сообщения.").setEphemeral(true).queue();
            return;
        }
        User employee = Objects.requireNonNull(event.getOption("сотрудник")).getAsUser();
        String passport = optionString(event, "номер-паспорта");
        String reason = optionString(event, "причина");
        Integer rank = event.getOption("ранг", OptionMapping::getAsInt);
        String actionText = actionType.getDefaultActionText();
        if (actionType == HrActionType.PROMOTE && rank != null) {
            actionText = "Повышение на " + rank + " ранг";
        }
        if (actionType == HrActionType.DEMOTE && rank != null) {
            actionText = "Понижение до " + rank + " ранга";
        }

        Member employeeMember = event.getGuild().getMember(employee);
        String employeeDisplayName = employeeMember != null ? employeeMember.getEffectiveName() : employee.getName();
        EmbedBuilder embed = buildAuditEmbed(event.getMember(), employee, employeeDisplayName, passport, reason, actionType, actionText);
        String content = event.getMember().getAsMention() + " заполнил(а) кадровый аудит на " + employee.getAsMention();
        targetChannel.sendMessage(content)
                .setEmbeds(embed.build())
                .queue(
                        success -> event.reply("Отчёт отправлен в " + targetChannel.getAsMention() + ".").setEphemeral(true).queue(),
                        error -> event.reply("Не удалось отправить отчёт в настроенный канал.").setEphemeral(true).queue()
                );
    }

    private void handlePromotionRequestForm(SlashCommandInteractionEvent event) {
        HrChannelSettings settings = hrChannelSettingsService.findByGuildId(event.getGuild().getId()).orElse(null);
        if (resolvePromotionRequestChannel(event.getGuild(), settings, event.getChannel().getId()) == null) {
            event.reply("Не удалось определить канал для запроса на повышение.").setEphemeral(true).queue();
            return;
        }
        TextInput passportInput = TextInput.create("passport", TextInputStyle.SHORT)
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(32)
                .build();
        TextInput currentRankInput = TextInput.create("current-rank", TextInputStyle.SHORT)
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(32)
                .build();
        TextInput newRankInput = TextInput.create("new-rank", TextInputStyle.SHORT)
                .setRequired(true)
                .setMinLength(1)
                .setMaxLength(32)
                .build();
        TextInput approvedReportInput = TextInput.create("approved-report", TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setMinLength(5)
                .setMaxLength(400)
                .build();
        Modal modal = Modal.create(REQUEST_PROMOTION_COMMAND, "Запрос на повышение")
                .addComponents(
                        Label.of("Номер паспорта", passportInput),
                        Label.of("Текущий ранг", currentRankInput),
                        Label.of("Новый ранг", newRankInput),
                        Label.of("Ссылка на одобренный отчёт", approvedReportInput)
                )
                .build();
        event.replyModal(modal).queue();
    }

    private void submitPromotionRequest(ModalInteractionEvent event) {
        HrChannelSettings settings = hrChannelSettingsService.findByGuildId(event.getGuild().getId()).orElse(null);
        TextChannel targetChannel = resolvePromotionRequestChannel(event.getGuild(), settings, event.getChannel().getId());
        if (targetChannel == null) {
            event.reply("Не удалось определить канал для запроса на повышение.").setEphemeral(true).queue();
            return;
        }

        String passport = modalValue(event, "passport");
        String currentRank = modalValue(event, "current-rank");
        String newRank = modalValue(event, "new-rank");
        String approvedReport = modalValue(event, "approved-report");

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(0xF59E0B)
                .setTitle("Запрос на повышение")
                .setDescription(buildPromotionRequestDescription(event.getMember(), passport, currentRank, newRank, approvedReport));

        List<ActionRow> components = List.of(ActionRow.of(
                Button.success(APPROVE_BUTTON_PREFIX + event.getUser().getId(), "Одобрить"),
                Button.danger(REJECT_BUTTON_PREFIX + event.getUser().getId(), "Отклонить")
        ));

        targetChannel.sendMessage("<@&" + SENIOR_ROLE_ID + ">").setEmbeds(embed.build()).setComponents(components).queue(
                success -> event.reply("Запрос на повышение отправлен в " + targetChannel.getAsMention() + ".").setEphemeral(true).queue(),
                error -> event.reply("Не удалось отправить запрос на повышение.").setEphemeral(true).queue()
        );
    }

    private void applyPromotionDecision(Message message,
                                        Member reviewer,
                                        boolean approved,
                                        String rejectReason,
                                        InteractionHook hook) {
        updatePromotionRequestMessage(message, reviewer, approved, rejectReason, hook);
    }

    private void updatePromotionRequestMessage(Message message,
                                               Member reviewer,
                                               boolean approved,
                                               String rejectReason,
                                               InteractionHook hook) {
        if (message.getEmbeds().isEmpty()) {
            updateDeferredResponse(hook, "У сообщения нет embed для обновления.");
            return;
        }
        var original = message.getEmbeds().getFirst();
        EmbedBuilder embed = new EmbedBuilder(original)
                .setFooter((approved ? "Одобрено" : "Отклонено") + " • Проверил(а): " + memberSummary(reviewer), null);
        if (!approved) {
            embed.addField("Причина отклонения", rejectReason, false);
        }
        List<ActionRow> rows = approved
                ? List.of(ActionRow.of(
                        Button.success("approved", "Одобрено").asDisabled(),
                        Button.secondary("reviewer", trimButtonLabel("Проверил(а): " + memberSummary(reviewer))).asDisabled()
                ))
                : List.of(ActionRow.of(
                        Button.danger("rejected", "Отклонено").asDisabled(),
                        Button.secondary("reason", trimButtonLabel("Причина: " + rejectReason)).asDisabled(),
                        Button.secondary("reviewer", trimButtonLabel("Проверил(а): " + memberSummary(reviewer))).asDisabled()
                ));
        message.editMessageEmbeds(embed.build()).setComponents(rows).queue(
                success -> {
                    if (approved) {
                        sendApprovedPromotionAudit(message, reviewer, hook);
                    } else {
                        updateDeferredResponse(hook, "Запрос отклонён.");
                    }
                },
                error -> updateDeferredResponse(hook, "Не удалось обновить сообщение с заявкой.")
        );
    }


    private void sendApprovedPromotionAudit(Message requestMessage,
                                            Member reviewer,
                                            InteractionHook hook) {
        PromotionRequestPayload payload = extractPromotionRequestPayload(requestMessage);
        if (payload == null) {
            updateDeferredResponse(hook, "Запрос одобрен, но не удалось собрать данные для кадрового сообщения о повышении.");
            return;
        }

        HrChannelSettings settings = hrChannelSettingsService.findByGuildId(requestMessage.getGuild().getId()).orElse(null);
        TextChannel targetChannel = resolveAuditTargetChannel(
                requestMessage.getGuild(),
                settings,
                HrActionType.PROMOTE,
                requestMessage.getChannel().getId()
        );
        if (targetChannel == null) {
            updateDeferredResponse(hook, "Запрос одобрен, но не удалось определить канал для кадрового повышения.");
            return;
        }

        User employee = requestMessage.getJDA().getUserById(payload.userId());
        if (employee == null) {
            updateDeferredResponse(hook, "Запрос одобрен, но пользователь из заявки не найден для кадрового повышения.");
            return;
        }

        Member employeeMember = requestMessage.getGuild().getMemberById(payload.userId());
        String employeeDisplayName = employeeMember != null ? employeeMember.getEffectiveName() : employee.getName();
        String requestLink = requestMessage.getJumpUrl();
        String actionText = "Повышение на " + payload.newRank() + " ранг";
        EmbedBuilder embed = buildAuditEmbed(
                reviewer,
                employee,
                employeeDisplayName,
                payload.passport(),
                requestLink,
                HrActionType.PROMOTE,
                actionText
        );
        String content = reviewer.getAsMention() + " заполнил(а) кадровый аудит на " + employee.getAsMention();
        targetChannel.sendMessage(content).setEmbeds(embed.build()).queue(
                success -> updateDeferredResponse(hook, "Запрос одобрен, кадровое повышение отправлено в " + targetChannel.getAsMention() + "."),
                error -> updateDeferredResponse(hook, "Запрос одобрен, но не удалось отправить кадровое повышение в канал.")
        );
    }


    private void updateDeferredResponse(InteractionHook hook, String message) {
        if (hook == null) {
            return;
        }
        hook.editOriginal(message).queue();
    }

    private PromotionRequestPayload extractPromotionRequestPayload(Message requestMessage) {
        if (requestMessage.getEmbeds().isEmpty()) {
            return null;
        }
        String description = requestMessage.getEmbeds().getFirst().getDescription();
        if (description == null || description.isBlank()) {
            return null;
        }
        String requesterLine = extractSectionValue(description, "Заполнил(а)");
        String passport = extractSectionValue(description, "Номер паспорта");
        String newRank = extractSectionValue(description, "Новый ранг");
        if (requesterLine == null || passport == null || newRank == null) {
            return null;
        }
        Matcher matcher = USER_MENTION_PATTERN.matcher(requesterLine);
        if (!matcher.find()) {
            return null;
        }
        return new PromotionRequestPayload(matcher.group(1), passport, newRank);
    }

    private String extractSectionValue(String description, String title) {
        String marker = "**" + title + "**\n• ";
        int start = description.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int nextSection = description.indexOf("\n\n**", valueStart);
        if (nextSection < 0) {
            nextSection = description.length();
        }
        return description.substring(valueStart, nextSection).trim();
    }

    private TextChannel resolveAuditTargetChannel(Guild guild,
                                                  HrChannelSettings settings,
                                                  HrActionType actionType,
                                                  String fallbackChannelId) {
        if (settings != null) {
            String configuredChannelId = hrChannelSettingsService.resolveChannelId(settings, actionType);
            TextChannel configuredChannel = guild.getTextChannelById(configuredChannelId);
            if (configuredChannel != null) {
                return configuredChannel;
            }
        }
        return guild.getTextChannelById(fallbackChannelId);
    }

    private TextChannel resolvePromotionRequestChannel(Guild guild,
                                                       HrChannelSettings settings,
                                                       String fallbackChannelId) {
        if (settings != null) {
            String configuredChannelId = hrChannelSettingsService.resolvePromotionRequestChannelId(settings);
            TextChannel configuredChannel = guild.getTextChannelById(configuredChannelId);
            if (configuredChannel != null) {
                return configuredChannel;
            }
        }
        return guild.getTextChannelById(fallbackChannelId);
    }

    private EmbedBuilder buildAuditEmbed(Member author,
                                         User employee,
                                         String employeeDisplayName,
                                         String passport,
                                         String reason,
                                         HrActionType actionType,
                                         String actionText) {
        String employeeMention = employee.getAsMention();
        String employeeLine = employeeMention + " | " + employeeDisplayName;
        return new EmbedBuilder()
                .setColor(actionType.getColor())
                .setTitle("📄 Кадровый аудит | " + actionType.getEmoji() + " " + actionType.getDisplayName())
                .setDescription("**Заполнил:**\n• " + memberSummaryWithMention(author) + "\n\n"
                        + "**" + actionType.getEmployeeFieldLabel() + ":**\n• " + employeeLine + "\n\n"
                        + "**Статик сотрудника:**\n• " + passport + "\n\n"
                        + "**Действие:**\n• " + actionText + "\n\n"
                        + "**" + actionType.getReasonFieldLabel() + ":**\n• " + reason + "\n\n"
                        + "Дата заполнения: " + formatMoscowNow());
    }

    private String buildPromotionRequestDescription(Member author,
                                                    String passport,
                                                    String currentRank,
                                                    String newRank,
                                                    String approvedReport) {
        return "**Заполнил(а)**\n• " + memberSummaryWithMention(author) + "\n\n"
                + "**Номер паспорта**\n• " + passport + "\n\n"
                + "**Текущий ранг**\n• " + currentRank + "\n\n"
                + "**Новый ранг**\n• " + newRank + "\n\n"
                + "**Ссылка на одобренный отчёт на повышение**\n• " + approvedReport + "\n\n"
                + formatMoscowNow().replace(" (по МСК)", "");
    }

    private String memberSummaryWithMention(Member member) {
        return member.getAsMention() + " | " + memberSummary(member);
    }

    private String memberSummary(Member member) {
        return member.getEffectiveName();
    }

    private boolean hasSeniorRole(Member member) {
        return member.getRoles().stream().anyMatch(role -> SENIOR_ROLE_ID.equals(role.getId()))
                || member.hasPermission(Permission.ADMINISTRATOR);
    }

    private void registerCommands(Guild guild) {
        CommandListUpdateAction updateAction = guild.updateCommands()
                .addCommands(
                        buildAuditCommand(HrActionType.FIRE),
                        buildAuditCommand(HrActionType.PROMOTE).addOptions(new OptionData(OptionType.INTEGER, "ранг", "Ранг после повышения", true)),
                        buildAuditCommand(HrActionType.DEMOTE).addOptions(new OptionData(OptionType.INTEGER, "ранг", "Ранг после понижения", true)),
                        buildAuditCommand(HrActionType.HIRE),
                        Commands.slash(REQUEST_PROMOTION_COMMAND, "Отправить запрос на повышение через форму"),
                        Commands.slash(CONFIG_COMMAND, "Настроить каналы кадрового аудита")
                                .addOptions(channelOption("канал-увольнений", "Куда отправлять увольнения", true))
                                .addOptions(channelOption("канал-повышений", "Куда отправлять повышения", true))
                                .addOptions(channelOption("канал-понижений", "Куда отправлять понижения", true))
                                .addOptions(channelOption("канал-принятия", "Куда отправлять принятия", true))
                                .addOptions(channelOption("канал-запросов-на-повышение", "Куда отправлять запросы на повышение", false))
                );
        updateAction.queue(
                success -> botLogService.log("INFO", "Registered HR slash commands for guild " + guild.getName()),
                error -> botLogService.log("WARN", "Failed to register HR commands for guild " + guild.getName() + ": " + error.getMessage())
        );
    }

    private SlashCommandData buildAuditCommand(HrActionType actionType) {
        return Commands.slash(actionType.getCommandName(), "Отправить кадровый аудит: " + actionType.getDisplayName())
                .addOptions(new OptionData(OptionType.USER, "сотрудник", "Сотрудник", true))
                .addOptions(new OptionData(OptionType.STRING, "номер-паспорта", "Номер паспорта сотрудника", true).setMaxLength(32))
                .addOptions(new OptionData(OptionType.STRING, "причина", "Причина или ссылка", true).setMaxLength(400));
    }

    private OptionData channelOption(String name, String description, boolean required) {
        return new OptionData(OptionType.CHANNEL, name, description, required)
                .setChannelTypes(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT);
    }

    private String optionString(SlashCommandInteractionEvent event, String name) {
        return Objects.requireNonNull(event.getOption(name)).getAsString();
    }

    private String modalValue(ModalInteractionEvent event, String id) {
        return Objects.requireNonNull(event.getValue(id)).getAsString();
    }

    private String requireTextChannelId(OptionMapping option) {
        if (option == null) {
            return null;
        }
        Channel channel = option.getAsChannel();
        if (channel instanceof GuildChannel guildChannel) {
            return guildChannel.getId();
        }
        return null;
    }

    private String formatChannelReference(String channelId) {
        return channelId == null ? "не настроенный канал" : "<#" + channelId + ">";
    }

    private String formatMoscowNow() {
        return ZonedDateTime.now(MOSCOW_ZONE).format(MOSCOW_TIME_FORMATTER);
    }

    private String trimButtonLabel(String value) {
        return value.length() <= 80 ? value : value.substring(0, 77) + "...";
    }

    private record PromotionRequestPayload(String userId, String passport, String newRank) {
    }
}

