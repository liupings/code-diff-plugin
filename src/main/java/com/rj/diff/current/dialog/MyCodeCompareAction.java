package com.rj.diff.current.dialog;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class MyCodeCompareAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (editor == null || project == null) {
            return;
        }

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile == null) {
            return;
        }

        String content = editor.getDocument().getText();
        Path filePath = Path.of(virtualFile.getPath());

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

        // 在 IDE 窗口中央显示对话框
        MyCodeCompareDialog dialog = new MyCodeCompareDialog(project, content, filePath, file);
        dialog.show();

        // 如果点击了确定，刷新文件
        if (dialog.isOK()) {
            FileDocumentManager.getInstance().reloadFiles(virtualFile);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只在有编辑器和项目时启用
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(project != null && editor != null);
    }
}