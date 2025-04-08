package com.rj.diff.current;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
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
import com.rj.diff.current.utils.CodeElementDiffer;
import com.rj.diff.current.utils.ThemeUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * 代码对比对话框，用于比较本地代码与远程获取的代码差异
 * 提供代码对比、差异高亮、代码应用和保存功能
 */
public class MyCodeCompareDialog extends DialogWrapper {
    // UI组件
    private final RSyntaxTextArea leftTextArea;  // 左侧文本区域(本地代码)
    private final RSyntaxTextArea rightTextArea; // 右侧文本区域(远程代码)
    private final JButton fetchButton;           // 获取远程代码按钮
    //private final JButton compareButton;         // 对比代码按钮
    private final JButton applyButton;           // 应用所有更改按钮
    private final JButton saveButton;            // 保存按钮
    private final JTextField urlTextField;       // URL输入框
    private final JComboBox<String> languageComboBox; // 语言选择框

    // 高亮相关
    private final Highlighter.HighlightPainter addedPainter;   // 新增代码高亮
    private final Highlighter.HighlightPainter removedPainter; // 删除代码高亮

    // 状态管理
    private boolean isAdjusting = false;         // 滚动同步状态标志
    private Point lastScrollPosition;            // 最后滚动位置
    private JDialog loadingDialog;               // 加载对话框

    // 项目相关
    private final Project project;               // 当前项目
    private final VirtualFile currentFile;       // 当前文件
    private final Path sourceFilePath;           // 源文件路径

