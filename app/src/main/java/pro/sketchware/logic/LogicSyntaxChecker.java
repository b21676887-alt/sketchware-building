package pro.sketchware.logic;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import pro.sketchware.beans.BlockBean;
import java.util.ArrayList;

//import a.a.a.Fx;
import pro.sketchware.core.codegen.BlockInterpreter;

//import a.a.a.jq;
import pro.sketchware.core.project.BuildConfig;

public class LogicSyntaxChecker {

    public static class SyntaxResult {
        public final boolean isValid;
        public final String errorMessage;
        public final String generatedCode;

        public SyntaxResult(boolean isValid, String errorMessage, String generatedCode) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.generatedCode = generatedCode;
        }
    }

    /**
     * Checks the syntax of the generated code from blocks.
     * We wrap the block code in a dummy class/method to allow JavaParser to parse it.
     */
    public static SyntaxResult check(String activityName, BuildConfig buildConfig, ArrayList<BlockBean> blocks, boolean isViewBindingEnabled) {
        if (blocks == null || blocks.isEmpty()) {
            return new SyntaxResult(true, null, "");
        }

        try {
            // Generate the code using the existing Fx class
            BlockInterpreter generator = new BlockInterpreter(activityName, buildConfig, blocks, isViewBindingEnabled);
            String code = generator.interpretBlocks();

            if (code == null || code.trim().isEmpty()) {
                return new SyntaxResult(true, null, "");
            }

            // Wrap in a method and class for parsing
            String wrappedCode = "class Dummy { void dummy() {\n" + code + "\n} }";

            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> result = parser.parse(wrappedCode);

            if (result.isSuccessful()) {
                return new SyntaxResult(true, null, code);
            } else {
                String errors = result.getProblems().stream()
                        .map(p -> p.getMessage())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("Unknown syntax error");
                return new SyntaxResult(false, errors, code);
            }
        } catch (Exception e) {
            return new SyntaxResult(false, "Exception during check: " + e.getMessage(), "");
        }
    }
}
