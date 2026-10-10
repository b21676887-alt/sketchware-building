package pro.sketchware.util;

import android.os.Environment;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.core.project.SketchwarePaths;

/**
 * Resolves project-scoped paths for chat tools so they never escape the
 * currently selected Sketchware project.
 */
public final class ProjectPathResolver {

    private ProjectPathResolver() {
    }

    public static final class ResolvedPath {
        private final File file;
        private final String relativePath;

        public ResolvedPath(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }

        public File getFile() {
            return file;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    public static File getSketchwareRoot() {
        return new File(SketchwarePaths.getSketchwareRoot());
    }

    public static File getDefaultWorkingRoot(String scId) {
        return getSketchwareRoot();
    }

    /**
     * Working directory for terminal tools (run_command / persistent terminals).
     * For Sketchware projects this must be the PROJECT's own folder, not the
     * shared {@code .sketchware} root — otherwise commands run at the root of all
     * projects (the "tools escape to the Android root" bug). Falls back to the
     * next existing project folder, and only to the shared root as last resort.
     */
    public static File getTerminalWorkingRoot(String scId) {
        File primary = getPrimaryReadableRoot(scId);
        if (primary != null) {
            return primary;
        }
        return new File(SketchwarePaths.getDataPath(scId));
    }

    public static List<File> getReadableRoots(String scId) {
        List<File> roots = new ArrayList<>();
        addReadableRoot(roots, new File(SketchwarePaths.getDataPath(scId)));
        addReadableRoot(roots, new File(SketchwarePaths.getMyscPath(scId)));
        addReadableRoot(roots, new File(SketchwarePaths.getProjectListPath(scId)));
        return roots;
    }

    @Nullable
    public static File getPrimaryReadableRoot(String scId) {
        List<File> roots = getReadableRoots(scId);
        return roots.isEmpty() ? null : roots.get(0);
    }

    private static void addReadableRoot(List<File> roots, File root) {
        if (root != null && root.isDirectory()) {
            roots.add(root);
        }
    }

    public static List<File> getWritableRoots(String scId) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(SketchwarePaths.getDataPath(scId)));
        roots.add(new File(SketchwarePaths.getProjectListPath(scId)));
        roots.add(new File(SketchwarePaths.getMyscPath(scId) + File.separator + "app"));
        roots.add(new File(SketchwarePaths.getMyscPath(scId) + File.separator + "bin"));
        roots.add(new File(SketchwarePaths.getMyscPath(scId) + File.separator + "gen"));
        return roots;
    }

    public static String toDisplayPath(String scId, File file) {
        if (file == null) {
            return "";
        }
        try {
            return getSketchwareRoot().toPath().relativize(file.toPath()).toString().replace(File.separator, "/");
        } catch (Exception ignored) {
            return file.getAbsolutePath();
        }
    }

    @Nullable
    public static ResolvedPath resolveForRead(String scId, String requestedPath) {
        return resolve(scId, requestedPath, true);
    }

    @Nullable
    public static ResolvedPath resolveForWrite(String scId, String requestedPath) {
        return resolve(scId, requestedPath, false);
    }

    private static ResolvedPath resolve(String scId, String requestedPath, boolean readOnlyScope) {
        if (scId == null || scId.trim().isEmpty() || requestedPath == null) {
            ChatToolLog.pathResolve(scId, String.valueOf(requestedPath), null, false);
            return null;
        }

        if (isPlaceholderPath(requestedPath)) {
            ChatToolLog.w("path", "placeholder path rejected: \"" + requestedPath + "\"");
            ChatToolLog.pathResolve(scId, requestedPath, null, false);
            return null;
        }

        if (readOnlyScope && isReadRootAlias(requestedPath)) {
            File primary = getPrimaryReadableRoot(scId);
            if (primary == null) {
                ChatToolLog.w("path", "project root alias could not be resolved for sc=" + scId);
                ChatToolLog.pathResolve(scId, requestedPath, null, false);
                return null;
            }
            String displayPath = toDisplayPath(scId, primary);
            ChatToolLog.pathResolve(scId, requestedPath, displayPath, true);
            return new ResolvedPath(primary, displayPath);
        }

        String normalizedPath = normalize(requestedPath);
        if (normalizedPath.isEmpty()) {
            ChatToolLog.w("path", "empty/traversal after normalize: \"" + requestedPath + "\"");
            ChatToolLog.pathResolve(scId, requestedPath, null, false);
            return null;
        }

        String mappedRelativePath = mapToProjectScope(scId, normalizedPath);
        if (mappedRelativePath == null) {
            ChatToolLog.w("path", "OUT_OF_SCOPE (Sketchware project): requested=\"" + requestedPath
                    + "\" normalized=\"" + normalizedPath + "\" — expected data/" + scId
                    + " or mysc/list/" + scId + " prefix");
            ChatToolLog.pathResolve(scId, requestedPath, null, false);
            return null;
        }

        if (!readOnlyScope && !isWritableProjectPath(scId, mappedRelativePath)) {
            ChatToolLog.w("path", "not writable: mapped=\"" + mappedRelativePath + "\"");
            ChatToolLog.pathResolve(scId, requestedPath, mappedRelativePath, false);
            return null;
        }

        File root = getSketchwareRoot();
        File candidate = new File(root, mappedRelativePath.replace("/", File.separator));
        if (!isInsideAllowedRoots(scId, candidate, readOnlyScope)) {
            ChatToolLog.w("path", "candidate escaped allowed roots: " + candidate.getAbsolutePath());
            ChatToolLog.pathResolve(scId, requestedPath, mappedRelativePath, false);
            return null;
        }

        ChatToolLog.pathResolve(scId, requestedPath, mappedRelativePath, true);
        return new ResolvedPath(candidate, mappedRelativePath);
    }

