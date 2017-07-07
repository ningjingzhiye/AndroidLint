package com.paincker.lint.core;

import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.psi.PsiExpression;

import org.apache.commons.compress.utils.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by jzj on 2017/7/4.
 */
public final class LintConfig {

    public static final String CONFIG_FILE_NAME = "custom-lint-config.json";

    private static LintConfig sInstance;

    public static LintConfig getInstance(Context context) {
        if (sInstance == null) {
            synchronized (LintConfig.class) {
                if (sInstance == null) {
                    sInstance = new LintConfig(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * git工程文件夹，末尾包含separator，例如"/usr/project/"
     */
    private String mGitDir;
    /**
     * git中新增的文件
     */
    private HashSet<String> mAddedFiles;
    /**
     * 需要区分git版本的Issue
     */
    private HashSet<String> mGitBasedIssueSet;

    private LintConfig(Context context) {
        File projectDir = context.getProject().getDir();
        File configFile = new File(projectDir, CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            Config config = readConfig(configFile);
            if (config != null) {
                mGitDir = getGitDir(projectDir);
                mGitBasedIssueSet = listToSet(config.mGitBasedIssues);
                mAddedFiles = readAddedFiles(projectDir, config.mGitBase);
                if (notEmpty(config.mLogFile)) {
                    LogUtils.setLogFile(new File(projectDir, config.mLogFile));
                }
                LogUtils.d("git base = " + config.mGitBase + ", git dir = " + mGitDir);
            }
        }
    }

    public boolean shouldCheckFile(JavaContext context, Issue issue, PsiExpression call) {
        if (context == null || issue == null || call == null || mAddedFiles == null || mGitDir == null
                || mGitBasedIssueSet == null || !mGitBasedIssueSet.contains(issue.getId())) {
            return true;
        }
        // path = "/usr/project/Test.java"
        // gitDir = "/usr/project/"
        // relative = "Test.java"
        String path = context.getLocation(call).getFile().getAbsolutePath();
        int len = mGitDir.length();
        if (!path.startsWith(mGitDir) || path.length() <= len) {
            return true;
        }
        String relative = path.substring(len);
        return mAddedFiles.contains(relative);
    }

    private Config readConfig(File configFile) {
        String s = readAsString(configFile);
        LogUtils.d("configFile = \n" + String.valueOf(s));
        return s == null ? null : new Gson().fromJson(s, Config.class);
    }

    private String readAsString(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            return toString(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(reader);
        }
        return null;
    }

    private HashSet<String> readAddedFiles(File projectDir, String gitBase) {
        if (isEmpty(gitBase)) {
            return null;
        }
        HashSet<String> addedFiles = null;
        String result = exec(projectDir, "git diff " + gitBase + " --diff-filter=A --name-only");
        if (result != null) {
            LogUtils.d("git added files = \n" + result);
            String[] split = result.split("\n");
            if (split.length > 0) {
                addedFiles = new HashSet<>();
                for (String s : split) {
                    s = s.trim();
                    if (notEmpty(s)) {
                        addedFiles.add(s);
                    }
                }
            }
        }
        return addedFiles;
    }

    /**
     * 获取git工程根目录
     */
    private String getGitDir(File projectDir) {
        String gitDir = exec(projectDir, "git rev-parse --show-toplevel");
        if (gitDir != null && !gitDir.endsWith(File.separator)) {
            gitDir += File.separator;
        }
        return gitDir;
    }

    private String exec(File dir, String cmd) {
        BufferedReader reader = null;
        try {
            Process pro = Runtime.getRuntime().exec(cmd, null, dir);
            pro.waitFor();
            reader = new BufferedReader(new InputStreamReader(pro.getInputStream()));
            return toString(reader);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(reader);
        }
        return null;
    }

    private String toString(BufferedReader read) throws IOException {
        StringBuilder s = null;
        String line;
        while ((line = read.readLine()) != null) {
            if (s == null) {
                s = new StringBuilder(line);
            } else {
                s.append('\n').append(line);
            }
        }
        return s == null ? "" : s.toString();
    }

    private <T> HashSet<T> listToSet(List<T> list) {
        if (list == null) return null;
        if (list.isEmpty()) return new HashSet<T>();
        HashSet<T> set = new HashSet<>();
        set.addAll(list);
        return set;
    }

    private boolean notEmpty(String gitBase) {
        return gitBase != null && gitBase.length() > 0;
    }

    private boolean isEmpty(String gitBase) {
        return gitBase == null || gitBase.length() == 0;
    }

    private static class Config {
        @SerializedName("git-base")
        String mGitBase;

        @SerializedName("git-based-issues")
        ArrayList<String> mGitBasedIssues;

        @SerializedName("log-file")
        String mLogFile;
    }
}
