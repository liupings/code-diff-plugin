package com.rj.diff.current.utils;

import javax.swing.*;

public class LoadingDialog extends JDialog {
    public LoadingDialog(JFrame parent) {
        super(parent, false); // false 表示非模态
        setUndecorated(true);
        setSize(120, 120); // 根据 loading 动画调整大小
        setLocationRelativeTo(parent); // 初始位置居中
        setAlwaysOnTop(true);

        JLabel loadingLabel = new JLabel(new ImageIcon("loading.gif"));
        getContentPane().add(loadingLabel);
    }
}
