package com.bot.constants;

import java.util.function.Consumer;

public final class BotSettings {
    private static volatile boolean diagnosticLogsEnabled = false;
    private static volatile Consumer<String> uiLogSink;

    private BotSettings() {
    }

    public static boolean isDiagnosticLogsEnabled() {
        return diagnosticLogsEnabled;
    }

    public static void setDiagnosticLogsEnabled(boolean enabled) {
        diagnosticLogsEnabled = enabled;
    }

    public static void setUiLogSink(Consumer<String> sink) {
        uiLogSink = sink;
    }

    public static void logToUi(String message) {
        Consumer<String> sink = uiLogSink;
        if (sink != null && message != null && !message.trim().isEmpty()) {
            sink.accept(message);
        }
    }
}
