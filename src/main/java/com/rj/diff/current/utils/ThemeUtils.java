package com.rj.diff.current.utils;

import com.intellij.ide.ui.LafManager;

public class ThemeUtils {
    public static boolean isDarkTheme() {
        try {
            // 优先尝试 2023.3+ 新方法
            Object lafManager = LafManager.getInstance();
            Object themeLookAndFeel = lafManager.getClass()
                    .getMethod("getCurrentUIThemeLookAndFeel")
                    .invoke(lafManager);
            if (themeLookAndFeel != null) {
                return (Boolean) themeLookAndFeel.getClass()
                        .getMethod("isDark")
                        .invoke(themeLookAndFeel);
            }
        } catch (Exception ignored) {
            // 低版本 fallback（仅在低版本中会走到这里）
            try {
                Object laf = LafManager.getInstance()
                        .getClass()
                        .getMethod("getCurrentLookAndFeel")
                        .invoke(LafManager.getInstance());
                if (laf != null) {
                    String name = (String) laf.getClass().getMethod("getName").invoke(laf);
                    return name != null && name.toLowerCase().contains("darcula");
                }
            } catch (Exception ex) {
                return false;
            }
        }

        return false;
    }
}
