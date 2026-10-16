package ru.bagomot.kolobok.block;

import net.minecraft.util.StringRepresentable;

/**
 * The thirteen blocks of the Russian stove, as seen by someone standing in front of it.
 *
 * <pre>
 * layer 0:  back  [■][■][■]     layer 1:  [■][■][■]     layer 2:  [ ][ ][T]
 *           front [■][M][■]               [■][■][■]
 * </pre>
 *
 * <p>M is the furnace, which becomes the mouth and keeps the stove's block entity; ■ and T are
 * bricks. T, above the back right corner, becomes the chimney. Left and right are the onlooker's.
 * Each part knows its cell: column -1 left, 0 middle, 1 right; row 0 front, 1 back; layer 0 bottom,
 * 1 top, 2 the chimney.
 */
public enum StovePart implements StringRepresentable {
    FRONT_LEFT("front_left", -1, 0, 0),
    MOUTH("mouth", 0, 0, 0),
    FRONT_RIGHT("front_right", 1, 0, 0),
    BACK_LEFT("back_left", -1, 1, 0),
    BACK_MIDDLE("back_middle", 0, 1, 0),
    BACK_RIGHT("back_right", 1, 1, 0),
    UPPER_FRONT_LEFT("upper_front_left", -1, 0, 1),
    UPPER_FRONT_MIDDLE("upper_front_middle", 0, 0, 1),
    UPPER_FRONT_RIGHT("upper_front_right", 1, 0, 1),
    UPPER_BACK_LEFT("upper_back_left", -1, 1, 1),
    UPPER_BACK_MIDDLE("upper_back_middle", 0, 1, 1),
    UPPER_BACK_RIGHT("upper_back_right", 1, 1, 1),
    CHIMNEY("chimney", 1, 1, 2);

    private final String name;
    private final int column;
    private final int row;
    private final int layer;

    StovePart(String name, int column, int row, int layer) {
        this.name = name;
        this.column = column;
        this.row = row;
        this.layer = layer;
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    public int layer() {
        return layer;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
