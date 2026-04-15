package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TerminalInput implements GameInput {

    private final BufferedReader reader;
    private final PrintStream prompt;

    public TerminalInput(InputStream in, PrintStream prompt) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.prompt = prompt;
    }

    @Override
    public int readChoice(List<Choice> choices) {
        while (true) {
            prompt.print("> ");
            prompt.flush();
            String line = readLine();
            try {
                int value = Integer.parseInt(line.trim());
                if (value >= 1 && value <= choices.size()) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to re-prompt
            }
        }
    }

    @Override
    public boolean readYesNo(String promptText) {
        while (true) {
            prompt.print(promptText + " (y/n) ");
            prompt.flush();
            String line = readLine().trim().toLowerCase();
            if (line.equals("y")) return true;
            if (line.equals("n")) return false;
        }
    }

    @Override
    public void waitForEnter() {
        prompt.print("Press Enter to continue...");
        prompt.flush();
        readLine();
    }

    private String readLine() {
        try {
            String line = reader.readLine();
            return line == null ? "" : line;
        } catch (Exception e) {
            return "";
        }
    }
}
