package com.rj.diff.dialog2;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CodeCompareDialog extends DialogWrapper {
    private final Project project;
    private final VirtualFile currentFile;
    private final Editor hostEditor;
    private EditorTextField currentCodeEditor;
    private EditorTextField remoteCodeEditor;
    private JPanel mainPanel;
    private JButton fetchButton;
    private JButton compareButton;
    private JButton replaceAllButton;
    private JButton replaceSelectedButton;
    private JTextField urlTextField;
    private String lastSelectedText = "";

    public CodeCompareDialog(@NotNull Project project, @NotNull VirtualFile currentFile, Editor hostEditor) {
        super(project);
        this.project = project;
        this.currentFile = currentFile;
        this.hostEditor = hostEditor;
        setTitle("代码对比工具");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(JBUI.size(800, 600));

        // 创建顶部控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        urlTextField = new JTextField(30);
        fetchButton = new JButton("获取远程代码");
        compareButton = new JButton("对比代码");
        replaceAllButton = new JButton("全部替换");
        replaceSelectedButton = new JButton("部分替换");

        urlTextField.setText("http://172.16.1.11/api/app-center/api/pages/list/918");

        controlPanel.add(new JLabel("URL:"));
        controlPanel.add(urlTextField);
        controlPanel.add(fetchButton);
        controlPanel.add(compareButton);
        controlPanel.add(replaceAllButton);
        controlPanel.add(replaceSelectedButton);

        // 创建代码编辑器面板
        JPanel editorPanel = new JPanel(new GridLayout(1, 2));
        currentCodeEditor = createEditor(FileDocumentManager.getInstance().getDocument(currentFile));
        remoteCodeEditor = createEditor(null);

        editorPanel.add(createEditorPanel("当前代码", currentCodeEditor));
        editorPanel.add(createEditorPanel("远程代码", remoteCodeEditor));

        // 添加事件监听
        setupListeners();
        
        // 添加选择监听器
        setupSelectionListener();

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(editorPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private void setupSelectionListener() {
        Editor remoteEditor = remoteCodeEditor.getEditor();
        if (remoteEditor != null) {
            remoteEditor.getSelectionModel().addSelectionListener(new SelectionListener() {
                @Override
                public void selectionChanged(@NotNull SelectionEvent e) {
                    lastSelectedText = remoteEditor.getSelectionModel().getSelectedText();
                }
            });
        }
    }

    private JPanel createEditorPanel(String title, EditorTextField editor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(editor, BorderLayout.CENTER);
        return panel;
    }

    private EditorTextField createEditor(Document document) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(currentFile);
        EditorTextField editor = new EditorTextField(
                document != null ? document : EditorFactory.getInstance().createDocument(""),
                project,
                fileType,
                false,
                false
        );
        editor.setPreferredSize(JBUI.size(400, 500));
        editor.setOneLineMode(false);
        return editor;
    }

    private void setupListeners() {
        fetchButton.addActionListener(e -> fetchRemoteCode());
        compareButton.addActionListener(e -> showDiff());
        replaceAllButton.addActionListener(e -> replaceAll());
        replaceSelectedButton.addActionListener(e -> replaceSelected());
    }

    private void fetchRemoteCode() {
        String url = urlTextField.getText();
        if (url == null || url.trim().isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "请输入有效的URL", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String remoteContent = cn.hutool.http.HttpUtil.get(url);
            remoteCodeEditor.setText(remoteContent);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel, "获取远程代码失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDiff() {
        String currentText = currentCodeEditor.getText();
        String remoteText = remoteCodeEditor.getText();

        if (remoteText == null || remoteText.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "请先获取远程代码", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(currentFile);
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();

        DiffContent currentContent = contentFactory.create(project, currentText, fileType);
        DiffContent remoteContent = contentFactory.create(project, remoteText, fileType);

        SimpleDiffRequest request = new SimpleDiffRequest(
                "代码对比",
                currentContent,
                remoteContent,
                "当前代码",
                "远程代码"
        );

        DiffManager.getInstance().showDiff(project, request);
    }

    private void replaceAll() {
        String remoteText = remoteCodeEditor.getText();
        if (remoteText == null || remoteText.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "没有可替换的内容", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                mainPanel,
                "确定要用远程代码完全替换当前代码吗?",
                "确认替换",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            currentCodeEditor.setText(remoteText);
        }
    }

    private void replaceSelected() {
        if (lastSelectedText == null || lastSelectedText.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "请先在远程代码中选择要替换的内容", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(
                mainPanel,
                "确定要用选中的远程代码替换当前代码中的相应部分吗?",
                "确认部分替换",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            String currentText = currentCodeEditor.getText();
            // 在实际应用中，这里需要更智能的替换逻辑
            // 这里简单演示替换第一个匹配项
            String newText = currentText.replaceFirst(lastSelectedText, lastSelectedText);
            currentCodeEditor.setText(newText);
        }
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{
                new DialogWrapperExitAction("保存并关闭", OK_EXIT_CODE) {
                    @Override
                    protected void doAction(ActionEvent e) {
                        saveChanges();
                        super.doAction(e);
                    }
                },
                getCancelAction()
        };
    }

    private void saveChanges() {
        try {
            String newContent = currentCodeEditor.getText();
            Path filePath = Path.of(currentFile.getPath());
            Files.writeString(filePath, newContent, StandardCharsets.UTF_8);
            
            // 如果是从编辑器打开的，刷新编辑器内容
            if (hostEditor != null) {
                hostEditor.getDocument().setText(newContent);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(mainPanel, "保存文件失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

}