    public static boolean isReadRootAlias(String requestedPath) {
        if (requestedPath == null) {
            return false;
        }
        String value = requestedPath.trim();
        return value.isEmpty() || ".".equals(value) || "/".equals(value) || "\\".equals(value);
    }

    public static boolean isPlaceholderPath(String requestedPath) {
        if (requestedPath == null) {
            return false;
        }
        String value = requestedPath.trim();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return "<uri".equals(lower)
                || "<uri>".equals(lower)
                || "<path".equals(lower)
                || "<path>".equals(lower)
                || "<file_path".equals(lower)
                || "<file_path>".equals(lower)
                || "undefined".equals(lower)
                || "null".equals(lower)
                || (value.startsWith("{{") && value.endsWith("}}"))
                || (value.startsWith("${") && value.endsWith("}"));
    }

    public static boolean hasParentTraversal(String requestedPath) {
        if (requestedPath == null) {
            return false;
        }
        String value = requestedPath.trim().replace("\\", "/");
        for (String segment : value.split("/", -1)) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String requestedPath) {
        String normalizedPath = requestedPath.trim().replace("\\", "/");
        normalizedPath = normalizedPath.replaceAll("/{2,}", "/");

        if (hasParentTraversal(normalizedPath)) {
            return "";
        }

        int sketchwareIndex = normalizedPath.indexOf(".sketchware/");
        if (sketchwareIndex >= 0) {
            normalizedPath = normalizedPath.substring(sketchwareIndex + ".sketchware/".length());
        }

        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        return normalizedPath;
    }

    @Nullable
    private static String mapToProjectScope(String scId, String normalizedPath) {
        String duplicatedMyscPrefix = "mysc/" + scId + "/" + scId + "/";
        if (normalizedPath.startsWith(duplicatedMyscPrefix)) {
            normalizedPath = "mysc/" + scId + "/"
                    + normalizedPath.substring(duplicatedMyscPrefix.length());
        }
        if (normalizedPath.equals("project") || normalizedPath.equals("project.json")) {
            return "mysc/list/" + scId + "/project";
        }

        String scopedDataPrefix = "data/" + scId;
        String scopedListPrefix = "mysc/list/" + scId;
        String scopedMyscPrefix = "mysc/" + scId;

        if (normalizedPath.equals(scopedDataPrefix)
                || normalizedPath.startsWith(scopedDataPrefix + "/")
                || normalizedPath.equals(scopedListPrefix)
                || normalizedPath.startsWith(scopedListPrefix + "/")
                || normalizedPath.equals(scopedMyscPrefix)
                || normalizedPath.startsWith(scopedMyscPrefix + "/")) {
            return normalizedPath;
        }

        if (normalizedPath.startsWith("data/") || normalizedPath.startsWith("mysc/")) {
            return null;
        }

        return null;
    }

    private static boolean isWritableProjectPath(String scId, String mappedRelativePath) {
        String dataPrefix = "data/" + scId + "/";
        if (mappedRelativePath.startsWith(dataPrefix)) {
            return true;
        }
        if (mappedRelativePath.equals("mysc/list/" + scId + "/project")) {
            return true;
        }
        String myscPrefix = "mysc/" + scId + "/";
        return mappedRelativePath.startsWith(myscPrefix + "app/")
                || mappedRelativePath.startsWith(myscPrefix + "bin/")
                || mappedRelativePath.startsWith(myscPrefix + "gen/");
    }

    private static boolean isInsideAllowedRoots(String scId, File candidate, boolean readOnlyScope) {
        try {
            String candidatePath = candidate.getCanonicalPath();
            for (File root : readOnlyScope ? getReadableRoots(scId) : getWritableRoots(scId)) {
                String allowedPath = root.getCanonicalPath();
                if (candidatePath.equals(allowedPath) || candidatePath.startsWith(allowedPath + File.separator)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}
