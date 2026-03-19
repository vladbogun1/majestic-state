package com.majesticstate.bot.service;

import java.awt.Color;

public enum HrActionType {
    HIRE(
            "принятие",
            "Принятие",
            "✅",
            new Color(125, 211, 252),
            "Новый сотрудник",
            "Принятие нового сотрудника в организацию",
            "Причина принятия"
    ),
    FIRE(
            "увольнение",
            "Увольнение",
            "⛔",
            new Color(239, 68, 68),
            "Сотрудник",
            "Увольнение сотрудника из организации",
            "Причина"
    ),
    PROMOTE(
            "повышение",
            "Повышение",
            "⬆️",
            new Color(34, 197, 94),
            "Сотрудник",
            null,
            "Причина повышения"
    ),
    DEMOTE(
            "понижение",
            "Понижение",
            "⬇️",
            new Color(245, 158, 11),
            "Сотрудник",
            null,
            "Причина понижения"
    );

    private final String commandName;
    private final String displayName;
    private final String emoji;
    private final Color color;
    private final String employeeFieldLabel;
    private final String defaultActionText;
    private final String reasonFieldLabel;

    HrActionType(String commandName,
                 String displayName,
                 String emoji,
                 Color color,
                 String employeeFieldLabel,
                 String defaultActionText,
                 String reasonFieldLabel) {
        this.commandName = commandName;
        this.displayName = displayName;
        this.emoji = emoji;
        this.color = color;
        this.employeeFieldLabel = employeeFieldLabel;
        this.defaultActionText = defaultActionText;
        this.reasonFieldLabel = reasonFieldLabel;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public Color getColor() {
        return color;
    }

    public String getEmployeeFieldLabel() {
        return employeeFieldLabel;
    }

    public String getDefaultActionText() {
        return defaultActionText;
    }

    public String getReasonFieldLabel() {
        return reasonFieldLabel;
    }
}
