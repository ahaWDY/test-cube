package org.testshift.testcube.inspect;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Contract;
import org.testshift.testcube.branches.CFGWindow;
import org.testshift.testcube.branches.rendering.RenderCommand;
import org.testshift.testcube.misc.TestCubeNotifier;
import org.testshift.testcube.model.GeneratedTestCase;
import org.testshift.testcube.model.GenerationResult;

import javax.swing.*;
import java.awt.*;

public class ResultWithCFGWindow extends Component {
    private JPanel amplificationResultPanel;

    private TestCaseEditorField amplifiedTestCase;

    private JPanel buttons;
    private JButton add;
    private JButton ignore;
    private JButton next;
    private JButton previous;
    private JButton close;
    private JPanel testCasePanel;

    private CFGWindow cfgWindow;

    private int currentAmplificationTestCaseIndex;
    GeneratedTestCase currentTestCase;

    private String targetMethod;
    private GenerationResult generationResult;


    public ResultWithCFGWindow(CFGWindow cfgWindow, String targetMethod, GenerationResult generationResult){
        this.amplificationResultPanel = new JPanel();

        this.cfgWindow = cfgWindow;

        this.targetMethod = targetMethod;
        this.currentAmplificationTestCaseIndex = 0;
        this.generationResult = generationResult;
        this.currentTestCase = generationResult.generatedTestCases.get(
                currentAmplificationTestCaseIndex);

        cfgWindow.getCfgPanel().render(RenderCommand.Reason.FILE_SWITCHED);
        cfgWindow.getCfgPanel().displayResult(RenderCommand.Reason.FILE_SWITCHED);
        cfgWindow.getCfgPanel().maintainInitialCover();
        cfgWindow.getCfgPanel().setNewCoveredLines(currentTestCase.newCoveredLine);
        cfgWindow.getCfgPanel().setNewCoveredBranches(currentTestCase.newCovredBranch);
        cfgWindow.getCfgPanel().maintainNewCover();
        cfgWindow.getCfgPanel().setLayout(new GridLayout());

        amplifiedTestCase = new TestCaseEditorField();
        amplifiedTestCase.createEditor();
        showTestCaseInEditor(currentTestCase, amplifiedTestCase);

        this.buttons = new JPanel();
        this.add = new JButton("Add Test To Test Suite");
        this.ignore = new JButton("Ignore Test Case");
        this.next = new JButton("Next Test Case");
        this.previous = new JButton("Previous Test Case");
        this.close = new JButton("Close Amplification Result");
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(add);
        buttons.add(ignore);
        buttons.add(next);
        buttons.add(previous);
        buttons.add(close);

        close.addActionListener(l -> close());
        add.addActionListener(l -> addTestCaseToTestSuite());
        ignore.addActionListener(l -> ignoreTestCase());
        next.addActionListener(l -> nextTestCase());
        previous.addActionListener(l -> previousTestCase());
        this.testCasePanel = new JPanel();
        testCasePanel.setLayout(new BorderLayout());
        testCasePanel.add(amplifiedTestCase, BorderLayout.CENTER);
        testCasePanel.add(buttons, BorderLayout.SOUTH);
        amplificationResultPanel.setVisible(true);
        amplificationResultPanel.setLayout(new BorderLayout());
        amplificationResultPanel.add(testCasePanel, BorderLayout.NORTH);
        amplificationResultPanel.add(cfgWindow.getContent(), BorderLayout.CENTER);
    }

    private void showTestCaseInEditor(GeneratedTestCase testCase, TestCaseEditorField editor) {
        //todo: add comment about the target branch
        //todo: modify the method name containing the target branch
        editor.setText(testCase.getMethod().getText());
    }

    @Contract("false, true -> fail")
    private void navigateTestCases(boolean forward, boolean removeCurrent) {
        if (!forward & removeCurrent) throw new IllegalArgumentException();

        if (removeCurrent) {
            //todo: remove also from the coverage json file
            generationResult.generatedTestCases.remove(currentTestCase);
            currentAmplificationTestCaseIndex--;
            if (generationResult.generatedTestCases.isEmpty()) {
                TestCubeNotifier notifier = new TestCubeNotifier();
                notifier.notify(generationResult.project,
                                "All generated test cases were added or ignored. Thank you for using Test Cube!");
                return;
            }
        }
        if (forward) {
            if (currentAmplificationTestCaseIndex + 1 == generationResult.generatedTestCases.size()) {
                currentAmplificationTestCaseIndex = 0;
            } else {
                currentAmplificationTestCaseIndex++;
            }
        } else {
            if (currentAmplificationTestCaseIndex == 0) {
                currentAmplificationTestCaseIndex = generationResult.generatedTestCases.size() - 1;
            } else {
                currentAmplificationTestCaseIndex--;
            }
        }
        currentTestCase = generationResult.generatedTestCases.get(currentAmplificationTestCaseIndex);
        cfgWindow.getCfgPanel().setNewCoveredLines(currentTestCase.newCoveredLine);
        cfgWindow.getCfgPanel().setNewCoveredBranches(currentTestCase.newCovredBranch);
        cfgWindow.getCfgPanel().maintainNewCover();
        showTestCaseInEditor(currentTestCase, amplifiedTestCase);
    }

    public void close() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(generationResult.project).getToolWindow("Test Cube");
        if (toolWindow != null) {
            toolWindow.getContentManager()
                      .removeContent(toolWindow.getContentManager().findContent(getDisplayName()), true);
            if (toolWindow.getContentManager().getContentCount() == 0) {
                toolWindow.hide();
            }
        }
    }

    public void addTestCaseToTestSuite() {
        GeneratedTestCase testToAdd = currentTestCase;

        PsiMethod method = testToAdd.getMethod();
        WriteCommandAction.runWriteCommandAction(generationResult.project, () -> {
            if (method != null) {
                PsiMethod methodSave = (PsiMethod) method.copy();
                generationResult.getOriginalClass().add(methodSave);
                method.delete();
                PsiDocumentManager.getInstance(generationResult.project).commitAllDocuments();

                navigateTestCases(true, true);
            }
        });
    }

    public void deleteAmplifiedTestCaseFromFile() {
        PsiMethod method = currentTestCase.getMethod();
        WriteCommandAction.runWriteCommandAction(generationResult.project, () -> {
            if (method != null) {
                method.delete();
                PsiDocumentManager.getInstance(generationResult.project).commitAllDocuments();
            }
        });
    }

    public void ignoreTestCase() {
        deleteAmplifiedTestCaseFromFile();
        navigateTestCases(true, true);
    }

    public void nextTestCase() {
        navigateTestCases(true, false);
    }

    public void previousTestCase() {
        navigateTestCases(false, false);
    }

    public JComponent getContent() {
        return amplificationResultPanel;
    }

    public String getDisplayName() {
        return "Test Generation for " + targetMethod+  "()'";
    }
}
