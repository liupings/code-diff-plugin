package com.rj.diff;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CodeDiffNotifications {
    private static final String NOTIFICATION_GROUP = "CodeDiff.Notification.Group";

    private static final NotificationGroup GROUP =
            NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP);

    public static void showError(@NotNull Project project, @NotNull String title, @NotNull String message) {
        GROUP.createNotification(title, message, NotificationType.ERROR)
                .notify(project);
    }

    public static void showWarning(@NotNull Project project, @NotNull String title, @NotNull String message) {
        GROUP.createNotification(title, message, NotificationType.WARNING)
                .notify(project);
    }
}