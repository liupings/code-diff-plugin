package com.rj.diff.mydialog;

import cn.hutool.core.swing.clipboard.ClipboardListener;
import cn.hutool.http.HttpUtil;
import com.intellij.diff.*;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

public class MyCodeCompareDialog extends DialogWrapper {
    private final RSyntaxTextArea leftTextArea;
    private final RSyntaxTextArea rightTextArea;
    private final JButton fetchButton;
    private final JButton compareButton;
    private final JButton applyAllButton;
    private JPanel mainPanel;
    private final Project project;
    private final JButton applySelectedButton;
    private final JButton saveButton;
    private Point lastScrollPosition;
    private final VirtualFile currentFile;
    private final JTextField urlTextField;
    private final JComboBox<String> languageComboBox;
    private final Path sourceFilePath;
    private final Highlighter.HighlightPainter addedPainter;
    private final Highlighter.HighlightPainter removedPainter;


    public MyCodeCompareDialog(@Nullable Project project, String sourceCode, Path sourceFilePath, VirtualFile currentFile) {
        super(project, true);
        // 设置对话框初始大小
        setSize(1600, 800);
        // 允许调整大小
        setResizable(true);
        this.sourceFilePath = sourceFilePath;
        setTitle("代码对比工具");
        setModal(true);
        this.project = project;
        this.currentFile = currentFile;
        // 初始化UI组件
        leftTextArea = createSyntaxTextArea();
        rightTextArea = createSyntaxTextArea();
        leftTextArea.setText(sourceCode);

        fetchButton = new JButton("获取远程代码");
        compareButton = new JButton("对比代码");
        applyAllButton = new JButton("全部应用");
        applySelectedButton = new JButton("应用选中");
        saveButton = new JButton("保存到源文件");
        urlTextField = new JTextField(30);
        //languageComboBox = new ComboBox<>(new String[]{"Java", "Kotlin", "Python", "JavaScript", "HTML", "XML", "SQL", "JSON"});
        languageComboBox = new ComboBox<>(new String[]{"Java"});

        // 设置高亮颜色
        addedPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(144, 238, 144, 100));
        removedPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 182, 193, 100));

        init();
        setupListeners();
        loadPreferences();
    }
    private void updateLeftTextArea(String newContent) {
        SwingUtilities.invokeLater(() -> {
            // 保存当前状态
            int caretPos = leftTextArea.getCaretPosition();
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
                    JScrollPane.class, leftTextArea);

            if (scrollPane != null) {
                lastScrollPosition = scrollPane.getViewport().getViewPosition();
            }

            // 更新内容
            leftTextArea.setText(newContent);

            // 恢复光标位置
            if (caretPos <= newContent.length()) {
                leftTextArea.setCaretPosition(caretPos);
            } else {
                leftTextArea.setCaretPosition(newContent.length());
            }

            // 恢复滚动位置
            if (scrollPane != null && lastScrollPosition != null) {
                scrollPane.getViewport().setViewPosition(lastScrollPosition);
            }
        });
    }

    @Override
    public void dispose() {
        // 移除剪贴板监听
        super.dispose();
    }
    private RSyntaxTextArea createSyntaxTextArea() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);

        // 使用 IDE 的字体设置
        Font editorFont = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
        textArea.setFont(editorFont);

        // 根据 IDE 主题选择合适的语法高亮主题
        try {
            boolean isDarkTheme = LafManager.getInstance().getCurrentUIThemeLookAndFeel().isDark();
            // 或者使用:
            // boolean isDarkTheme = "Darcula".equals(LafManager.getInstance().getCurrentTheme().getName());

            if (isDarkTheme) {
                Theme theme = Theme.load(getClass().getResourceAsStream(
                        "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                theme.apply(textArea);
            }
        } catch (IOException e) {
            // 使用默认主题
        }

        return textArea;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controlPanel.add(new JLabel("URL:"));
        controlPanel.add(urlTextField);
        controlPanel.add(fetchButton);
        controlPanel.add(new JLabel("语言:"));
        controlPanel.add(languageComboBox);
        controlPanel.add(compareButton);

        // 代码对比面板
        JPanel codePanel = new JPanel(new GridLayout(1, 2, 10, 10));
        //codePanel.add(createTextAreaPanel("当前代码", leftTextArea));
        //codePanel.add(createTextAreaPanel("远程代码", rightTextArea));

        RTextScrollPane currentCode = new RTextScrollPane(createTextAreaPanel("当前代码", leftTextArea));
        RTextScrollPane rightCode = new RTextScrollPane(createTextAreaPanel("远程代码", rightTextArea));
        codePanel.add(currentCode);
        codePanel.add(rightCode);

        JScrollBar leftScrollBar = currentCode.getVerticalScrollBar();
        JScrollBar rightScrollBar = rightCode.getVerticalScrollBar();

        // 设置滚动步长（值可以调整）
        int unitIncrement = 30;  // 单位增量，控制鼠标滚轮滚动速度
        int blockIncrement = 100; // 块增量，控制 PgUp/PgDn 速度

        leftScrollBar.setUnitIncrement(unitIncrement);
        rightScrollBar.setUnitIncrement(unitIncrement);
        leftScrollBar.setBlockIncrement(blockIncrement);
        rightScrollBar.setBlockIncrement(blockIncrement);

        // 添加同步监听
        leftScrollBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                rightScrollBar.setValue(e.getValue());
            }
        });

        rightScrollBar.addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                leftScrollBar.setValue(e.getValue());
            }
        });

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(codePanel, BorderLayout.CENTER);
        this.mainPanel = mainPanel;
        return mainPanel;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.add(applySelectedButton);
        buttonPanel.add(applyAllButton);
        buttonPanel.add(saveButton);

        // 添加默认的OK/Cancel按钮
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.EAST);
        southPanel.add(super.createSouthPanel(), BorderLayout.WEST);

        return southPanel;
    }

    private JPanel createTextAreaPanel(String title, RSyntaxTextArea textArea) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void setupListeners() {
        fetchButton.addActionListener(this::fetchRemoteCode);
        compareButton.addActionListener(this::compareCode);
        applyAllButton.addActionListener(this::applyAllChanges);
        applySelectedButton.addActionListener(this::applySelectedChanges);
        saveButton.addActionListener(this::saveToSourceFile);

        languageComboBox.addActionListener(e -> {
            String selectedLanguage = (String) languageComboBox.getSelectedItem();
            String syntaxStyle = getSyntaxStyleForLanguage(selectedLanguage);
            leftTextArea.setSyntaxEditingStyle(syntaxStyle);
            rightTextArea.setSyntaxEditingStyle(syntaxStyle);
            savePreferences();
        });

        // 自动格式化代码监听
        leftTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                autoFormatCode(leftTextArea);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                autoFormatCode(leftTextArea);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                autoFormatCode(leftTextArea);
            }
        });
    }

    private void autoFormatCode(RSyntaxTextArea textArea) {
        // 这里可以添加自动格式化逻辑
    }

    private String getSyntaxStyleForLanguage(String language) {
        if (language == null) return SyntaxConstants.SYNTAX_STYLE_NONE;

        return switch (language) {
            case "Java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            //case "Kotlin" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            //case "Python" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            //case "JavaScript" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            //case "HTML" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            //case "XML" -> SyntaxConstants.SYNTAX_STYLE_XML;
            //case "SQL" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            //case "JSON" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    private void fetchRemoteCode(ActionEvent e) {
        String url = urlTextField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(getWindow(), "请输入URL", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String remoteCode = HttpUtil.get(url);
                SwingUtilities.invokeLater(() -> {
                    rightTextArea.setText(remoteCode);
                    savePreferences();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(getWindow(),
                                "获取远程代码失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    //idea自带
    //private void compareCode(ActionEvent e) {
    //    String currentText = leftTextArea.getText();
    //    String remoteText = rightTextArea.getText();
    //
    //    if (remoteText == null || remoteText.isEmpty()) {
    //        JOptionPane.showMessageDialog(mainPanel, "请先获取远程代码", "错误", JOptionPane.ERROR_MESSAGE);
    //        return;
    //    }
    //
    //    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(currentFile);
    //    DiffContentFactory contentFactory = DiffContentFactory.getInstance();
    //
    //    // 创建 DiffContent 实例
    //    DiffContent currentContent = contentFactory.create(project, currentText, JavaFileType.INSTANCE);
    //    DiffContent remoteContent = contentFactory.create(project, remoteText, JavaFileType.INSTANCE);
    //
    //    // 创建 SimpleDiffRequest
    //    SimpleDiffRequest request = new SimpleDiffRequest(
    //            "代码对比",               // 对比窗口的标题
    //            currentContent,           // 左侧（当前代码）
    //            remoteContent,            // 右侧（远程代码）
    //            "当前代码",               // 左侧的标题
    //            "远程代码"                // 右侧的标题
    //    );
    //
    //    // 确保显示对比界面
    //    DiffManager.getInstance().showDiff(project, request);
    //}

    //算法对比
    private void compareCode(ActionEvent e) {
        String leftText = leftTextArea.getText();
        String rightText = rightTextArea.getText();

        if (leftText.isEmpty() || rightText.isEmpty()) {
            JOptionPane.showMessageDialog(getWindow(),
                    "两侧代码都不能为空", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new Thread(() -> {
            // 使用diff-match-patch计算差异
            diff_match_patch dmp = new diff_match_patch();
            LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(leftText, rightText);
            dmp.diff_cleanupSemantic(diffs);

            SwingUtilities.invokeLater(() -> {
                // 清除之前的高亮
                leftTextArea.getHighlighter().removeAllHighlights();
                rightTextArea.getHighlighter().removeAllHighlights();

                // 应用高亮显示差异
                highlightDifferences(diffs);
            });
        }).start();
    }
    private void highlightDifferences(List<diff_match_patch.Diff> diffs) {
        try {
            int leftPos = 0;
            int rightPos = 0;

            for (diff_match_patch.Diff diff : diffs) {
                String text = diff.text;
                int length = text.length();

                switch (diff.operation) {
                    case INSERT:
                        // 在右侧显示新增内容
                        rightTextArea.getHighlighter().addHighlight(
                                rightPos, rightPos + length, addedPainter);
                        rightPos += length;
                        break;
                    case DELETE:
                        // 在左侧显示删除内容
                        leftTextArea.getHighlighter().addHighlight(
                                leftPos, leftPos + length, removedPainter);
                        leftPos += length;
                        break;
                    case EQUAL:
                        leftPos += length;
                        rightPos += length;
                        break;
                }
            }
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void applyAllChanges(ActionEvent e) {
        leftTextArea.setText(rightTextArea.getText());
    }

    private void applySelectedChanges(ActionEvent e) {
        String selectedText = rightTextArea.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            JOptionPane.showMessageDialog(getWindow(),
                    "请先在右侧选择要应用的代码", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            int leftSelectionStart = leftTextArea.getSelectionStart();
            int leftSelectionEnd = leftTextArea.getSelectionEnd();

            if (leftSelectionStart == leftSelectionEnd) {
                // 如果没有选择左侧文本，则在光标处插入
                leftTextArea.insert(selectedText, leftTextArea.getCaretPosition());
            } else {
                // 替换选中的左侧文本
                leftTextArea.replaceSelection(selectedText);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(getWindow(),
                    "应用选中代码失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveToSourceFile(ActionEvent e) {
        if (sourceFilePath == null) {
            JOptionPane.showMessageDialog(getWindow(),
                    "未指定源文件路径", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // 保存文件内容到磁盘
            Files.writeString(sourceFilePath, leftTextArea.getText());

            // 获取对应的 VirtualFile
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourceFilePath.toString());
            if (virtualFile != null) {
                // 通知文件系统文件已更改
                virtualFile.refresh(false, false);

                // 通知编辑器重新加载文件
                FileDocumentManager.getInstance().reloadFiles(virtualFile);

                // 如果需要，可以刷新整个项目
                // Project project = getProject();
                // if (project != null) {
                //     RefreshQueue.getInstance().refresh(true, true, () -> {}, virtualFile);
                // }
            }

            JOptionPane.showMessageDialog(getWindow(),
                    "保存成功", "成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(getWindow(),
                    "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void savePreferences() {
        Preferences prefs = Preferences.userNodeForPackage(MyCodeCompareDialog.class);
        prefs.put("lastUrl", urlTextField.getText());
        prefs.put("lastLanguage", (String) languageComboBox.getSelectedItem());
    }

    private void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(MyCodeCompareDialog.class);
        urlTextField.setText(prefs.get("lastUrl", ""));
        String lastLanguage = prefs.get("lastLanguage", "Java");
        languageComboBox.setSelectedItem(lastLanguage);

        // 设置初始语法高亮
        String syntaxStyle = getSyntaxStyleForLanguage(lastLanguage);
        leftTextArea.setSyntaxEditingStyle(syntaxStyle);
        rightTextArea.setSyntaxEditingStyle(syntaxStyle);
    }

    @Override
    protected void doOKAction() {
        savePreferences();
        super.doOKAction();
    }
}