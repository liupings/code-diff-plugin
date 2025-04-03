package com.rj.diff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;

public class action extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor requiredData = e.getRequiredData(CommonDataKeys.EDITOR);
        String selectedText = requiredData.getSelectionModel().getSelectedText();


       /* Editor selectedTextEditor = FileEditorManager.getInstance(e.getProject()).getSelectedTextEditor();

        if (null != selectedTextEditor) {
            VirtualFile file = FileDocumentManager.getInstance().getFile(selectedTextEditor.getDocument());
            if (file != null) {
                // 当前文件路径
                String filePath = file.getPath();
                System.out.println("Current file: " + filePath);

                //Messages.showInfoMessage(filePath, file.getName());
            }
        }else {
            Messages.showErrorDialog("请打开需要跟新的文件！！！", "提示");

        }

        //DiffRequestFactory.getInstance().createFromFiles(e.getProject(),)
        //
        DiffDiglog diffDiglog = new DiffDiglog();
        diffDiglog.setSize(1500, 900);
        diffDiglog.setLocationRelativeTo(null);
        diffDiglog.setVisible(true);*/
    }
}
