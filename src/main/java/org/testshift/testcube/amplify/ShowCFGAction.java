package org.testshift.testcube.amplify;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.lang.LangBundle;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testIntegration.LanguageTestCreators;
import com.intellij.testIntegration.TestCreator;
import com.intellij.testIntegration.TestFinderHelper;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ObjectUtils;
import eu.stamp_project.dspot.common.report.output.selector.branchcoverage.json.TestClassBranchCoverageJSON;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import eu.stamp_project.dspot.common.report.output.selector.TestClassJSON;
import org.testshift.testcube.branches.*;
import org.testshift.testcube.branches.rendering.*;
import org.testshift.testcube.icons.TestCubeIcons;
import org.testshift.testcube.inspect.InspectTestCubeResultsAction;
import org.testshift.testcube.misc.Config;
import org.testshift.testcube.misc.TestCubeNotifier;
import org.testshift.testcube.misc.Util;
import org.testshift.testcube.settings.AppSettingsState;
import org.testshift.testcube.settings.AskJavaPathDialogWrapper;
import org.testshift.testcube.settings.AskMavenHomeDialogWrapper;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;


public class ShowCFGAction extends AnAction {
    private static final Logger logger = Logger.getInstance(ShowCFGAction.class);
    private final PsiClass targetClass;
    private String targetClassName;
    private final PsiMethod targetMethod;
    private  String targetMethodName;
    private PsiClass testClass;
    private String testClassName;
    private String testMethodsName;
    private Project project;
    private String moduleRootPath;

    public ShowCFGAction(@Nullable /*@Nls(capitalization = Nls.Capitalization.Title)*/ String text,
                         PsiClass targetClass, PsiMethod targetMethod, String moduleRootPath) {
        super(text, "generate test cases for the selected method", TestCubeIcons.AMPLIFY_TEST);
        this.moduleRootPath = moduleRootPath;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.targetClassName = targetClass.getQualifiedName();
        this.targetMethodName = targetMethod.getName();
    }

    @Override
    public void update(AnActionEvent e) {
        // Set the availability based on whether a project is open
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        this.project = event.getProject();
        //find testclass
        Collection<PsiElement> testClasses = ReadAction.compute(() -> TestFinderHelper.findTestsForClass(targetClass));
        final List<PsiElement> candidates = Collections.synchronizedList(new ArrayList<>());
        candidates.addAll(testClasses);
        if(candidates.size()>0) {
            this.testClass = (PsiClass) candidates.get(0);
        }

        if(this.testClass==null){
            Editor editor = event.getData(CommonDataKeys.EDITOR);
            if (editor == null || project == null) return;
            PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
            if (file == null) return;
            CreateTestDialog createTestDialog = new CreateTestDialog(editor, file);
            createTestDialog.pack();
            createTestDialog.setVisible(true);

            return;
        }

//        try {
//            Thread.currentThread().sleep(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        // find testMethods;
        this.testClassName = testClass.getQualifiedName();
        PsiMethod[] testMethods = this.testClass.getMethods();

        if(testMethods.length ==0 || isEmptyMethods(testMethods)){
            WriteObjectDialog writeObjectDialog = new WriteObjectDialog();
            writeObjectDialog.pack();
            writeObjectDialog.setVisible(true);
            return;
        }

        this.testMethodsName = getMethodsString(testMethods);


        try {
            runDSpotForCoverage(this.project);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String getMethodsString(PsiMethod[] testMethods) {
        String methods = "";
        for(PsiMethod method: testMethods){
            methods = methods + method.getName() +",";
        }
        methods = methods.substring(0,methods.length()-1);
        return methods;
    }

    private boolean isEmptyMethods(PsiMethod[] testMethods) {
        boolean empty = true;
        for(PsiMethod method: testMethods){
            if(!method.getBody().isEmpty()){
                empty = false;
                break;
            }
        }
        return empty;
    }

    private void runDSpotForCoverage(Project currentProject) throws IOException, InterruptedException {

        IdeaPluginDescriptor testCubePlugin = PluginManagerCore.getPlugin(PluginId.getId("org.testshift.testcube"));
        if (testCubePlugin != null) {
            testCubePlugin.getPluginClassLoader();
        }
        DSpotStartConfiguration configuration = new DSpotStartConfiguration(currentProject, moduleRootPath);

        Task.Backgroundable dspotTask = new Task.Backgroundable(currentProject, "Computing coverage", true) {
            public void run(@NotNull ProgressIndicator indicator) {
                // clean output directory
//                // todo close open amplification result windows or split output into different directories
                File outputDir = new File(Util.getOutputSavePath(currentProject));
                if (!outputDir.exists()) {
                    if (!outputDir.mkdirs()) {
                        logger.error("Could not create TestCube output directory!");
                    }
                }

                // run amplification
                spawnDSpotProcess(configuration, currentProject);

                // save output
//                try {
//                    FileUtils.deleteDirectory(new File(Util.getOutputSavePath(currentProject)));
//                    Files.move(new File(Util.getDSpotOutputPath(currentProject)).toPath(),
//                               new File(Util.getOutputSavePath(currentProject)).toPath(),
//                               StandardCopyOption.REPLACE_EXISTING);

//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

                // popup about completion
                notifyDSpotFinished(currentProject);
            }
        };

        BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(dspotTask);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(dspotTask, processIndicator);
    }

    private void spawnDSpotProcess(DSpotStartConfiguration configuration, Project currentProject) {
        List<String> dSpotStarter = configuration.getCommandLineOptions(testClassName, testMethodsName);
        dSpotStarter.set(10, "BranchCoverageSelector");
        dSpotStarter.set(20, Config.AMPLIFIERS_TARGET);
        dSpotStarter.set(29, "All");
        dSpotStarter.add("--target-class");
        dSpotStarter.add(targetClassName);
        dSpotStarter.add("--target-method");
        dSpotStarter.add(targetMethodName);


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

    private void notifyDSpotFinished(Project currentProject) {
        TestCubeNotifier notifier = new TestCubeNotifier();
        notifier.notify(currentProject, "Initial Coverage computing finished",
                        new ShowCFGCoverageAction(project, targetClass, targetMethod, testClass,
                                                  testMethodsName,
                                                  moduleRootPath));
    }
}
