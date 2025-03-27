package com.rj.diff.dialog1;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.impl.DiffRequestPanelImpl;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class DiffDialogdddddddbak extends DialogWrapper {
    private final Project project;
    private final String leftText;
    private final String rightText;
    private DiffRequestPanelImpl diffPanel;

    public DiffDialogdddddddbak(@NotNull Project project, @NotNull String leftText, @NotNull String rightText) {
        super(project,true);
        this.project = project;
        this.leftText = leftText;
        this.rightText = rightText;
        setResizable(true);
        setTitle("com liuping");
        setModal(true);
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        // 创建 DiffContent
        DiffContent leftContent = DiffContentFactory.getInstance().create(project, leftText);
        DiffContent rightContent = DiffContentFactory.getInstance().create(project, rightText);

        // 创建 Diff 请求
        SimpleDiffRequest diffRequest = new SimpleDiffRequest("Code Comparison", leftContent, rightContent, "Current", "Remote");

        // 使用 DiffRequestPanelImpl 作为 Diff 组件
        diffPanel = new DiffRequestPanelImpl(project, null);
        diffPanel.setRequest(diffRequest);

        // 包装一个 JPanel 以适应 DialogWrapper
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(diffPanel.getComponent(), BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(800, 600));
        return panel;
    }
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 650); // 默认窗口大小
    }
    @Override
    protected void dispose() {
        super.dispose();
        if (diffPanel != null) {
            diffPanel.dispose();
        }
    }

    public static void showDiff(@NotNull Project project, @NotNull String leftText, @NotNull String rightText) {
        DiffDialogdddddddbak dialog = new DiffDialogdddddddbak(project, leftText, rightText);
        dialog.show();
    }
}
