package org.example.raft.persistence;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import org.example.raft.state.LogEntry;
import org.example.raft.state.Command;

public class LogPersistence {
    private final String logFileName;

    public LogPersistence(String logFileName) {
        this.logFileName = logFileName;
        System.out.println("Init: log file=" + new File(logFileName).getAbsolutePath());
    }

    public String getLogFileName() {
        return logFileName;
    }

    public void saveLog(List<LogEntry> entries) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName))) {
            for (LogEntry entry : entries) {
                // Format: term=1|command=key=value
                writer.write(String.format("term=%d|command=%s%n",
                        entry.term(),
                        entry.command().value()));
            }
            System.out.println(
                    String.format("Save: %d entries to %s", entries.size(), new File(logFileName).getAbsolutePath()));
        } catch (IOException e) {
            System.err.println(String.format("Error: save log %s: %s", logFileName, e.getMessage()));
            e.printStackTrace();
        }
    }

    public void appendLog(LogEntry entry) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFileName, true))) {
            // Format: term=1|command=key=value
            writer.write(String.format("term=%d|command=%s%n",
                    entry.term(),
                    entry.command().value()));
            System.out.println(String.format("Append: entry to %s", new File(logFileName).getAbsolutePath()));
        } catch (IOException e) {
            System.err.println(String.format("Error: append log %s: %s", logFileName, e.getMessage()));
            e.printStackTrace();
        }
    }

    public List<LogEntry> loadLog() {
        List<LogEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Parse line in format: term=1|command=key=value
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    int term = Integer.parseInt(parts[0].substring(5));
                    String command = parts[1].substring(8); // Remove "command="
                    entries.add(new LogEntry(term, new Command(command)));
                }
            }
            System.out.println(
                    String.format("Load: %d entries from %s", entries.size(), new File(logFileName).getAbsolutePath()));
            return entries;
        } catch (IOException e) {
            System.out.println(
                    String.format("Info: no log file %s, starting empty", new File(logFileName).getAbsolutePath()));
            return new ArrayList<>();
        }
    }
}