package org.testshift.testcube.misc;

import com.google.gson.Gson;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import eu.stamp_project.dspot.common.report.output.selector.branchcoverage.json.TestClassBranchCoverageJSON;
import eu.stamp_project.dspot.common.report.output.selector.extendedcoverage.json.TestClassJSON;
import eu.stamp_project.dspot.selector.branchcoverageselector.BranchCoverage;
import eu.stamp_project.dspot.selector.branchcoverageselector.LineCoverage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

public class Util {

    public static String getAmplifiedTestClassPath(Project currentProject, String testClass) {
        return getTestClassPath(currentProject, testClass, false);
    }

    public static String getOriginalTestClassPath(Project currentProject, String testClass) {
        return getTestClassPath(currentProject, testClass, true);
    }

    private static String getTestClassPath(Project currentProject, String testClass, boolean original) {
        return currentProject.getBasePath() + Config.OUTPUT_PATH_DSPOT + (original ? File.separator + "original" : "") +
               File.separator + testClass.replaceAll("\\.", Matcher.quoteReplacement(File.separator)) + ".java";
    }

    public static String getDSpotOutputPath(Project project) {
        return project.getBasePath() + Config.OUTPUT_PATH_DSPOT;
    }

    public static String getTestCubeOutputPath(Project project) {
        return project.getBasePath() + Config.OUTPUT_PATH_TESTCUBE;
    }


    public static TestClassJSON getResultJSON(Project project, String testClass) {
        Gson gson = new Gson();
        try {
            return gson.fromJson(new FileReader(
                                         project.getBasePath() + Config.OUTPUT_PATH_DSPOT + File.separator + testClass + "_report.json"),
                                 TestClassJSON.class);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static TestClassBranchCoverageJSON getBranchCoverageJSON(Project project, String testClass) {
        Gson gson = new Gson();
        try {
            return gson.fromJson(new FileReader(
                                         project.getBasePath() + Config.OUTPUT_PATH_DSPOT + File.separator + testClass + "_report.json"),
                                 TestClassBranchCoverageJSON.class);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Set<String> getInitialCoveredLine(TestClassBranchCoverageJSON result){
        Set<String> coveredLines = new HashSet<>();
        List<LineCoverage> lineCoverages = result.getInitialLineCoverage();
        for(LineCoverage lineCoverage: lineCoverages){
            coveredLines.add(lineCoverage.getLine()+"");
        }
        return coveredLines;
    }

    public static class Branch {
        private String line;
        private String symbol;
        public Branch(String line, String symbol){
            this.line = line;
            this.symbol = symbol;
        }

        public String getLine() {
            return line;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static Set<Branch> getInitialCoveredBranch(TestClassBranchCoverageJSON result){
        Set<Branch> coveredBranches = new HashSet<>();
        List<BranchCoverage> branchCoverages = result.getInitialBranchCoverage();
        for(BranchCoverage branchCoverage: branchCoverages){
            if(branchCoverage.getTrueHitCount()>0){
                coveredBranches.add(new Branch(branchCoverage.getRegion().getStartLine() + "", "True"));
            }
            if(branchCoverage.getFalseHitCount()>0){
                coveredBranches.add(new Branch(branchCoverage.getRegion().getStartLine() + "", "False"));
            }
        }
        return coveredBranches;
    }

    public static boolean matchMethodNameAndDescriptor(PsiMethod psiMethod, String name, String descriptor) {
        return psiMethod.getName().equals(name) && ClassUtil.getAsmMethodSignature(psiMethod).equals(descriptor);
    }

    public static void sleepAndRefreshProject(Project project) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Objects.requireNonNull(ProjectUtil.guessProjectDir(project)).refresh(false, true);
    }
}
