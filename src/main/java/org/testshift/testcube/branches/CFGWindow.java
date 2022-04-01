package org.testshift.testcube.branches;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.checkout.ProjectCheckoutListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import eu.stamp_project.dspot.common.report.output.selector.branchcoverage.json.TestClassBranchCoverageJSON;
import eu.stamp_project.dspot.common.report.output.selector.extendedcoverage.json.TestClassJSON;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.testshift.testcube.amplify.*;
import org.testshift.testcube.branches.preview.image.links.Highlighter;
import org.testshift.testcube.branches.rendering.ImageFormat;
import org.testshift.testcube.branches.rendering.RenderCommand;
import org.testshift.testcube.inspect.InspectResultWithCFGAction;
import org.testshift.testcube.inspect.InspectTestCubeResultsAction;
import org.testshift.testcube.misc.Config;
import org.testshift.testcube.misc.TestCubeNotifier;
import org.testshift.testcube.misc.Util;
import org.testshift.testcube.model.GenerationResult;
import org.testshift.testcube.settings.AppSettingsState;
import org.testshift.testcube.settings.AskJavaPathDialogWrapper;
import org.testshift.testcube.settings.AskMavenHomeDialogWrapper;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CFGWindow extends JPanel implements Disposable {
    private static final Logger logger = Logger.getInstance(CFGWindow.class);

    private Project project;
    private String targetClass;
    private String targetMethod;
    private static JPanel contentPanel;
    private CFGPanel cfgPanel;
    private JPanel buttonPanel;
    private JButton finish;
    private JButton close;
    private String moduleRootPath;
    private String testClass;
    private String testMethod;
//    private Set<String> initialCoveredLines;
//    private Set<Util.Branch> initialCoveredBranches;

    private int branchNum;
    private int startLine;
    private String selectedBranch;

    public CFGWindow(Project project, String targetClass, String targetMethod, String source,
                     Set<String> initialCoveredLines,
                     Set<Util.Branch> initialCoveredBranches,
                     String moduleRootPath, String testClass, String testMethod, int branchNum, int startLine){
        this.project = project;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.moduleRootPath = moduleRootPath;
        this.testClass = testClass;
        this.testMethod = testMethod;
//        this.initialCoveredLines = initialCoveredLines;
//        this.initialCoveredBranches = initialCoveredBranches;
        this.branchNum = branchNum;
        this.startLine = startLine;

        this.contentPanel = new JPanel();
        this.buttonPanel = new JPanel();
        this.finish = new JButton("Ok");
        this.close = new JButton("Close");

        ImageFormat imageFormat = ImageFormat.PNG;
        int page = 0;
        int version = 0;
        RenderCommand.Reason reason = RenderCommand.Reason.FILE_SWITCHED;
        this.cfgPanel = new CFGPanel(/*sourceFilePath,*/ source, imageFormat, page, version, initialCoveredLines, initialCoveredBranches);
        cfgPanel.render(reason);
        cfgPanel.displayResult(reason);
        cfgPanel.maintainInitialCover();
        cfgPanel.setLayout(new GridLayout());
        finish.addActionListener(l-> finishSelection(project));
        close.addActionListener(l->cancel());
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(finish);
        buttonPanel.add(close);
        contentPanel.setVisible(true);
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(cfgPanel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void cancel() {
        close(project, true);
    }


    public static JPanel getContent() {
        return contentPanel;
    }

    public CFGPanel getCfgPanel() {
        return cfgPanel;
    }

    public JPanel getButtonPanel() {
        return buttonPanel;
    }

    public String getDisplayName(){
        if(cfgPanel.getNewCoveredBranches()!=null || cfgPanel.getNewCoveredLines()!=null){
            return "Test Generation for " + targetMethod+  "()'";
        }
        return "Control Flow Graph of "+ targetMethod;
    }

    private void finishSelection(Project project) {
        if(branchNum>0) {
            cfgPanel.recordHilight();
            selectedBranch = cfgPanel.getHilightText();
            if (!selectedBranch.equals("")) {
                //todo: cancel highlight
                new Highlighter().highlightImages(cfgPanel.getImagesPanel(), selectedBranch);
                List<String> expectedTests = Util.getExistingTestMethods(project, testClass, selectedBranch, startLine);
                if(expectedTests.isEmpty()) {
                    try {
                        runDSpot(project, selectedBranch);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    runDspotForCoverage(project,expectedTests);
                }
                close(project,false);
            }
            else {
                NoSelectionDialog dialog = new NoSelectionDialog();
                dialog.pack();;
                dialog.setVisible(true);
            }
        }
        else{
            selectedBranch = "noBranch";
            if(cfgPanel.getInitialCoveredLines().isEmpty()) {
                List<String> expectedTests = Util.getExistingTestMethods(project, testClass, selectedBranch, startLine);
                if (expectedTests.isEmpty()) {
                    try {
                        runDSpot(project, selectedBranch);
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    runDspotForCoverage(project, expectedTests);
                }
                close(project,false);
            }
            else{
                close(project,true);
            }
        }
    }

    private void updateInitialCoverage() {
        DSpotStartConfiguration configuration = new DSpotStartConfiguration(project, moduleRootPath);
        spawnDSpotProcess(configuration, project, selectedBranch, true);
        TestClassBranchCoverageJSON coverageResult = (TestClassBranchCoverageJSON) Util.getBranchCoverageJSON(project,
                                                                                                              testClass, true);
        Set<String> initialCoveredLines = Util.getInitialCoveredLine(coverageResult);
        Set<Util.Branch> initialCoveredBranches = Util.getInitialCoveredBranch(coverageResult);
        this.cfgPanel.maintainNewInitialCover(initialCoveredLines, initialCoveredBranches);
    }

    private void runDspotForCoverage(Project currentProject, List<String> expectedTests){
        Task.Backgroundable dspotTask = new Task.Backgroundable(currentProject, "Amplifying test", true) {
            public void run(@NotNull ProgressIndicator indicator) {
                if(cfgPanel.getNewCoveredBranches()!=null || cfgPanel.getNewCoveredLines()!=null) {
                    updateInitialCoverage();
                }
                notifyDSpotFinished(project, expectedTests);
            }
        };
        BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(dspotTask);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(dspotTask, processIndicator);
    }

    private void runDSpot(Project currentProject, String branch) throws IOException, InterruptedException {

        IdeaPluginDescriptor testCubePlugin = PluginManagerCore.getPlugin(PluginId.getId("org.testshift.testcube"));
        if (testCubePlugin != null) {
            testCubePlugin.getPluginClassLoader();
        }
        DSpotStartConfiguration configuration = new DSpotStartConfiguration(currentProject, moduleRootPath);
        PrettifierStartConfiguration prettifierConfiguration = new PrettifierStartConfiguration(currentProject,
                                                                                                moduleRootPath);

        Task.Backgroundable dspotTask = new Task.Backgroundable(currentProject, "Amplifying test", true) {

            public void run(@NotNull ProgressIndicator indicator) {
                // clean output directory
                // todo close open amplification result windows or split output into different directories

                File outputDir = new File(Util.getOutputSavePath(currentProject));
                if (!outputDir.exists()) {
                    if (!outputDir.mkdirs()) {
                        logger.error("Could not create TestCube output directory!");
                    }
                }
                // update Initial Coverage is it's result window
                if(cfgPanel.getNewCoveredBranches()!=null || cfgPanel.getNewCoveredLines()!=null){
                    updateInitialCoverage();
                }

                spawnDSpotProcess(configuration, currentProject, branch, false);

                // save output
                try {
                    FileUtils.deleteDirectory(new File(Util.getOutputSavePath(currentProject)));
                    Files.move(new File(Util.getDSpotOutputPath(currentProject)).toPath(),
                               new File(Util.getOutputSavePath(currentProject)).toPath(),
                               StandardCopyOption.REPLACE_EXISTING);

                    // prettify generated test cases
                    spawnDSpotProcess(prettifierConfiguration, currentProject);

                    FileUtils.delete(new File(Util.getAmplifiedTestClassPathToPrettify(currentProject, testClass)));
                    Files.move(new File(Util.getAmplifiedTestClassPath(currentProject, testClass)).toPath(),
                               new File(Util.getAmplifiedTestClassPathToPrettify(currentProject, testClass)).toPath(),
                               StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }

//                Util.sleepAndRefreshProject(currentProject);
                // popup about completion
                List<String> expectedTests = Util.getExistingTestMethods(project,testClass, selectedBranch, startLine);
                notifyDSpotFinished(currentProject, expectedTests);
            }
        };

        BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(dspotTask);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(dspotTask, processIndicator);
    }

    private void spawnDSpotProcess(DSpotStartConfiguration configuration, Project currentProject, String branch,
                                   boolean forCoverage) {
        List<String> dSpotStarter = configuration.getCommandLineOptions(testClass, testMethod);
        dSpotStarter.set(10, "BranchCoverageSelector");
        dSpotStarter.set(20, Config.AMPLIFIERS_TARGET);
        dSpotStarter.set(29, "All");
        dSpotStarter.add("--target-class");
        dSpotStarter.add(targetClass);
        dSpotStarter.add("--target-method");
        dSpotStarter.add(targetMethod);
        if(! forCoverage) {
            dSpotStarter.add("--target-branch");
            dSpotStarter.add(branch);
        }


        ProcessBuilder processBuilder = prepareEnvironmentForSubprocess(dSpotStarter, currentProject, configuration);
        try {
            Process p = processBuilder.start();

            File dSpotTerminalOutput = new File(
                    Util.getTestCubeOutputPath(currentProject) + File.separator + "terminal_output_dspot.txt");

            // write the output to the console and the file simultaneously, while the project is running
            try (BufferedWriter writer =
                         new BufferedWriter(new FileWriter(dSpotTerminalOutput, configuration.appendToLog()))) {
                InputStream is = p.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    System.out.println(line);
                    writer.write(line);
                    writer.newLine();
                }
            }
            p.waitFor();
            System.out.println(p.exitValue());

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        // try to avoid the newly generated files not being found by IntelliJ
        Util.sleepAndRefreshProject(currentProject);
    }

    /**
     * Sets up and starts the subprocess that runs DSpot or the prettifier.
     * @param configuration the {@link DSpotStartConfiguration} to use, pass a
     * {@link PrettifierStartConfiguration} to run the prettifier.
     * @param currentProject the currently active project.
     */
    private void spawnDSpotProcess(DSpotStartConfiguration configuration, Project currentProject) {
        List<String> dSpotStarter = configuration.getCommandLineOptions(testClass, testMethod);
        dSpotStarter.set(27, "None");
        dSpotStarter.remove("--filter-dev-friendly");
        dSpotStarter.remove("--apply-extended-coverage-minimizer");
        dSpotStarter.remove("--prioritize-most-coverage");
        dSpotStarter.remove("--generate-descriptions");


        ProcessBuilder processBuilder = prepareEnvironmentForSubprocess(dSpotStarter, currentProject, configuration);
        try {
            Process p = processBuilder.start();

            File dSpotTerminalOutput = new File(
                    Util.getTestCubeOutputPath(currentProject) + File.separator + "terminal_output_dspot.txt");

            // write the output to the console and the file simultaneously, while the project is running
            try (BufferedWriter writer =
                         new BufferedWriter(new FileWriter(dSpotTerminalOutput, configuration.appendToLog()))) {
                InputStream is = p.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    System.out.println(line);
                    writer.write(line);
                    writer.newLine();
                }
            }
            p.waitFor();
            System.out.println(p.exitValue());

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        // try to avoid the newly generated files not being found by IntelliJ
        Util.sleepAndRefreshProject(currentProject);
    }

    /**
     * Prepares the environment variables and directories to start the DSpot process.
     */
    private ProcessBuilder prepareEnvironmentForSubprocess(List<String> dSpotStarter, Project currentProject,
                                                           DSpotStartConfiguration configuration) {
        ProcessBuilder pb = new ProcessBuilder(dSpotStarter);

        // clean output directory
        // todo close open amplification result windows or split output into different directories
        try {
            File outputDirectory = new File(configuration.getOutputDirectoryToClean());
            if (outputDirectory.exists()) {
                FileUtils.cleanDirectory(outputDirectory);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create the temporary directories to save the output in.
        // These seem arbitrary but are required by the compiler DSpot uses :)
        File workdir = new File(Util.getTestCubeOutputPath(currentProject) + File.separator + "workdir"
                                + File.separator + "target" + File.separator + "dspot");
        if (!workdir.exists()) {
            if (!workdir.mkdirs()) {
                logger.error("Could not create workdir output directory!");
            }
        }
        pb.directory(workdir);

//        File workdirTarget = new File(workdir.getPath() + File.separator + "target" + File.separator + "dspot");
//        if (!workdirTarget.exists()) {
//            if (!workdirTarget.mkdirs()) {
//                logger.error("Could not create workdir/target/dspot output directory!");
//            }
//        }

        pb.redirectErrorStream(true);
        pb.environment().put("MAVEN_HOME", AppSettingsState.getInstance().mavenHome);

        // TODO check: is this subsumed by creating the workdir?

        return pb;
    }

    private void notifyDSpotFinished(Project currentProject ,List<String> expectedTests) {
        TestCubeNotifier notifier = new TestCubeNotifier();
//        TestClassBranchCoverageJSON coverageResult =
//                (TestClassBranchCoverageJSON) Util.getBranchCoverageJSON(project, testClass, false);
//        List<String> expectedTests = Util.getTargetTestMethods(coverageResult, selectedBranch);

        if (expectedTests.isEmpty()) {
            notifier.notify(currentProject, "No new test cases found.",
                            new InspectDSpotTerminalOutputAction());
        } else {
            int amplifiedTestCasesCount = expectedTests.size();

            if (amplifiedTestCasesCount == 0) {
                notifier.notify(currentProject, "Could find no new test cases through amplification.");
            } else {
                notifier.notify(currentProject,
                                "Test Cube found " + amplifiedTestCasesCount + " amplified test cases.",
                                new InspectResultWithCFGAction(currentProject, testClass, testMethod,
                                                               this,
                                                               targetMethod,
                                                               selectedBranch, expectedTests),
                                new InspectDSpotTerminalOutputAction());
                    }
                }
    }

    private void close(Project project, boolean dispose) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Test Cube");
        if (toolWindow != null) {
            toolWindow.getContentManager()
                      .removeContent(toolWindow.getContentManager().findContent(getDisplayName()), dispose);
            if (toolWindow.getContentManager().getContentCount() == 0) {
                toolWindow.hide();
            }
        }
    }

    @Override
    public void dispose() {
        close(project, true);
    }
}
