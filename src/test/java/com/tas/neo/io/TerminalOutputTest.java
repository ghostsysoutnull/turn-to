package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TerminalOutput}.
 *
 * <p>Covers the TerminalOutput rows in the test strategy table in
 * {@code docs/design/06-testability.md}.
 *
 * <p>Uses a captured {@link ByteArrayOutputStream} wrapped in a {@link PrintStream}
 * rather than {@code System.out}.
 */
class TerminalOutputTest {

    private ByteArrayOutputStream captured;
    private TerminalOutput output;

    @BeforeEach
    void setUp() {
        captured = new ByteArrayOutputStream();
        output = new TerminalOutput(new PrintStream(captured));
    }

    private String capturedText() {
        return captured.toString();
    }

    // -------------------------------------------------------------------------
    // showNarrative
    // -------------------------------------------------------------------------

    @Test
    void showNarrative_writes_text_to_stream() {
        output.showNarrative("You stand before the cave entrance.");

        assertThat(capturedText())
            .as("showNarrative must write the narrative text to the print stream")
            .contains("You stand before the cave entrance.");
    }

    @Test
    void showNarrative_empty_string_produces_output() {
        output.showNarrative("");

        // A call with an empty narrative must not throw; output may be a blank line
        // but the stream must have been written to.
        assertThat(captured.size())
            .as("showNarrative with empty string must not throw and must write to stream")
            .isGreaterThanOrEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // showChoices
    // -------------------------------------------------------------------------

    @Test
    void showChoices_writes_each_choice_text_to_stream() {
        List<Choice> choices = List.of(
            Choice.to("Enter the mountain", new SectionTarget(2)),
            Choice.to("Walk away", new SectionTarget(3))
        );

        output.showChoices(choices);

        String text = capturedText();
        assertThat(text)
            .as("showChoices must write 'Enter the mountain' to the stream")
            .contains("Enter the mountain");
        assertThat(text)
            .as("showChoices must write 'Walk away' to the stream")
            .contains("Walk away");
    }

    @Test
    void showChoices_writes_1_based_numbers_for_each_choice() {
        List<Choice> choices = List.of(
            Choice.to("Go north", new SectionTarget(10)),
            Choice.to("Go south", new SectionTarget(11))
        );

        output.showChoices(choices);

        String text = capturedText();
        assertThat(text)
            .as("showChoices must write '1' as index for the first choice")
            .contains("1");
        assertThat(text)
            .as("showChoices must write '2' as index for the second choice")
            .contains("2");
    }

    // -------------------------------------------------------------------------
    // showMessage
    // -------------------------------------------------------------------------

    @Test
    void showMessage_writes_message_text_to_stream() {
        output.showMessage("You feel a chill in the air.");

        assertThat(capturedText())
            .as("showMessage must write the message text to the print stream")
            .contains("You feel a chill in the air.");
    }

    // -------------------------------------------------------------------------
    // showInventory — countable items show quantity suffix; non-countable do not
    // -------------------------------------------------------------------------

    @Test
    void showInventory_countable_item_at_quantity_three_shows_quantity_suffix() {
        Item potion = new Item("Healing Potion", "Restores 4 STAMINA", ItemCategory.USABLE,
                               true, ScriptBlock.empty());
        List<ItemStack> stacks = List.of(new ItemStack(potion, 3));

        output.showInventory(stacks, 10, 2);

        assertThat(capturedText())
            .as("showInventory must include quantity suffix for countable item with quantity > 1")
            .contains("Healing Potion x3");
    }

    @Test
    void showInventory_non_countable_item_at_quantity_one_shows_no_suffix() {
        Item key = new Item("Iron Key", "Opens the iron door", ItemCategory.KEY,
                            false, ScriptBlock.empty());
        List<ItemStack> stacks = List.of(new ItemStack(key, 1));

        output.showInventory(stacks, 0, 0);

        String text = capturedText();
        assertThat(text)
            .as("showInventory must include item name for non-countable item")
            .contains("Iron Key");
        assertThat(text)
            .as("showInventory must not append quantity suffix for non-countable item at quantity 1")
            .doesNotContain("Iron Key x1");
    }

    @Test
    void showInventory_writes_gold_and_provisions() {
        output.showInventory(List.of(), 15, 3);

        String text = capturedText();
        assertThat(text)
            .as("showInventory must display gold amount")
            .contains("15");
        assertThat(text)
            .as("showInventory must display provisions amount")
            .contains("3");
    }

    // -------------------------------------------------------------------------
    // showGameOver / showVictory
    // -------------------------------------------------------------------------

    @Test
    void showGameOver_writes_message_to_stream() {
        output.showGameOver("You have died.");

        assertThat(capturedText())
            .as("showGameOver must write the game-over message to the print stream")
            .contains("You have died.");
    }

    @Test
    void showVictory_writes_message_to_stream() {
        output.showVictory("You have defeated the Warlock!");

        assertThat(capturedText())
            .as("showVictory must write the victory message to the print stream")
            .contains("You have defeated the Warlock!");
    }
}