    /**
     * 构造函数
     *
     * @param project        当前项目
     * @param sourceCode     源代码内容
     * @param sourceFilePath 源文件路径
     * @param currentFile    当前文件
     */
    public MyCodeCompareDialog(@Nullable Project project, String sourceCode, Path sourceFilePath, VirtualFile currentFile) {
        super(project, true);
        this.project = project;
        this.sourceFilePath = sourceFilePath;
        this.currentFile = currentFile;

        // 初始化UI设置
        setSize(1500, 800);
        setResizable(true);
        setTitle("代码对比");
        setModal(true);

        // 初始化UI组件
        leftTextArea = createSyntaxTextArea();
        rightTextArea = createSyntaxTextArea();
        leftTextArea.setText(sourceCode);

        fetchButton = new JButton("获取快速开发平台代码");
        //compareButton = new JButton("对比代码");
        applyButton = new JButton("应用");
        saveButton = new JButton("保存");
        urlTextField = new JTextField(30);
        languageComboBox = new ComboBox<>(new String[]{"Java"});
        languageComboBox.setVisible(Boolean.FALSE);

        // 初始化高亮颜色
        addedPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(100, 255, 100, 20));
        removedPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(243, 243, 24, 100));

        // 初始化对话框
        init();
        addTextChangeListener(leftTextArea);
        addTextChangeListener(rightTextArea);
        setupListeners();
        loadPreferences();
        setupLoadingDialog();

        // 设置默认URL
        urlTextField.setText("http://172.16.1.14:9000/api/interface-definition/api/generator/javaBasedByClassName/" + currentFile.getName());
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    /**
     * 创建语法高亮文本区域
     *
     * @return 配置好的RSyntaxTextArea实例
     */
    private RSyntaxTextArea createSyntaxTextArea() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setClearWhitespaceLinesEnabled(true);

        // 使用IDE的字体设置
        Font editorFont = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
        textArea.setFont(editorFont);

        // 根据IDE主题设置语法高亮主题
        applyThemeBasedOnIDESettings(textArea);

        return textArea;
    }

    /**
     * 根据IDE主题设置应用相应的语法高亮主题
     *
     * @param textArea 文本区域
     */
    private void applyThemeBasedOnIDESettings(RSyntaxTextArea textArea) {
        try {
            //boolean isDarkTheme = LafManager.getInstance().getCurrentUIThemeLookAndFeel().isDark();

            if (ThemeUtils.isDarkTheme()) {
                Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
                theme.apply(textArea);
                textArea.setBackground(new Color(43, 43, 43));
                textArea.setSelectionColor(new Color(59, 117, 231));
                textArea.setMargin(new Insets(0, 3, 0, 0));
                textArea.setCaretColor(Color.WHITE);
            }
        } catch (IOException e) {
            // 使用默认主题
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder());

        // 顶部控制面板
        JPanel controlPanel = createControlPanel();

        // 代码对比面板
        JPanel codePanel = createCodeComparePanel();

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(codePanel, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * 创建控制面板
     *
     * @return 配置好的控制面板
     */
    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("URL:"));
        controlPanel.add(urlTextField);
        controlPanel.add(fetchButton);
        controlPanel.add(languageComboBox);
        //controlPanel.add(compareButton);
        return controlPanel;
    }

    /**
     * 创建代码对比面板
     *
     * @return 配置好的代码对比面板
     */
    private JPanel createCodeComparePanel() {
        JPanel codePanel = new JPanel(new GridLayout());

        RTextScrollPane currentCode = new RTextScrollPane(createTextAreaPanel("当前代码", leftTextArea));
        RTextScrollPane rightCode = new RTextScrollPane(createTextAreaPanel("快速开发平台代码", rightTextArea));

        codePanel.add(currentCode);
        codePanel.add(rightCode);

        setupScrollSync(currentCode, rightCode);

        return codePanel;
    }

    /**
     * 设置滚动同步
     *
     * @param leftScrollPane  左侧滚动面板
     * @param rightScrollPane 右侧滚动面板
     */
    private void setupScrollSync(RTextScrollPane leftScrollPane, RTextScrollPane rightScrollPane) {
        JScrollBar leftScrollBar = leftScrollPane.getVerticalScrollBar();
        JScrollBar rightScrollBar = rightScrollPane.getVerticalScrollBar();

        JScrollBar leftHScrollBar = leftScrollPane.getHorizontalScrollBar();
        JScrollBar rightHScrollBar = rightScrollPane.getHorizontalScrollBar();

        // 设置滚动步长
        int unitIncrement = 30;
        int blockIncrement = 100;

        leftScrollBar.setUnitIncrement(unitIncrement);
        rightScrollBar.setUnitIncrement(unitIncrement);
        leftScrollBar.setBlockIncrement(blockIncrement);
        rightScrollBar.setBlockIncrement(blockIncrement);

        leftHScrollBar.setUnitIncrement(30);
        rightHScrollBar.setBlockIncrement(30);

        // 添加同步监听
        leftScrollBar.addAdjustmentListener(e -> syncScrollBars(e, rightScrollBar));
        rightScrollBar.addAdjustmentListener(e -> syncScrollBars(e, leftScrollBar));
    }

    /**
     * 同步滚动条
     *
     * @param event           调整事件
     * @param targetScrollBar 目标滚动条
     */
    private void syncScrollBars(AdjustmentEvent event, JScrollBar targetScrollBar) {
        if (!isAdjusting && !event.getValueIsAdjusting()) {
            isAdjusting = true;
            targetScrollBar.setValue(event.getValue());
            isAdjusting = false;
        }
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(applyButton);
        buttonPanel.add(saveButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(buttonPanel, BorderLayout.CENTER);

        return southPanel;
    }

    /**
     * 创建文本区域面板
     *
     * @param title    面板标题
     * @param textArea 文本区域
     * @return 配置好的面板
     */
    private JPanel createTextAreaPanel(String title, RSyntaxTextArea textArea) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), title));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 设置事件监听器
     */
    private void setupListeners() {
        fetchButton.addActionListener(this::fetchRemoteCode);
        //compareButton.addActionListener(this::compareCode);
        applyButton.addActionListener(this::applyAllChanges);
        saveButton.addActionListener(this::saveToSourceFile);

        languageComboBox.addActionListener(e -> {
            String selectedLanguage = (String) languageComboBox.getSelectedItem();
            String syntaxStyle = getSyntaxStyleForLanguage(selectedLanguage);
            leftTextArea.setSyntaxEditingStyle(syntaxStyle);
            rightTextArea.setSyntaxEditingStyle(syntaxStyle);
            savePreferences();
        });
    }

    /**
     * 根据语言获取语法高亮样式
     *
     * @param language 编程语言
     * @return 语法高亮样式常量
     */
    private String getSyntaxStyleForLanguage(String language) {
        if (language == null) return SyntaxConstants.SYNTAX_STYLE_NONE;

        return switch (language) {
            case "Java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            //case "JavaScript" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            //case "HTML" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    /**
     * 设置加载对话框
     */
    private void setupLoadingDialog() {
        loadingDialog = new JDialog();
        loadingDialog.setUndecorated(true);
        loadingDialog.setModal(true);
        loadingDialog.setSize(150, 90);  // 稍微加大尺寸
        loadingDialog.setLocationRelativeTo(null);
        loadingDialog.setLayout(new BorderLayout());
        // 设置对话框背景完全透明
        loadingDialog.setBackground(new Color(0, 0, 0, 0));

        // 主面板 - 添加圆角和阴影效果
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();

                // 设置透明度 (0.7f表示70%不透明)
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

                // 抗锯齿
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 绘制圆角背景
                g2d.setColor(new Color(122, 138, 153));  // 浅灰色背景
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                // 绘制边框
                g2d.setColor(new Color(122, 138, 153));  // 浅灰色边框
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

                g2d.dispose();
            }
        };
        mainPanel.setBorder(BorderFactory.createEmptyBorder());
        mainPanel.setOpaque(false);

        // 加载图标 (可以使用自定义图标或系统图标)
        JLabel loadingIcon = new JLabel(new ImageIcon(getClass().getResource("/icons/loading.gif")));
        loadingIcon.setHorizontalAlignment(JLabel.CENTER);

        // 如果没有GIF资源，可以使用旋转的JLabel替代
        if (loadingIcon.getIcon() == null) {
            loadingIcon = new JLabel("⏳");  // 沙漏emoji作为后备
            loadingIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        }

        // 加载文本
        JLabel loadingLabel = new JLabel("数据加载中...", JLabel.CENTER);
        loadingLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        loadingLabel.setForeground(new Color(169, 183, 193));  // 深灰色文字

        // 布局组件
        JPanel contentPanel = new JPanel(new BorderLayout(0, 15));
        contentPanel.setOpaque(false);

        contentPanel.add(loadingIcon, BorderLayout.NORTH);
        contentPanel.add(loadingLabel, BorderLayout.CENTER);

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        loadingDialog.add(mainPanel);

        // 添加轻微阴影效果
        loadingDialog.setBackground(new Color(0, 0, 0, 0));  // 透明背景
        loadingDialog.setOpacity(0.95f);  // 轻微透明
    }

    /**
     * 获取远程代码
     *
     * @param e 动作事件
     */
    private void fetchRemoteCode(ActionEvent e) {
        String url = urlTextField.getText().trim();
        if (url.isEmpty()) {
            CodeDiffNotifications.showError(project, "错误", "请输入URL");
            return;
        }

        showLoadingDialog();
        fetchButton.setEnabled(false);

        CompletableFuture.supplyAsync(() -> HttpUtil.get(url))
                .thenAccept(remoteCode -> SwingUtilities.invokeLater(() -> {
                    rightTextArea.setText(remoteCode);
                    compareCode(null);
                    hideLoadingDialog();
                    fetchButton.setEnabled(true);
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        CodeDiffNotifications.showError(project, "错误", "获取远程代码失败: " + ex.getMessage());
                        hideLoadingDialog();
                        fetchButton.setEnabled(true);
                    });
                    return null;
                });
    }

    /**
     * 显示加载对话框
     */
    private void showLoadingDialog() {
        SwingUtilities.invokeLater(() -> loadingDialog.setVisible(true));
    }

    /**
     * 隐藏加载对话框
     */
    private void hideLoadingDialog() {
        SwingUtilities.invokeLater(() -> loadingDialog.setVisible(false));
    }

    /**
     * 对比代码
     *
     * @param e 动作事件
     */
    private void compareCode(ActionEvent e) {
        String leftText = leftTextArea.getText();
        String rightText = rightTextArea.getText();

        if (leftText.isEmpty() || rightText.isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            CodeElementDiffer differ = new CodeElementDiffer(rightTextArea, addedPainter);
            differ.highlightDifferences(leftText, rightText);
        });
    }

    /**
     * 应用所有更改
     *
     * @param e 动作事件
     */
    private void applyAllChanges(ActionEvent e) {
        if (StrUtil.isBlank(rightTextArea.getText())) {
            CodeDiffNotifications.showError(project, "错误", "没有获取到远程内容");
            return;
        }

        String resultJava = AstDiffUpdater.updateControllerWithDifferences(project, rightTextArea.getText(), leftTextArea.getText());
        leftTextArea.setText(resultJava);
        compareCode(null);
    }

    /**
     * 保存到源文件
     *
     * @param e 动作事件
     */
    private void saveToSourceFile(ActionEvent e) {
        if (sourceFilePath == null) {
            CodeDiffNotifications.showWarning(project, "警告", "未指定源文件路径");
            return;
        }

        try {
            Files.writeString(sourceFilePath, leftTextArea.getText());
            refreshFileSystem();
            CodeDiffNotifications.showInfo(project, "提示", "保存成功");
            dispose();
        } catch (IOException ex) {
            CodeDiffNotifications.showError(project, "错误", "保存失败");
        }
    }

    /**
     * 刷新文件系统
     */
    private void refreshFileSystem() throws IOException {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourceFilePath.toString());
        if (virtualFile != null) {
            virtualFile.refresh(false, false);
            FileDocumentManager.getInstance().reloadFiles(virtualFile);
        }
    }

    /**
     * 保存偏好设置
     */
    private void savePreferences() {
        Preferences prefs = Preferences.userNodeForPackage(MyCodeCompareDialog.class);
        prefs.put("lastUrl", urlTextField.getText());
        prefs.put("lastLanguage", (String) languageComboBox.getSelectedItem());
    }

    /**
     * 加载偏好设置
     */
    private void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(MyCodeCompareDialog.class);
        urlTextField.setText(prefs.get("lastUrl", ""));
        String lastLanguage = prefs.get("lastLanguage", "Java");
        languageComboBox.setSelectedItem(lastLanguage);

        String syntaxStyle = getSyntaxStyleForLanguage(lastLanguage);
        leftTextArea.setSyntaxEditingStyle(syntaxStyle);
        rightTextArea.setSyntaxEditingStyle(syntaxStyle);
    }

    /**
     * 添加文本变化监听器
     *
     * @param textArea 文本区域
     */
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