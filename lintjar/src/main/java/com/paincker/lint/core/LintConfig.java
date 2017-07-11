package com.paincker.lint.core;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.intellij.psi.PsiElement;

import org.apache.commons.compress.utils.IOUtils;
import org.w3c.dom.Node;

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
     * git中的历史文件
     */
    private HashSet<String> mOldFiles;
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
                if (notEmpty(mGitDir)) {
                    mGitBasedIssueSet = listToSet(config.mGitBasedIssues);
                    mOldFiles = readOldFiles(projectDir, config.mGitBase);
                }
                if (notEmpty(config.mLogFile)) {
                    LogUtils.setLogFile(new File(projectDir, config.mLogFile));
                }
                LogUtils.d("git base = " + config.mGitBase + ", git dir = " + mGitDir);
            }
        }
    }

    public boolean shouldCheckFile(XmlContext context, Issue issue, Node node) {
        if (context == null || issue == null || node == null || mOldFiles == null || mOldFiles.isEmpty()
                || mGitDir == null || mGitBasedIssueSet == null || !mGitBasedIssueSet.contains(issue.getId())) {
            return true;
        }
        Location location = context.getLocation(node);
        return shouldCheckLocation(location, mGitDir, mOldFiles);
    }

    public boolean shouldCheckFile(JavaContext context, Issue issue, PsiElement node) {
        if (context == null || issue == null || node == null || mOldFiles == null || mOldFiles.isEmpty()
                || mGitDir == null || mGitBasedIssueSet == null || !mGitBasedIssueSet.contains(issue.getId())) {
            return true;
        }
        Location location = context.getLocation(node);
        return shouldCheckLocation(location, mGitDir, mOldFiles);
    }

    private boolean shouldCheckLocation(Location location, @NonNull String gitDir, @NonNull HashSet<String> oldFiles) {
        // path = "/usr/project/Test.java"
        // gitDir = "/usr/project/"
        // relative = "Test.java"
        String path = location.getFile().getAbsolutePath();
        int len = gitDir.length();
        if (!path.startsWith(mGitDir) || path.length() <= len) {
            return true;
        }
        String relative = path.substring(len);
        return !oldFiles.contains(relative);
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

    private HashSet<String> readOldFiles(File dir, String gitBase) {
        if (isEmpty(gitBase)) {
            return null;
        }
        String result = exec(dir, "git ls-tree --full-tree --full-name --name-only -r " + gitBase);
        if (result != null) {
            LogUtils.d("git old files = \n" + result);
            String[] split = result.split("\n");
            if (split.length > 0) {
                HashSet<String> oldFiles = new HashSet<>();
                for (String s : split) {
                    s = s.trim();
                    if (notEmpty(s)) {
                        oldFiles.add(s);
                    }
                }
                return oldFiles;
            }
        }
        return null;
    }

    private HashSet<String> readAddedFiles(File dir, String gitBase) {
        if (isEmpty(gitBase)) {
            return null;
        }
        // diff时包含未追踪文件
        // --intent-to-add  -N -- record only that path will be added later
        exec(dir, "git add --intent-to-add .");
        // diff输出新增文件
        String result = exec(dir, "git diff " + gitBase + " --diff-filter=A --name-only");
        if (result != null) {
            LogUtils.d("git added files = \n" + result);
            String[] split = result.split("\n");
            if (split.length > 0) {
                HashSet<String> addedFiles = new HashSet<>();
                for (String s : split) {
                    s = s.trim();
                    if (notEmpty(s)) {
                        addedFiles.add(s);
                    }
                }
                return addedFiles;
            }
        }
        return null;
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
