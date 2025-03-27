package com.rj.diff.dialog1;

import cn.hutool.http.HttpUtil;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.rj.diff.CodeDiffNotifications;
import org.jetbrains.annotations.NotNull;

public class MyOpenCodeDiffAction extends AnAction {

    private Project project;

    private FileType fileType;


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        this.project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        String currentCode = editor.getDocument().getText();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        String fileName = file != null ? file.getName() : "unknown";
        this.fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);

        showDiff(currentCode);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Enable the action only when there's an active editor
        boolean enabled = e.getProject() != null &&
                e.getData(CommonDataKeys.EDITOR) != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    public void showDiff(String leftText) {
        try {
            String rightText = HttpUtil.get("http://172.16.1.11/api/app-center/api/pages/list/918");


            DiffDialogdddddddbak.showDiff(project,leftText,rightText);

            //DiffContent leftContent = createDiffContent(leftText, "Current");
            //DiffContent rightContent = createDiffContent(rightText, "Remote");
            //
            //SimpleDiffRequest request = new SimpleDiffRequest(
            //        "Code Comparison",
            //        leftContent, rightContent,
            //        "Your Code", "Remote Code"
            //);
            //
            //
            //DiffManager.getInstance().showDiff(project, request);
        } catch (Exception ex) {
            CodeDiffNotifications.showError(
                    project,
                    "Diff Error",
                    "Failed to show diff: " + ex.getMessage()
            );
        }
    }

    private DiffContent createDiffContent(@NotNull String text, @NotNull String title) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName("temp." + getFileExtension());
        Document document = EditorFactory.getInstance().createDocument(text);
        return DiffContentFactory.getInstance().create(project, document, fileType);
    }
    private String getFileExtension() {
        return fileType.getDefaultExtension().isEmpty() ? "txt" : fileType.getDefaultExtension();
    }
}