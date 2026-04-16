package com.tas.neo.domain.location;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DirectionTest {

    @Test
    void north_has_zero_minus_one_zero_delta() {
        assertThat(Direction.NORTH.dx()).isZero();
        assertThat(Direction.NORTH.dy()).isEqualTo(-1);
        assertThat(Direction.NORTH.dz()).isZero();
    }

    @Test
    void south_has_zero_plus_one_zero_delta() {
        assertThat(Direction.SOUTH.dx()).isZero();
        assertThat(Direction.SOUTH.dy()).isEqualTo(1);
        assertThat(Direction.SOUTH.dz()).isZero();
    }

    @Test
    void east_has_plus_one_zero_zero_delta() {
        assertThat(Direction.EAST.dx()).isEqualTo(1);
        assertThat(Direction.EAST.dy()).isZero();
        assertThat(Direction.EAST.dz()).isZero();
    }

    @Test
    void west_has_minus_one_zero_zero_delta() {
        assertThat(Direction.WEST.dx()).isEqualTo(-1);
        assertThat(Direction.WEST.dy()).isZero();
        assertThat(Direction.WEST.dz()).isZero();
    }

    @Test
    void up_has_zero_zero_plus_one_delta() {
        assertThat(Direction.UP.dx()).isZero();
        assertThat(Direction.UP.dy()).isZero();
        assertThat(Direction.UP.dz()).isEqualTo(1);
    }

    @Test
    void down_has_zero_zero_minus_one_delta() {
        assertThat(Direction.DOWN.dx()).isZero();
        assertThat(Direction.DOWN.dy()).isZero();
        assertThat(Direction.DOWN.dz()).isEqualTo(-1);
    }

    @Test
    void opposite_inverts_north_south() {
        assertThat(Direction.NORTH.opposite()).isEqualTo(Direction.SOUTH);
        assertThat(Direction.SOUTH.opposite()).isEqualTo(Direction.NORTH);
    }

    @Test
    void opposite_inverts_east_west() {
        assertThat(Direction.EAST.opposite()).isEqualTo(Direction.WEST);
        assertThat(Direction.WEST.opposite()).isEqualTo(Direction.EAST);
    }

    @Test
    void opposite_inverts_up_down() {
        assertThat(Direction.UP.opposite()).isEqualTo(Direction.DOWN);
        assertThat(Direction.DOWN.opposite()).isEqualTo(Direction.UP);
    }

    @Test
    void defaultLabel_returns_human_readable_string_for_each_direction() {
        assertThat(Direction.NORTH.defaultLabel())
            .as("NORTH default label must be 'Go north'")
            .isEqualTo("Go north");
        assertThat(Direction.SOUTH.defaultLabel())
            .as("SOUTH default label must be 'Go south'")
            .isEqualTo("Go south");
        assertThat(Direction.EAST.defaultLabel())
            .as("EAST default label must be 'Go east'")
            .isEqualTo("Go east");
        assertThat(Direction.WEST.defaultLabel())
            .as("WEST default label must be 'Go west'")
            .isEqualTo("Go west");
        assertThat(Direction.UP.defaultLabel())
            .as("UP default label must be 'Go up'")
            .isEqualTo("Go up");
        assertThat(Direction.DOWN.defaultLabel())
            .as("DOWN default label must be 'Go down'")
            .isEqualTo("Go down");
    }
}
