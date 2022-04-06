package org.testshift.testcube.amplify;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.Function;
import eu.stamp_project.dspot.assertiongenerator.assertiongenerator.methodreconstructor.observer.testwithloggenerator.objectlogsyntaxbuilder_constructs.objectlog.MethodsHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testshift.testcube.icons.TestCubeIcons;

import java.util.Objects;

public class AmplifyFakeMarkerContributor extends RunLineMarkerContributor {
    @Override
    public @Nullable Info getInfo(@NotNull PsiElement element) {
        Function<PsiElement, String> tooltipProvider = element1 -> {
            return "Generate Test";
        };
        if (element instanceof PsiIdentifier) {
            PsiElement parent = element.getParent();
//            if (parent instanceof PsiClass) {
//                TestFramework framework = TestFrameworks.detectFramework((PsiClass)parent);
//                if (framework != null && framework.isTestClass(parent)) {
//                    // test class
//                    return new Info(AllIcons.Actions.Colors, tooltipProvider, new StartTestCubeAction("Amplify test
//                    case"));
//                }
//            }
            if (parent instanceof PsiMethod) {
                /**
                 * From {@link com.intellij.testIntegration.TestRunLineMarkerProvider#getInfo(PsiElement)}
                 */
                PsiClass containingClass = PsiTreeUtil.getParentOfType(parent, PsiClass.class);
                if (!isTestMethod(containingClass, (PsiMethod) parent) && !containingClass.getName().contains("Test") &&
                    !((PsiMethod) parent).isConstructor() && supportedByDspot((PsiMethod) parent)) {
                    PsiClass targetClass = Objects.requireNonNull(((PsiMethod) parent).getContainingClass());

                    VirtualFile file = parent.getContainingFile().getVirtualFile();
                    if (file != null) {
//                        VirtualFile moduleRoot = ProjectFileIndex.SERVICE.getInstance(parent.getProject())
//                                                                         .getContentRootForFile(file);
//                        String moduleRootPath;
//                        if (moduleRoot == null) {
//                            moduleRootPath = parent.getProject().getBasePath();
//                        } else {
//                            moduleRootPath = moduleRoot.getPath();
//                        }
                        return new Info(TestCubeIcons.AMPLIFY_TEST, tooltipProvider,
                                        new FakeAction("TestCube Option"));
                    }
                }
            }
        }
        return null;
    }

    /**
     * From {@link com.intellij.testIntegration.TestRunLineMarkerProvider#getInfo(PsiElement)}
     */
    private static boolean isTestMethod(PsiClass containingClass, PsiMethod method) {
        if (containingClass == null) return false;
        TestFramework framework = TestFrameworks.detectFramework(containingClass);
        return framework != null && framework.isTestMethod(method, false);
    }

    private static boolean supportedByDspot(PsiMethod method){
        PsiModifierList psiModifierList = method.getModifierList();
        boolean isPublic =
                psiModifierList.hasModifierProperty(PsiModifier.PUBLIC) && !psiModifierList.hasModifierProperty(PsiModifier.STATIC) && !psiModifierList.hasModifierProperty(PsiModifier.ABSTRACT);
        boolean notSupportive = !isASupportedMethodName(method.getName())
                                || (MethodsHandler.isASupportedMethodName(method.getName()) && !method.getParameterList()
                                                                                                      .isEmpty());
        return isPublic && notSupportive;
    }

    public static boolean isASupportedMethodName(String name) {
        return name.startsWith("has") ||
               name.startsWith("get") ||
               name.startsWith("is") ||
               name.startsWith("should") ||
               name.equals("toString") ||
               name.startsWith("hashCode");
    }

    private class FakeAction extends AnAction{
        private Project project;
        private FakeAction(String text){
            super(text, "TestCube Option",TestCubeIcons.AMPLIFY_TEST_DARK);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            this.project = e.getProject();
            return;
        }

        @Override
        public void update(AnActionEvent e) {
            // Set the availability based on whether a project is open
            Project project = e.getProject();
            e.getPresentation().setEnabledAndVisible(project != null);
        }
    }
}
