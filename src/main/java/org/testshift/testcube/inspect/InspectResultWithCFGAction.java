package org.testshift.testcube.inspect;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import eu.stamp_project.dspot.common.report.output.selector.branchcoverage.json.TestClassBranchCoverageJSON;
import org.jetbrains.annotations.NotNull;
import org.testshift.testcube.branches.CFGPanel;
import org.testshift.testcube.branches.CFGWindow;
import org.testshift.testcube.icons.TestCubeIcons;
import org.testshift.testcube.misc.Util;
import org.testshift.testcube.model.GenerationResult;

import java.util.List;

public class InspectResultWithCFGAction extends NotificationAction {
    private final Project project;
    private final String testClass;
    private final String testMethod;
//    private CFGPanel cfgPanel;
    private CFGWindow cfgWindow;
    private String targetMethod;
    private String targetBranch;
    private List<String> expectedTests;


    public InspectResultWithCFGAction(Project project, String testClass, String testMethod, CFGWindow cfgWindow,
                                      String targetMethod, String targetBranch, List<String> expectedTests) {
        super("Inspect amplification results");
        this.project = project;
        this.testClass = testClass;
        this.testMethod = testMethod;
        this.targetMethod = targetMethod;
        this.targetBranch = targetBranch;
//        this.cfgPanel = cfgPanel;
        this.cfgWindow = cfgWindow;
        this.expectedTests = expectedTests;
    }

    @Override
    public void update(AnActionEvent e) {
        // Set the availability based on whether a project is open
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        GenerationResult generationResult = GenerationResult.buildGenerationResult(project, testClass, targetBranch,
                                                                                   cfgWindow.getCfgPanel().getInitialCoveredLines(),
                                                                                   cfgWindow.getCfgPanel().getInitialCoveredBranches(),
                                                                                   expectedTests);

        ResultWithCFGWindow resultWithCFGWindow = new ResultWithCFGWindow(cfgWindow, targetMethod, generationResult);

        ToolWindow toolWindow = ToolWindowManager.getInstance(e.getProject()).getToolWindow("Test Cube");
        if (toolWindow != null) {
            ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
            Content content = contentFactory.createContent(resultWithCFGWindow.getContent(),
                                                           resultWithCFGWindow.getDisplayName(), false);
            content.setCloseable(true);
            content.setIcon(TestCubeIcons.AMPLIFY_TEST);
            toolWindow.getContentManager().addContent(content);
            toolWindow.getContentManager().setSelectedContent(content);

            toolWindow.show();
        }
    }
}
