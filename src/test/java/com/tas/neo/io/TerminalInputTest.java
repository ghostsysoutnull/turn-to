package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TerminalInput}.
 *
 * <p>Covers the TerminalInput rows in the test strategy table in
 * {@code docs/design/06-testability.md}:
 * <ul>
 *   <li>readChoice returns the player's 1-based selection</li>
 *   <li>readChoice re-prompts on invalid input (0, out-of-range, non-numeric)</li>
 *   <li>readYesNo parses y/n (case-insensitive)</li>
 *   <li>readYesNo re-prompts on other input</li>
 * </ul>
 *
 * <p>Uses {@link ByteArrayInputStream} for simulated input and a no-op
 * {@link PrintStream} to absorb prompts without polluting any stream.
 */
class TerminalInputTest {

    /** Creates a TerminalInput fed by the given lines of text (each terminated with a newline). */
    private static TerminalInput inputOf(String... lines) {
        String joined = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        InputStream in = new ByteArrayInputStream(joined.getBytes(StandardCharsets.UTF_8));
        // Prompts go to a silent stream — tests must not write to System.out.
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        return new TerminalInput(in, silent);
    }

    private static List<Choice> twoChoices() {
        return List.of(
            Choice.to("Enter the mountain", new SectionTarget(2)),
            Choice.to("Walk away", new SectionTarget(3))
        );
    }

    private static List<Choice> threeChoices() {
        return List.of(
            Choice.to("North", new SectionTarget(10)),
            Choice.to("South", new SectionTarget(11)),
            Choice.to("East",  new SectionTarget(12))
        );
    }

    // -------------------------------------------------------------------------
    // readChoice: valid 1-based selections
    // -------------------------------------------------------------------------

    @Test
    void readChoice_returns_first_choice_when_player_enters_one() {
        TerminalInput input = inputOf("1");

        int choice = input.readChoice(twoChoices());

        assertThat(choice)
            .as("readChoice must return 1 when the player enters '1'")
            .isEqualTo(1);
    }

    @Test
    void readChoice_returns_second_choice_when_player_enters_two() {
        TerminalInput input = inputOf("2");

        int choice = input.readChoice(twoChoices());

        assertThat(choice)
            .as("readChoice must return 2 when the player enters '2'")
            .isEqualTo(2);
    }

    @Test
    void readChoice_returns_last_choice_for_upper_bound() {
        TerminalInput input = inputOf("3");

        int choice = input.readChoice(threeChoices());

        assertThat(choice)
            .as("readChoice must return 3 when the player enters '3' for a 3-choice list")
            .isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // readChoice: re-prompts on invalid input
    // -------------------------------------------------------------------------

    @Test
    void readChoice_reprompts_when_player_enters_zero_then_accepts_valid() {
        // First line is invalid (0), second is valid (1).
        TerminalInput input = inputOf("0", "1");

        int choice = input.readChoice(twoChoices());

        assertThat(choice)
            .as("readChoice must skip '0' and accept the next valid input '1'")
            .isEqualTo(1);
    }

    @Test
    void readChoice_reprompts_when_player_enters_out_of_range_then_accepts_valid() {
        // 99 is out of range for a 2-choice list; 2 is valid.
        TerminalInput input = inputOf("99", "2");

        int choice = input.readChoice(twoChoices());

        assertThat(choice)
            .as("readChoice must skip '99' (out of range) and accept the next valid input '2'")
            .isEqualTo(2);
    }

    @Test
    void readChoice_reprompts_when_player_enters_non_numeric_then_accepts_valid() {
        // "abc" is non-numeric; 1 is valid.
        TerminalInput input = inputOf("abc", "1");

        int choice = input.readChoice(twoChoices());

        assertThat(choice)
            .as("readChoice must skip non-numeric 'abc' and accept the next valid input '1'")
            .isEqualTo(1);
    }

    @Test
    void readChoice_reprompts_multiple_times_before_accepting_valid() {
        // Three invalid inputs followed by one valid.
        TerminalInput input = inputOf("0", "abc", "99", "2");

        int choice = input.readChoice(twoChoices());

        assertThat(choice)
            .as("readChoice must re-prompt through multiple invalid entries before accepting '2'")
            .isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // readYesNo: parses y/n case-insensitively
    // -------------------------------------------------------------------------

    @Test
    void readYesNo_returns_true_for_lowercase_y() {
        TerminalInput input = inputOf("y");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must return true for input 'y'")
            .isTrue();
    }

    @Test
    void readYesNo_returns_true_for_uppercase_Y() {
        TerminalInput input = inputOf("Y");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must return true for input 'Y' (case-insensitive)")
            .isTrue();
    }

    @Test
    void readYesNo_returns_false_for_lowercase_n() {
        TerminalInput input = inputOf("n");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must return false for input 'n'")
            .isFalse();
    }

    @Test
    void readYesNo_returns_false_for_uppercase_N() {
        TerminalInput input = inputOf("N");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must return false for input 'N' (case-insensitive)")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // readYesNo: re-prompts on other input
    // -------------------------------------------------------------------------

    @Test
    void readYesNo_reprompts_when_player_enters_invalid_then_accepts_y() {
        // "maybe" is invalid; "y" is valid.
        TerminalInput input = inputOf("maybe", "y");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must skip 'maybe' and accept the next valid input 'y'")
            .isTrue();
    }

    @Test
    void readYesNo_reprompts_when_player_enters_invalid_then_accepts_n() {
        TerminalInput input = inputOf("yes", "n");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must skip 'yes' (not 'y') and accept the next valid input 'n'")
            .isFalse();
    }

    @Test
    void readYesNo_reprompts_multiple_times_before_accepting_valid() {
        TerminalInput input = inputOf("", "1", "no", "N");

        boolean result = input.readYesNo("Test your luck?");

        assertThat(result)
            .as("readYesNo must re-prompt through multiple invalid entries before accepting 'N'")
            .isFalse();
    }
}
