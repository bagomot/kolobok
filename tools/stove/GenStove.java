import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Builds the assembled Russian stove: its 13 block models, the blockstate and its textures.
 *
 * <p>Run from the project root: {@code java tools/stove/GenStove.java src/main/resources/assets/kolobok}
 *
 * <p>The whole stove is modelled once, as 1-pixel voxels in one 48×48×32 grid, facing north (its
 * front at z = 0; the onlooker's right is −x). Each part's model is its 16³ cell of that grid: every
 * face between a solid voxel and air becomes a flat element, and coplanar faces of one material are
 * merged into rectangles. Faces between two parts never exist, so the blocks join seamlessly, and
 * textures tile per block because element UVs follow their position.
 *
 * <p>The look follows the songbook prototype: whitewashed, a red painted band under the top, the
 * arched mouth with a sooty hearth, the firebox glowing behind it when lit, firewood stored
 * underneath, two small niches in the front and a chimney with a brick cap over the back right.
 */
public final class GenStove {

    static final int W = 48, H = 48, D = 32;

    // Materials. EMBER is the firebox floor of the lit model; the unlit one shows DARK there.
    static final int AIR = 0, PLASTER = 1, ORNAMENT = 2, SOOT = 3, DARK = 4, BRICK = 5, EMBER = 6;
    static final String[] TEXTURE = {null, "plaster", "ornament", "soot", "dark", "brick", "ember"};

    /** Parts in the order of StovePart, with their cell: column (−1 left … 1 right), row, layer. */
    static final Object[][] PARTS = {
            {"front_left", -1, 0, 0}, {"mouth", 0, 0, 0}, {"front_right", 1, 0, 0},
            {"back_left", -1, 1, 0}, {"back_middle", 0, 1, 0}, {"back_right", 1, 1, 0},
            {"upper_front_left", -1, 0, 1}, {"upper_front_middle", 0, 0, 1}, {"upper_front_right", 1, 0, 1},
            {"upper_back_left", -1, 1, 1}, {"upper_back_middle", 0, 1, 1}, {"upper_back_right", 1, 1, 1},
            {"chimney", 1, 1, 2},
    };

    public static void main(String[] args) throws IOException {
        File assets = new File(args.length > 0 ? args[0] : "src/main/resources/assets/kolobok");
        File models = new File(assets, "models/block/stove");
        File textures = new File(assets, "textures/block");
        models.mkdirs();
        textures.mkdirs();
        new File(assets, "blockstates").mkdirs();

        for (boolean lit : new boolean[]{false, true}) {
            int[][][] grid = build(lit);
            for (Object[] part : PARTS) {
                String name = (String) part[0];
                if (lit && !name.equals("mouth")) {
                    continue;
                }
                int x0 = 16 * (1 - (int) part[1]), z0 = 16 * (int) part[2], y0 = 16 * (int) part[3];
                List<String> elements = cellFaces(grid, x0, y0, z0);
                if (name.equals("mouth")) {
                    elements.addAll(firewood());
                    if (lit) {
                        elements.add(fire());
                    }
                }
                write(new File(models, name + (lit ? "_lit" : "") + ".json"), model(elements));
            }
        }
        write(new File(assets, "blockstates/stove_part.json"), blockstate());

        writeTexture(textures, "stove_plaster", GenStove::plaster);
        writeTexture(textures, "stove_ornament", GenStove::ornament);
        writeTexture(textures, "stove_soot", (x, y, r) -> vary(0x4A3B30, r, 0.10));
        writeTexture(textures, "stove_dark", (x, y, r) -> vary(0x1E0F07, r, 0.25));
        System.out.println("Wrote the stove's models, blockstate and textures to " + assets);
    }

    // ------------------------------------------------------------------------------------ the voxels

    static int[][][] build(boolean lit) {
        int[][][] g = new int[W][H][D];
        // Body, whitewashed over a brick plinth, with the red band near the top.
        fill(g, 0, 0, 0, W, 32, D, PLASTER);
        fill(g, 0, 0, 0, W, 3, D, BRICK);
        for (int x = 0; x < W; x++) {
            for (int y = 27; y < 30; y++) {
                for (int z = 0; z < D; z++) {
                    if (x == 0 || x == W - 1 || z == 0 || z == D - 1) {
                        g[x][y][z] = ORNAMENT;
                    }
                }
            }
        }
        // Under the stove: a dark space for firewood.
        fill(g, 18, 3, 0, 30, 8, 6, AIR);
        line(g, 17, 2, 0, 31, 9, 7, DARK);
        // The mouth: an arched niche with the hearth for its floor, sooty inside and above.
        fill(g, 18, 9, 0, 30, 20, 10, AIR);
        fill(g, 19, 20, 0, 29, 21, 10, AIR);
        fill(g, 21, 21, 0, 27, 22, 10, AIR);
        line(g, 17, 8, 0, 31, 23, 11, SOOT);
        fill(g, 19, 22, 0, 29, 26, 1, SOOT);
        fill(g, 20, 26, 0, 28, 27, 1, SOOT);
        // Deep in the mouth, the firebox.
        fill(g, 21, 9, 10, 27, 16, 14, AIR);
        line(g, 20, 8, 10, 28, 17, 15, DARK);
        if (lit) {
            fill(g, 21, 8, 10, 27, 9, 14, EMBER);
        }
        // Two small niches in the front for drying mittens.
        for (int x0 : new int[]{4, 40}) {
            fill(g, x0, 20, 0, x0 + 4, 24, 3, AIR);
            fill(g, x0, 20, 3, x0 + 4, 24, 4, DARK);
        }
        // The chimney over the back right, with a brick cap and the flue's dark mouth.
        fill(g, 3, 32, 19, 13, 48, 29, PLASTER);
        fill(g, 2, 44, 18, 14, 48, 30, BRICK);
        fill(g, 5, 46, 21, 11, 48, 27, AIR);
        fill(g, 5, 45, 21, 11, 46, 27, DARK);
        return g;
    }

    static void fill(int[][][] g, int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    g[x][y][z] = material;
                }
            }
        }
    }

    /** Recolours the solid voxels of a box: the lining of a hole carved inside it. */
    static void line(int[][][] g, int x0, int y0, int z0, int x1, int y1, int z1, int material) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    if (g[x][y][z] != AIR) {
                        g[x][y][z] = material;
                    }
                }
            }
        }
    }

    static int at(int[][][] g, int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= W || y >= H || z >= D ? AIR : g[x][y][z];
    }

    // ------------------------------------------------------------------------------------ faces

    /** Direction name, normal, and which grid axis is (u, v, depth) on that face. */
    record Dir(String name, int nx, int ny, int nz) {
    }

    static final Dir[] DIRS = {
            new Dir("north", 0, 0, -1), new Dir("south", 0, 0, 1),
            new Dir("west", -1, 0, 0), new Dir("east", 1, 0, 0),
            new Dir("down", 0, -1, 0), new Dir("up", 0, 1, 0),
    };

    /**
     * Every exposed face of the cell's voxels, merged per plane and material into rectangles.
     */
    static List<String> cellFaces(int[][][] g, int x0, int y0, int z0) {
        List<String> out = new ArrayList<>();
        for (Dir d : DIRS) {
            for (int k = 0; k < 16; k++) {
                int[][] mask = new int[16][16];
                for (int a = 0; a < 16; a++) {
                    for (int b = 0; b < 16; b++) {
                        int[] p = point(d, k, a, b);
                        int x = x0 + p[0], y = y0 + p[1], z = z0 + p[2];
                        int m = g[x][y][z];
                        if (m != AIR && at(g, x + d.nx, y + d.ny, z + d.nz) == AIR) {
                            mask[a][b] = m;
                        }
                    }
                }
                // A face on the cell's edge may be culled by a neighbouring block, but only by one
                // outside the stove: across a seam between two parts it is a hole's lining.
                int plane = (d.nx + d.ny + d.nz) < 0 ? k : k + 1;
                boolean outer = (plane == 0 || plane == 16)
                        && !isStoveCell(x0 / 16 + d.nx, y0 / 16 + d.ny, z0 / 16 + d.nz);
                for (int[] r : rectangles(mask)) {
                    out.add(face(d, k, r[0], r[1], r[2], r[3], r[4], outer));
                }
            }
        }
        return out;
    }

    /** Local voxel at depth k and face coordinates (a, b) for a direction. */
    static int[] point(Dir d, int k, int a, int b) {
        if (d.nz != 0) {
            return new int[]{a, b, k};
        }
        if (d.nx != 0) {
            return new int[]{k, a, b};
        }
        return new int[]{a, k, b};
    }

    /** Greedy rectangles of equal material: {a0, b0, a1, b1, material}, ends exclusive. */
    static List<int[]> rectangles(int[][] mask) {
        List<int[]> out = new ArrayList<>();
        boolean[][] used = new boolean[16][16];
        for (int b = 0; b < 16; b++) {
            for (int a = 0; a < 16; a++) {
                int m = mask[a][b];
                if (m == AIR || used[a][b]) {
                    continue;
                }
                int a1 = a;
                while (a1 < 16 && mask[a1][b] == m && !used[a1][b]) {
                    a1++;
                }
                int b1 = b + 1;
                grow:
                while (b1 < 16) {
                    for (int i = a; i < a1; i++) {
                        if (mask[i][b1] != m || used[i][b1]) {
                            break grow;
                        }
                    }
                    b1++;
                }
                for (int i = a; i < a1; i++) {
                    for (int j = b; j < b1; j++) {
                        used[i][j] = true;
                    }
                }
                out.add(new int[]{a, b, a1, b1, m});
            }
        }
        return out;
    }

    static boolean isStoveCell(int cx, int cy, int cz) {
        for (Object[] part : PARTS) {
            if (cx == 1 - (int) part[1] && cz == (int) part[2] && cy == (int) part[3]) {
                return true;
            }
        }
        return false;
    }

    static String face(Dir d, int k, int a0, int b0, int a1, int b1, int material, boolean outer) {
        // The face of voxel layer k lies on its near side for a negative normal, far side otherwise.
        int plane = (d.nx + d.ny + d.nz) < 0 ? k : k + 1;
        int[] from, to;
        if (d.nz != 0) {
            from = new int[]{a0, b0, plane};
            to = new int[]{a1, b1, plane};
        } else if (d.nx != 0) {
            from = new int[]{plane, a0, b0};
            to = new int[]{plane, a1, b1};
        } else {
            from = new int[]{a0, plane, b0};
            to = new int[]{a1, plane, b1};
        }
        String cull = outer ? ", \"cullface\": \"" + d.name + "\"" : "";
        String glow = material == EMBER ? ", \"light_emission\": 15" : "";
        return "{\"from\": " + vec(from) + ", \"to\": " + vec(to) + glow
                + ", \"faces\": {\"" + d.name + "\": {\"texture\": \"#" + TEXTURE[material] + "\"" + cull + "}}}";
    }

    /** Two logs stored under the stove, their ends to the front. */
    static List<String> firewood() {
        List<String> out = new ArrayList<>();
        for (int x : new int[]{3, 8}) {
            out.add("{\"from\": [" + x + ", 3, 1], \"to\": [" + (x + 4) + ", 7, 5], \"faces\": {"
                    + "\"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#log_top\"},"
                    + "\"east\": {\"uv\": [0, 0, 4, 4], \"texture\": \"#log\"},"
                    + "\"west\": {\"uv\": [0, 0, 4, 4], \"texture\": \"#log\"},"
                    + "\"up\": {\"uv\": [0, 0, 4, 4], \"texture\": \"#log\"}}}");
        }
        return out;
    }

    /** The flames in the firebox, facing out of the mouth. */
    static String fire() {
        return "{\"from\": [5, 9, 12.5], \"to\": [11, 16, 12.5], \"light_emission\": 15, \"shade\": false,"
                + " \"faces\": {\"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#fire\"}}}";
    }

    static String vec(int[] v) {
        return "[" + v[0] + ", " + v[1] + ", " + v[2] + "]";
    }

    static String model(List<String> elements) {
        Map<String, String> textures = new LinkedHashMap<>();
        textures.put("particle", "kolobok:block/stove_plaster");
        textures.put("plaster", "kolobok:block/stove_plaster");
        textures.put("ornament", "kolobok:block/stove_ornament");
        textures.put("soot", "kolobok:block/stove_soot");
        textures.put("dark", "kolobok:block/stove_dark");
        textures.put("brick", "minecraft:block/bricks");
        textures.put("ember", "minecraft:block/campfire_log_lit");
        textures.put("fire", "minecraft:block/campfire_fire");
        textures.put("log", "minecraft:block/oak_log");
        textures.put("log_top", "minecraft:block/oak_log_top");
        StringBuilder sb = new StringBuilder("{\n  \"textures\": {\n");
        int i = 0;
        for (Map.Entry<String, String> e : textures.entrySet()) {
            sb.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append('"')
                    .append(++i < textures.size() ? ",\n" : "\n");
        }
        sb.append("  },\n  \"elements\": [\n");
        for (int j = 0; j < elements.size(); j++) {
            sb.append("    ").append(elements.get(j)).append(j + 1 < elements.size() ? ",\n" : "\n");
        }
        return sb.append("  ]\n}\n").toString();
    }

    static String blockstate() {
        String[] facings = {"north", "east", "south", "west"};
        List<String> variants = new ArrayList<>();
        for (String facing : facings) {
            int y = 90 * java.util.Arrays.asList(facings).indexOf(facing);
            for (boolean lit : new boolean[]{false, true}) {
                for (Object[] part : PARTS) {
                    String name = (String) part[0];
                    String model = "kolobok:block/stove/" + name + (lit && name.equals("mouth") ? "_lit" : "");
                    variants.add("    \"facing=" + facing + ",lit=" + lit + ",part=" + name + "\": {\"model\": \""
                            + model + "\"" + (y != 0 ? ", \"y\": " + y : "") + "}");
                }
            }
        }
        return "{\n  \"variants\": {\n" + String.join(",\n", variants) + "\n  }\n}\n";
    }

    static void write(File file, String text) throws IOException {
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------------ textures

    interface Painter {
        int colour(int x, int y, Random random);
    }

    static void writeTexture(File dir, String name, Painter painter) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(name.hashCode());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, 0xFF000000 | painter.colour(x, y, random));
            }
        }
        ImageIO.write(img, "png", new File(dir, name.toLowerCase(Locale.ROOT) + ".png"));
    }

    static final int[] WHITEWASH = {0xEDE7DA, 0xE4DCCB, 0xF2EDE3};
    static final int BRICK_RED = 0x9B4A35;
    static final int PAINT_RED = 0xC0262D;
    /** Where the whitewash has flaked and a brick shows through. */
    static final int[][] FLAKES = {{3, 11}, {4, 11}};

    static int plaster(int x, int y, Random random) {
        for (int[] f : FLAKES) {
            if (f[0] == x && f[1] == y) {
                return BRICK_RED;
            }
        }
        return WHITEWASH[random.nextInt(WHITEWASH.length)];
    }

    /**
     * The painted band. On the stove's sides it covers texture rows 2–4; the pattern is a red
     * check of two-pixel blocks between red lines, and tiles across blocks.
     */
    static int ornament(int x, int y, Random random) {
        boolean red = switch (y) {
            case 2, 4 -> x % 4 < 2;
            case 3 -> (x + 2) % 4 < 2;
            default -> false;
        };
        return red ? vary(PAINT_RED, random, 0.06) : plaster(x, y, random);
    }

    static int vary(int c, Random random, double amount) {
        double k = 1.0 + (random.nextDouble() - 0.5) * amount;
        int r = Math.min(255, (int) Math.round(((c >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) Math.round(((c >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) Math.round((c & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }
}
