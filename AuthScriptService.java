package com.example.k8sui.service; // adjust to your package

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Service
public class AuthScriptService {

    // Path to your login script (set in application.properties). Should be an absolute path.
    private final String loginScriptPath;

    // Timeout in seconds for script execution
    private final Duration timeout;

    // Very small whitelist for namespace names to avoid injection
    private static final Pattern SAFE_NAMESPACE = Pattern.compile("^[a-z0-9]([a-z0-9-_.]*[a-z0-9])?$", Pattern.CASE_INSENSITIVE);

    public AuthScriptService(@Value("${k8s.login.script.path:/usr/local/bin/login-to-cluster.sh}") String loginScriptPath,
                             @Value("${k8s.login.script.timeoutSec:30}") int timeoutSec) {
        this.loginScriptPath = loginScriptPath;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSec));
    }

    public ScriptResult runLoginScriptForNamespace(String namespace) throws AuthScriptException {
        validateNamespace(namespace);

        // Build command: pass namespace as an argument (not via shell expansion)
        List<String> cmd = Arrays.asList(loginScriptPath, namespace);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false); // capture stdout and stderr separately

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new AuthScriptException("Failed to start login script: " + e.getMessage(), e);
        }

        ExecutorService ex = Executors.newFixedThreadPool(2);
        Future<String> stdoutF = ex.submit(() -> streamToString(process.getInputStream()));
        Future<String> stderrF = ex.submit(() -> streamToString(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killQuietly(process);
            ex.shutdownNow();
            throw new AuthScriptException("Interrupted while waiting for login script", e);
        }

        if (!finished) {
            killQuietly(process);
            ex.shutdownNow();
            throw new AuthScriptException("Login script timed out after " + timeout.toSeconds() + "s");
        }

        int exit = process.exitValue();
        String stdout = "";
        String stderr = "";
        try {
            stdout = stdoutF.get(200, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        try {
            stderr = stderrF.get(200, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}

        ex.shutdown();

        return new ScriptResult(exit, stdout, stderr);
    }

    private static String streamToString(InputStream is) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static void killQuietly(Process p) {
        try { p.destroyForcibly(); } catch (Exception ignored) {}
    }

    private void validateNamespace(String ns) {
        if (ns == null || ns.isEmpty()) throw new IllegalArgumentException("namespace required");
        if (!SAFE_NAMESPACE.matcher(ns).matches()) throw new IllegalArgumentException("invalid namespace: " + ns);
    }

    public static class ScriptResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public ScriptResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode; this.stdout = stdout == null ? "" : stdout; this.stderr = stderr == null ? "" : stderr;
        }
    }

    public static class AuthScriptException extends Exception {
        public AuthScriptException(String message) { super(message); }
        public AuthScriptException(String message, Throwable cause) { super(message, cause); }
    }
}
