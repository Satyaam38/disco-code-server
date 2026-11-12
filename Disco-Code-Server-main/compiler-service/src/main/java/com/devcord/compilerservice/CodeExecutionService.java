package com.devcord.compilerservice;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class CodeExecutionService {

    private static final int TIMEOUT_SECONDS = 10;

    public Map<String, Object> execute(String language, String code) {
        return execute(language, code, null);
    }

    public Map<String, Object> execute(String language, String code, String stdin) {
        Map<String, Object> result = new HashMap<>();
        Path tempDir = null;

        try {
            tempDir = Files.createTempDirectory("exec_");

            switch (language.toLowerCase()) {
                case "python":
                    return runPython(tempDir, code, stdin);
                case "java":
                    return runJava(tempDir, code, stdin);
                case "cpp":
                case "c++":
                    return runCpp(tempDir, code, stdin);
                default:
                    result.put("stdout", "");
                    result.put("stderr", "Unsupported language: " + language);
                    result.put("exitCode", -1);
                    return result;
            }
        } catch (Exception e) {
            result.put("stdout", "");
            result.put("stderr", "Server error: " + e.getMessage());
            result.put("exitCode", -1);
            return result;
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir.toFile());
            }
        }
    }

    private Map<String, Object> runPython(Path dir, String code, String stdin) throws IOException, InterruptedException {
        Path script = dir.resolve("script.py");
        Files.writeString(script, code);
        return runProcess(new ProcessBuilder("python3", script.toString()), dir, stdin);
    }

    private Map<String, Object> runJava(Path dir, String code, String stdin) throws IOException, InterruptedException {
        Path source = dir.resolve("Main.java");
        Files.writeString(source, code);

        return runProcess(new ProcessBuilder(
                "java",
                "-XX:TieredStopAtLevel=1",
                "-XX:+UseSerialGC",
                "-Xshare:auto",
                source.toString()), dir, stdin);
    }

    private Map<String, Object> runCpp(Path dir, String code, String stdin) throws IOException, InterruptedException {
        Path source = dir.resolve("script.cpp");
        Files.writeString(source, code);
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        Path binary = dir.resolve(isWindows ? "a.exe" : "a.out");

        Map<String, Object> compileResult = runProcess(
                new ProcessBuilder("g++", source.toString(), "-o", binary.toString()), dir, null);

        if ((int) compileResult.get("exitCode") != 0) {
            return compileResult;
        }

        return runProcess(new ProcessBuilder(binary.toString()), dir, stdin);
    }

    private Map<String, Object> runProcess(ProcessBuilder pb, Path dir, String stdin) throws IOException, InterruptedException {
        Map<String, Object> result = new HashMap<>();
        pb.directory(dir.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outThread = new Thread(() -> readStream(process.getInputStream(), stdout));
        Thread errThread = new Thread(() -> readStream(process.getErrorStream(), stderr));
        outThread.start();
        errThread.start();

        // Feed standard input if provided, then close stdin stream to avoid hanging
        if (stdin != null && !stdin.isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                writer.write(stdin);
                if (!stdin.endsWith("\n")) {
                    writer.write("\n");
                }
                writer.flush();
            } catch (IOException ignored) {
            }
        } else {
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
        }

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            result.put("stdout", stdout.toString());
            result.put("stderr", "Execution timed out after " + TIMEOUT_SECONDS + " seconds");
            result.put("exitCode", -1);
            return result;
        }

        outThread.join();
        errThread.join();

        result.put("stdout", stdout.toString());
        result.put("stderr", stderr.toString());
        result.put("exitCode", process.exitValue());
        return result;
    }

    private void readStream(InputStream inputStream, StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException ignored) {
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }
}