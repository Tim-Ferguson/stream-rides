package com.pelotonhack.windowagent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TabletWindowAgent {
    public static final int PORT = 47631;

    private static final String JUST_RIDE_ACTIVITY = "FreestyleWorkoutActivity";
    private static final long WAIT_STEP_MS = 500;
    private static final String COMPACT_OVERLAY_DENSITY = "90";
    private static final String LOG_PATH = "/data/local/tmp/saro-window-agent.log";
    private static final int CLIENT_TIMEOUT_MS = 10000;
    private static final int IDLE_SHUTDOWN_MS = 30 * 60 * 1000;
    private static final int MAX_COMMAND_LENGTH = 256;
    private static volatile boolean running = true;
    private static volatile boolean densityChanged;
    private static String capability;

    private static final int[][] JUST_RIDE_TAPS = {
            {735, 684},
            {252, 649},
            {1740, 1004},
            {775, 930},
            {735, 684},
            {252, 649}
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("A 64-character launch capability is required");
        }
        capability = args[0];
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.setSoTimeout(IDLE_SHUTDOWN_MS);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT));
        try {
            while (running) {
                try {
                    new ClientHandler(server.accept()).run();
                } catch (SocketTimeoutException exception) {
                    running = false;
                }
            }
        } finally {
            server.close();
            if (densityChanged) {
                runBestEffort("wm", "density", "reset");
            }
        }
    }

    private TabletWindowAgent() {
    }

    private static final class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket closeable = socket;
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(closeable.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(
                         new OutputStreamWriter(closeable.getOutputStream(), StandardCharsets.UTF_8), true)) {
                closeable.setSoTimeout(CLIENT_TIMEOUT_MS);
                String command = reader.readLine();
                if (command == null || command.length() > MAX_COMMAND_LENGTH) {
                    writer.println("ERR empty command");
                    return;
                }
                try {
                    writer.println(handleAuthenticatedCommand(command.trim()));
                } catch (Throwable throwable) {
                    log("command failed: " + command + "\n" + stackTrace(throwable));
                    writer.println("ERR " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            } catch (Throwable throwable) {
                // The process is intentionally long lived; never let one request kill it.
                log("client failed\n" + stackTrace(throwable));
            }
        }
    }

    private static String handleAuthenticatedCommand(String request) throws Exception {
        int separator = request.indexOf(' ');
        if (separator <= 0 || !MessageDigest.isEqual(
                request.substring(0, separator).getBytes(StandardCharsets.UTF_8),
                capability.getBytes(StandardCharsets.UTF_8))) {
            return "ERR unauthorized";
        }
        return handleCommand(request.substring(separator + 1).trim());
    }

    private static String handleCommand(String command) throws Exception {
        if ("ping".equals(command)) {
            return "OK pong";
        }
        if ("status".equals(command)) {
            return "OK " + foregroundActivity();
        }
        if ("overlay-current".equals(command)) {
            return overlayCurrent(false);
        }
        if ("overlay-current-compact".equals(command)) {
            return overlayCurrent(true);
        }
        if ("reset-density".equals(command)) {
            resetDensity();
            return "OK density reset";
        }
        if ("shutdown".equals(command)) {
            resetDensity();
            running = false;
            return "OK shutdown";
        }
        if ("keep-working-out".equals(command)) {
            keepWorkingOut();
            return "OK keep-working-out";
        }
        if ("focus-just-ride".equals(command)) {
            focusJustRide();
            return "OK focus-just-ride";
        }
        if ("pin-just-ride".equals(command)) {
            pinJustRideOverlay();
            return "OK pinned";
        }
        return "ERR unsupported command";
    }

    private static String overlayCurrent(boolean compact) throws Exception {
        enableWindowOverrides();
        String target = contentPackage();
        if (compact) {
            setCompactOverlayDensity();
        }
        pinJustRideOverlay();
        if (target != null && !target.isEmpty()) {
            bringContentToFront(target);
        }
        return "OK overlay-current target=" + (target == null ? "none" : target)
                + " compact=" + compact;
    }

    private static void enableWindowOverrides() throws Exception {
        runChecked("settings", "put", "global", "force_resizable_activities", "1");
        runChecked("settings", "put", "global", "enable_freeform_support", "1");
    }

    private static void setCompactOverlayDensity() throws Exception {
        runChecked("wm", "density", COMPACT_OVERLAY_DENSITY);
        densityChanged = true;
        sleep(1000);
    }

    private static void resetDensity() throws Exception {
        if (densityChanged) {
            runChecked("wm", "density", "reset");
            densityChanged = false;
        }
        sleep(500);
    }

    private static void keepWorkingOut() throws Exception {
        runBestEffort("input", "keyevent", "20");
        sleep(250);
        runBestEffort("input", "keyevent", "66");
        sleep(800);
    }

    private static void focusJustRide() throws Exception {
        String stackList = stackList();
        String task = taskForActivity(stackList, JUST_RIDE_ACTIVITY);
        if (task == null) {
            startJustRide();
            task = taskForActivity(stackList(), JUST_RIDE_ACTIVITY);
        }
        if (task == null) {
            throw new IllegalStateException("Could not find Just Ride task");
        }
        moveTaskToFront(Integer.parseInt(task));
        sleep(1000);
    }

    private static void moveTaskToFront(int taskId) throws Exception {
        Class<?> activityTaskManager = Class.forName("android.app.ActivityTaskManager");
        Object service = activityTaskManager.getMethod("getService").invoke(null);
        Method method = null;
        for (Method candidate : service.getClass().getMethods()) {
            if ("moveTaskToFront".equals(candidate.getName())
                    && candidate.getParameterTypes().length == 5) {
                method = candidate;
                break;
            }
        }
        if (method == null) {
            throw new NoSuchMethodException("moveTaskToFront");
        }
        method.invoke(service, null, "com.android.shell", taskId, 0, null);
    }

    private static void pinJustRideOverlay() throws Exception {
        if (isActivityPinned(JUST_RIDE_ACTIVITY)) {
            return;
        }

        startJustRide();
        sleep(1000);
        if (isActivityPinned(JUST_RIDE_ACTIVITY)) {
            return;
        }

        String stackList = stackList();
        String task = taskForActivity(stackList, JUST_RIDE_ACTIVITY);
        String stack = stackForActivity(stackList, JUST_RIDE_ACTIVITY);
        if (task == null || stack == null) {
            throw new IllegalStateException("Could not find Just Ride task or stack");
        }

        runChecked("am", "task", "resizeable", task, "3");
        runChecked("am", "stack", "move-top-activity-to-pinned-stack", stack, "20", "20", "900", "260");
        sleep(1800);
    }

    private static void startJustRide() throws Exception {
        if (isActivityVisible(JUST_RIDE_ACTIVITY)) {
            return;
        }

        runBestEffort("am", "start", "--windowingMode", "1",
                "-a", "android.intent.action.VIEW",
                "-d", "peloton://activation/justride");
        if (waitForVisibleActivity(JUST_RIDE_ACTIVITY, 8000)) {
            return;
        }

        for (int[] tap : JUST_RIDE_TAPS) {
            runBestEffort("input", "tap", Integer.toString(tap[0]), Integer.toString(tap[1]));
            if (waitForVisibleActivity(JUST_RIDE_ACTIVITY, 4000)) {
                return;
            }
        }

        throw new IllegalStateException("Could not start Just Ride; foreground=" + foregroundActivity());
    }

    private static void bringContentToFront(String target) throws Exception {
        if ("netflix".equals(target)) {
            runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                    "-n", "com.netflix.mediaclient/.ui.launch.NetflixComLaunchActivity");
        } else if ("firefox".equals(target)) {
            runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                    "-n", "org.mozilla.firefox/org.mozilla.fenix.HomeActivity");
        } else if ("disney".equals(target)) {
            runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                    "-n", "com.disney.disneyplus/com.bamtechmedia.dominguez.main.MainActivity");
        } else if ("max".equals(target)) {
            if (packageInstalled("com.wbd.hbomax")) {
                runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                        "-a", "android.intent.action.MAIN",
                        "-c", "android.intent.category.LEANBACK_LAUNCHER",
                        "-n", "com.wbd.hbomax/com.wbd.beam.BeamActivity");
            } else {
                runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                        "-a", "android.intent.action.MAIN",
                        "-c", "android.intent.category.LEANBACK_LAUNCHER",
                        "-n", "com.wbd.stream/com.wbd.beam.BeamActivity");
            }
        } else if ("hulu".equals(target)) {
            runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                    "-n", "com.hulu.plus/com.hulu.features.splash.SplashActivity");
        } else if ("apple-tv".equals(target)) {
            runBestEffort("am", "start", "--windowingMode", "1", "--activity-reorder-to-front",
                    "-n", "com.apple.atve.androidtv.appletv/com.apple.android.tv.MainActivity");
        }
    }

    private static String contentPackage() throws Exception {
        String stackList = stackList();
        String visible = visibleContentPackage(stackList);
        if (visible != null) {
            return visible;
        }
        return firstContentPackage(stackList);
    }

    private static String visibleContentPackage(String stackList) {
        for (String line : stackList.split("\\n")) {
            if (!line.contains("visible=true") || !line.contains("topActivity=ComponentInfo{")) {
                continue;
            }
            String target = targetForLine(line);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private static String firstContentPackage(String stackList) {
        for (String line : stackList.split("\\n")) {
            if (!line.contains("taskId=")) {
                continue;
            }
            String target = targetForLine(line);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private static String targetForLine(String line) {
        if (line.contains("com.netflix.mediaclient/")) {
            return "netflix";
        }
        if (line.contains("org.mozilla.firefox/")) {
            return "firefox";
        }
        if (line.contains("com.disney.disneyplus/")) {
            return "disney";
        }
        if (line.contains("com.wbd.hbomax/") || line.contains("com.wbd.stream/")) {
            return "max";
        }
        if (line.contains("com.hulu.plus/")) {
            return "hulu";
        }
        if (line.contains("com.apple.atve.androidtv.appletv/")) {
            return "apple-tv";
        }
        return null;
    }

    private static boolean isActivityPinned(String activity) throws Exception {
        String stack = stackList();
        boolean pinned = false;
        for (String line : stack.split("\\n")) {
            if (line.startsWith("Stack id=")) {
                pinned = false;
            }
            if (line.contains("mWindowingMode=pinned")) {
                pinned = true;
            }
            if (pinned && line.contains(activity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActivityVisible(String activity) throws Exception {
        String stack = stackList();
        for (String line : stack.split("\\n")) {
            if (line.contains("visible=true") && line.contains(activity)) {
                return true;
            }
        }
        return false;
    }

    private static boolean waitForVisibleActivity(String activity, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isActivityVisible(activity)) {
                return true;
            }
            sleep(WAIT_STEP_MS);
        }
        return false;
    }

    private static String taskForActivity(String stackList, String activity) {
        String fallback = null;
        String visible = null;
        for (String line : stackList.split("\\n")) {
            if (!line.contains("taskId=") || !line.contains(activity)) {
                continue;
            }
            String task = between(line, "taskId=", ":");
            if (task == null) {
                continue;
            }
            if (fallback == null) {
                fallback = task;
            }
            if (line.contains("visible=true")) {
                visible = task;
            }
        }
        return visible != null ? visible : fallback;
    }

    private static String stackForActivity(String stackList, String activity) {
        String currentStack = null;
        String fallback = null;
        String visible = null;
        for (String line : stackList.split("\\n")) {
            if (line.startsWith("Stack id=")) {
                currentStack = between(line, "Stack id=", " ");
            } else if (line.contains(activity)) {
                if (fallback == null) {
                    fallback = currentStack;
                }
                if (line.contains("visible=true")) {
                    visible = currentStack;
                }
            }
        }
        return visible != null ? visible : fallback;
    }

    private static String foregroundActivity() throws Exception {
        String stack = stackList();
        for (String line : stack.split("\\n")) {
            if (line.contains("visible=true") && line.contains("topActivity=ComponentInfo{")) {
                String component = between(line, "topActivity=ComponentInfo{", "}");
                if (component != null) {
                    return component;
                }
            }
        }
        return "unknown";
    }

    private static String stackList() throws Exception {
        return runChecked("am", "stack", "list").stdout;
    }

    private static boolean packageInstalled(String packageName) throws Exception {
        CommandResult result = runBestEffort("pm", "path", packageName);
        return result.exitCode == 0 && result.stdout.contains("package:");
    }

    private static CommandResult runChecked(String... command) throws Exception {
        CommandResult result = run(command);
        if (result.exitCode != 0) {
            throw new IllegalStateException("Command failed " + Arrays.toString(command)
                    + " stdout=" + result.stdout + " stderr=" + result.stderr);
        }
        return result;
    }

    private static CommandResult runBestEffort(String... command) throws Exception {
        return run(command);
    }

    private static CommandResult run(String... command) throws Exception {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.addAll(Arrays.asList(command));
        Process process = new ProcessBuilder(fullCommand).redirectErrorStream(false).start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread outThread = drain(process.getInputStream(), stdout);
        Thread errThread = drain(process.getErrorStream(), stderr);
        int exitCode = process.waitFor();
        outThread.join();
        errThread.join();
        return new CommandResult(
                exitCode,
                new String(stdout.toByteArray(), StandardCharsets.UTF_8),
                new String(stderr.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Thread drain(final InputStream input, final ByteArrayOutputStream output) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[4096];
                int read;
                try {
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                } catch (IOException ignored) {
                }
            }
        }, "saro-window-agent-stream");
        thread.start();
        return thread;
    }

    private static String between(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return null;
        }
        startIndex += start.length();
        int endIndex = text.indexOf(end, startIndex);
        if (endIndex < 0) {
            return null;
        }
        return text.substring(startIndex, endIndex);
    }

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static void log(String message) {
        try (FileWriter writer = new FileWriter(LOG_PATH, true)) {
            writer.write(System.currentTimeMillis() + " " + message + "\n");
        } catch (IOException ignored) {
        }
    }

    private static String stackTrace(Throwable throwable) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(bytes);
        throwable.printStackTrace(writer);
        writer.flush();
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class CommandResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
