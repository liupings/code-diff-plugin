package com.rj.diff.dialog;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class OpenCodeDiffAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || editor == null) {
            return;
        }

        String currentCode = editor.getDocument().getText();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        String fileName = file != null ? file.getName() : "unknown";

        CodeDiffDialog dialog = new CodeDiffDialog(project, currentCode, fileName);
        if (dialog.showAndGet()) {
            String modifiedCode = dialog.getModifiedCode();
            if (!modifiedCode.equals(currentCode)) {
                // Apply changes to the document
                editor.getDocument().setText(modifiedCode);
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable the action only when there's an active editor
        boolean enabled = e.getProject() != null &&
                e.getData(CommonDataKeys.EDITOR) != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}