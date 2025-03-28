package com.rj.diff.current.dialog;

import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.rj.diff.CodeDiffNotifications;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private boolean isAdjusting = false;


    public MyCodeCompareDialog(@Nullable Project project, String sourceCode, Path sourceFilePath, VirtualFile currentFile) {
        super(project, true);
        // 设置对话框初始大小
        setSize(1500, 800);
        // 允许调整大小
        setResizable(true);
        this.sourceFilePath = sourceFilePath;
        setTitle("代码对比");
        setModal(true);
        this.project = project;
        this.currentFile = currentFile;
        // 初始化UI组件
        leftTextArea = createSyntaxTextArea();
        rightTextArea = createSyntaxTextArea();
        leftTextArea.setText(sourceCode);

        fetchButton = new JButton("获取快速开发平台代码");
        compareButton = new JButton("对比代码");
        applyAllButton = new JButton("全部应用");
        applySelectedButton = new JButton("应用选中");
        saveButton = new JButton("保存到源文件");
        urlTextField = new JTextField(30);
        //languageComboBox = new ComboBox<>(new String[]{"Java", "Kotlin", "Python", "JavaScript", "HTML", "XML", "SQL", "JSON"});
        languageComboBox = new ComboBox<>(new String[]{"Java"});
        languageComboBox.setVisible(Boolean.FALSE);

        // 设置高亮颜色
        addedPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(118, 206, 118, 100));
        removedPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(191, 185, 186, 100));

        init();
        addTextChangeListener(leftTextArea);
        addTextChangeListener(rightTextArea);
        setupListeners();
        loadPreferences();

        urlTextField.setText("http://172.16.1.11/api/table-info/api/getSqlByTable/59");
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
        super.dispose();
    }

    private RSyntaxTextArea createSyntaxTextArea() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);

        // 启用边界线显示
        //textArea.setMarginLineEnabled(true);
        // 设置边界线的位置，通常是 80 或者 100 字符处
        //textArea.setMarginLinePosition(80);
        // 启用清除空白行中的空白字符
        textArea.setClearWhitespaceLinesEnabled(true);
        // 使用 IDE 的字体设置
        Font editorFont = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
        textArea.setFont(editorFont);
        // 根据 IDE 主题选择合适的语法高亮主题
        try {
            boolean isDarkTheme = LafManager.getInstance().getCurrentUIThemeLookAndFeel().isDark();
            // 或者使用:
            // boolean isDarkTheme = "Darcula".equals(LafManager.getInstance().getCurrentTheme().getName());

            if (isDarkTheme) {
                Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                theme.apply(textArea);
            }
        } catch (IOException e) {
            // 使用默认主题
        }

        return textArea;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder());

        // 顶部控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("URL:"));
        controlPanel.add(urlTextField);
        controlPanel.add(fetchButton);
        //controlPanel.add(new JLabel("语言:"));
        controlPanel.add(languageComboBox);
        controlPanel.add(compareButton);

        // 代码对比面板
        JPanel codePanel = new JPanel(new GridLayout());
        //codePanel.add(createTextAreaPanel("当前代码", leftTextArea));
        //codePanel.add(createTextAreaPanel("远程代码", rightTextArea));

        RTextScrollPane currentCode = new RTextScrollPane(createTextAreaPanel("当前代码", leftTextArea));
        RTextScrollPane rightCode = new RTextScrollPane(createTextAreaPanel("快速开发平台代码", rightTextArea));
        codePanel.add(currentCode);
        codePanel.add(rightCode);

        JScrollBar leftScrollBar = currentCode.getVerticalScrollBar();
        JScrollBar rightScrollBar = rightCode.getVerticalScrollBar();

        JScrollBar horizontalScrollBar = currentCode.getHorizontalScrollBar();
        JScrollBar horizontalScrollBar1 = rightCode.getHorizontalScrollBar();
        horizontalScrollBar.setUnitIncrement(30);
        horizontalScrollBar1.setBlockIncrement(30);

        // 设置滚动步长（值可以调整）
        int unitIncrement = 30;  // 单位增量，控制鼠标滚轮滚动速度
        int blockIncrement = 100; // 块增量，控制 PgUp/PgDn 速度

        leftScrollBar.setUnitIncrement(unitIncrement);
        rightScrollBar.setUnitIncrement(unitIncrement);
        leftScrollBar.setBlockIncrement(blockIncrement);
        rightScrollBar.setBlockIncrement(blockIncrement);

        // 添加同步监听  防止死循环
        leftScrollBar.addAdjustmentListener(e -> {
            if (!isAdjusting && !e.getValueIsAdjusting()) {
                isAdjusting = true;
                rightScrollBar.setValue(e.getValue());
                isAdjusting = false;
            }
        });

        rightScrollBar.addAdjustmentListener(e -> {
            if (!isAdjusting && !e.getValueIsAdjusting()) {
                isAdjusting = true;
                leftScrollBar.setValue(e.getValue());
                isAdjusting = false;
            }
        });

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(codePanel, BorderLayout.CENTER);
        this.mainPanel = mainPanel;
        return mainPanel;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(applySelectedButton);
        buttonPanel.add(applyAllButton);
        buttonPanel.add(saveButton);

        // 添加默认的OK/Cancel按钮
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.CENTER);
        //southPanel.add(super.createSouthPanel(), BorderLayout.WEST);

        return southPanel;
    }

    private JPanel createTextAreaPanel(String title, RSyntaxTextArea textArea) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
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

    //自动格式化 Java 代码
    private void autoFormatCode(RSyntaxTextArea textArea) {
 //       try {
 //String formattedCode = new Formatter().formatSource(textArea.getText());
 //           textArea.setText(formattedCode);
 //       } catch (FormatterException e) {
 //           CodeDiffNotifications.showError(project, "错误", "格式化失败");
 //
 //       }
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
            //JOptionPane.showMessageDialog(getWindow(), "请输入URL", "错误", JOptionPane.ERROR_MESSAGE);
            CodeDiffNotifications.showError(project, "错误", "请输入URL");

            return;
        }

        //fetchButton.setEnabled(false);
        CompletableFuture.supplyAsync(() -> HttpUtil.get(url))
                .thenAccept(remoteCode -> SwingUtilities.invokeLater(() -> {
                    rightTextArea.setText(remoteCode);
                    compareCode(null);
                    fetchButton.setEnabled(true);
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> CodeDiffNotifications.showError(project, "错误", "获取远程代码失败: " + ex.getMessage()));
                    fetchButton.setEnabled(true);
                    return null;
                });
        //ApplicationManager.getApplication().executeOnPooledThread(() -> {
        //    try {
        //        String remoteCode = HttpUtil.get(url);
        //        SwingUtilities.invokeLater(() -> {
        //            rightTextArea.setText(remoteCode);
        //            savePreferences();
        //            // 自动触发代码对比
        //            compareCode(null);
        //        });
        //    } catch (Exception ex) {
        //        SwingUtilities.invokeLater(() ->
        //                //JOptionPane.showMessageDialog(getWindow(), "获取远程代码失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE)
        //                CodeDiffNotifications.showError(project, "错误", "获取远程代码失败")
        //
        //        );
        //
        //    }
        //});
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
            //JOptionPane.showMessageDialog(getWindow(),
            //        "两侧代码都不能为空", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        CompletableFuture.runAsync(() -> {
            // 使用diff-match-patch计算差异
            diff_match_patch dmp = new diff_match_patch();
            LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(leftText, rightText);
            dmp.Diff_Timeout = 1.0f;
            dmp.diff_cleanupSemantic(diffs);

            SwingUtilities.invokeLater(() -> {
                // 清除之前的高亮
                leftTextArea.getHighlighter().removeAllHighlights();
                rightTextArea.getHighlighter().removeAllHighlights();

                // 应用高亮显示差异
                highlightDifferences(diffs);
            });
        });
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
        if (StrUtil.isBlank(rightTextArea.getText())) {
            CodeDiffNotifications.showError(project, "错误", "没有获取到远程内容");
            return;
        }
        leftTextArea.setText(rightTextArea.getText());
    }

    private void applySelectedChanges(ActionEvent e) {
        String selectedText = rightTextArea.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) {
            //JOptionPane.showMessageDialog(getWindow(), "请先在右侧选择要应用的代码", "提示", JOptionPane.INFORMATION_MESSAGE);
            CodeDiffNotifications.showWarning(project, "警告", "请先在右侧选择要应用的代码");
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
            //JOptionPane.showMessageDialog(getWindow(), "应用选中代码失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            CodeDiffNotifications.showWarning(project, "警告", "应用选中代码失败");

        }
    }

    private void saveToSourceFile(ActionEvent e) {
        if (sourceFilePath == null) {
            //JOptionPane.showMessageDialog(getWindow(), "未指定源文件路径", "错误", JOptionPane.ERROR_MESSAGE);
            CodeDiffNotifications.showWarning(project, "警告", "未指定源文件路径");
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

            //JOptionPane.showMessageDialog(getWindow(), "保存成功", "成功", JOptionPane.INFORMATION_MESSAGE);
            CodeDiffNotifications.showInfo(project, "提示", "保存成功");

            dispose();
        } catch (IOException ex) {
            //JOptionPane.showMessageDialog(getWindow(), "保存失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            CodeDiffNotifications.showError(project, "错误", "保存失败");

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

    // 文本变化监听器
    private void addTextChangeListener(JTextArea textArea) {
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                compareCode(null);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                compareCode(null);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                compareCode(null);
            }
        });
    }

    @Override
    protected void doOKAction() {
        savePreferences();
        super.doOKAction();
    }
}