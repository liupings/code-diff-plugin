package com.rj.diff.dialog1;

import cn.hutool.http.HttpUtil;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.rj.diff.CodeDiffNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class MyCodeDiffDialog extends DialogWrapper {
    private final Project project;
    private final String currentCode;
    private final FileType fileType;

    private EditorTextField leftEditor;  // 左侧编辑器（当前代码）
    private EditorTextField rightEditor; // 右侧编辑器（远程代码）
    private JButton fetchButton;        // 获取远程代码按钮
    private JButton replaceAllButton;   // 全部替换按钮
    private JButton replaceSelectedButton; // 选择替换按钮
    private JButton showDiffButton;     // 显示差异对比按钮

    public MyCodeDiffDialog(Project project, String currentCode, String fileName) {
        super(project);
        this.project = project;
        this.currentCode = currentCode;
        this.fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);

        setTitle("Code Comparison Tool");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));

        // Create editor panels
        leftEditor = createEditor(currentCode, "Current Code");
        rightEditor = createEditor("", "Remote Code (from URL)");

        // Create split pane
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                //wrapWithScrollPane(leftEditor, "Current Code"),
                //wrapWithScrollPane(rightEditor, "Remote Code")
                createScrollableEditorPanel(leftEditor, "Current Code"),
                createScrollableEditorPanel(rightEditor, "Remote Code")
        );
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        return mainPanel;
    }

    // 创建带滚动条的编辑器面板
    private JPanel createScrollableEditorPanel(EditorTextField editor, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        JBScrollPane scrollPane = new JBScrollPane(editor);
        scrollPane.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private EditorTextField createEditor(String text, String title) {
        EditorFactory editorFactory = EditorFactory.getInstance();
        Document document = editorFactory.createDocument(text);

        EditorTextField editorTextField = new EditorTextField(document, project, fileType, false, false) {
            @Override
            protected @NotNull EditorEx createEditor() {
                EditorEx editor = super.createEditor();
                setupEditor(editor);
                return editor;
            }
        };

        editorTextField.setOneLineMode(false);
        editorTextField.setPreferredSize(JBUI.size(500, 400));
        editorTextField.setBackground(JBColor.background());

        return editorTextField;
    }

    private void setupEditor(EditorEx editor) {
        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(true);
        settings.setWhitespacesShown(true);
        settings.setLineMarkerAreaShown(true);
        settings.setIndentGuidesShown(true);

        EditorColorsScheme scheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
        editor.setColorsScheme(scheme);
    }

    private JScrollPane wrapWithScrollPane(JComponent component, String title) {
        JBScrollPane scrollPane = new JBScrollPane(component);
        scrollPane.setBorder(BorderFactory.createTitledBorder(title));
        return scrollPane;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        fetchButton = new JButton("Fetch Remote Code");
        replaceAllButton = new JButton("Replace All");
        replaceSelectedButton = new JButton("Replace Selected");
        showDiffButton = new JButton("Shows comparison differences");

        fetchButton.addActionListener(this::fetchRemoteCode);
        replaceAllButton.addActionListener(this::replaceAll);
        replaceSelectedButton.addActionListener(this::replaceSelected);
        showDiffButton.addActionListener(this::showDiff);

        replaceAllButton.setEnabled(false);
        replaceSelectedButton.setEnabled(false);
        showDiffButton.setEnabled(false);


        buttonPanel.add(fetchButton);
        buttonPanel.add(replaceAllButton);
        buttonPanel.add(replaceSelectedButton);
        buttonPanel.add(showDiffButton);


        return buttonPanel;
    }

    // 显示专业的差异对比界面
    private void showDiff(ActionEvent e) {
        try {
            DiffContent leftContent = createDiffContent(leftEditor.getText(), "Current");
            DiffContent rightContent = createDiffContent(rightEditor.getText(), "Remote");

            SimpleDiffRequest request = new SimpleDiffRequest(
                    "Code Comparison",
                    leftContent, rightContent,
                    "Your Code", "Remote Code"
            );

            DiffManager.getInstance().showDiff(project, request);
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
        // 根据你的实际需求返回文件扩展名
        // 例如从当前文件名提取，或使用默认值
        return fileType.getDefaultExtension().isEmpty() ?
                "txt" : fileType.getDefaultExtension();
    }
    private void fetchRemoteCode(ActionEvent e) {
        String url = Messages.showInputDialog(
                project,
                "Enter URL to fetch code from:",
                "Fetch Remote Code",
                Messages.getQuestionIcon(),
                "http://172.16.1.11/api/app-center/api/pages/list/918",
                null
        );

        if (url != null && !url.trim().isEmpty()) {
            try {
                String remoteCode = fetchCodeFromUrl(url);
                rightEditor.setText(remoteCode);
                replaceAllButton.setEnabled(true);
                replaceSelectedButton.setEnabled(true);
                showDiffButton.setEnabled(true);
            } catch (IOException ex) {
                CodeDiffNotifications.showError(
                        project,
                        "Fetch Error",
                        "Failed to fetch code: " + ex.getMessage()
                );
            }
        }
    }

    private String fetchCodeFromUrl(String urlString) throws IOException {
        //StringBuilder content = new StringBuilder();
        //URL url = new URL(urlString);
        String s = HttpUtil.get(urlString);
        return s;
        //
        //try (BufferedReader reader = new BufferedReader(
        //        new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
        //    String line;
        //    while ((line = reader.readLine()) != null) {
        //        content.append(line).append("\n");
        //    }
        //}
        //
        //return content.toString();
    }

    private void replaceAll(ActionEvent e) {
        leftEditor.setText(rightEditor.getText());
        close(DialogWrapper.OK_EXIT_CODE);
    }

    private void replaceSelected(ActionEvent e) {
        Editor leftEdit = leftEditor.getEditor();
        Editor rightEdit = rightEditor.getEditor();

        if (leftEdit == null || rightEdit == null) {
            return;
        }

        String selectedText = rightEdit.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            CodeDiffNotifications.showWarning(
                    project,
                    "No Selection",
                    "No text selected in the remote code"
            );
            return;
        }

        int leftSelectionStart = leftEdit.getSelectionModel().getSelectionStart();
        int leftSelectionEnd = leftEdit.getSelectionModel().getSelectionEnd();

        if (leftSelectionStart == leftSelectionEnd) {
            // No selection in left pane, insert at caret position
            int caretPos = leftEdit.getCaretModel().getOffset();
            leftEdit.getDocument().insertString(caretPos, selectedText);
        } else {
            // Replace selection in left pane
            leftEdit.getDocument().replaceString(
                    leftSelectionStart,
                    leftSelectionEnd,
                    selectedText
            );
        }
    }

    public String getModifiedCode() {
        return leftEditor.getText();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
    }
}