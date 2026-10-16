import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Draws Kolobok's textures: the 64×64 entity atlas per dough variant and the 16×16 spawn egg.
 *
 * <p>Run from the project root:
 * {@code java tools/textures/GenTextures.java src/main/resources/assets/kolobok/textures}
 *
 * <p>The atlas follows the box layout in {@code KolobokModel}. Each texel is coloured by the height of
 * the model point it lands on, so the "dark top, light bottom" gradient runs continuously across all
 * four boxes of the pixel ball: baked dark on top, pale where he lay on the hearth. The face
 * plates are drawn as seen from the front, on transparency.
 */
public final class GenTextures {

    record Dough(String name, int top, int side, int bottom, int dust, int crumb, long seed) {
    }

    static final Dough[] DOUGHS = {
            new Dough("classic", 0xB8702A, 0xDCA046, 0xF0D08E, 0xF7E6BC, 0x8A4E1C, 1),
            new Dough("borodinsky", 0x3E2414, 0x5A3420, 0x7A4A2C, 0xB08A5A, 0x2A160C, 2),
            new Dough("golden", 0xC8901E, 0xF0C040, 0xFFE48A, 0xFFF6C8, 0xA86E10, 3),
            new Dough("moon", 0x9A96AE, 0xC8C4D8, 0xECE8F4, 0xFFFFFF, 0x7E7A94, 4),
            new Dough("mini", 0xC27A30, 0xE2AA52, 0xF4D89A, 0xF9EAC6, 0x94561E, 5),
    };

    static final int EYE = 0x1C120C;
    static final int SHINE = 0xFFFFFF;
    static final int MOUTH = 0x4A1610;
    static final int TONGUE = 0xC0504A;
    static final int BLUSH = 0xEE8576;
    static final int BROW = 0x6B3A16;
    static final int MOUSTACHE = 0x5A3418;

    /**
     * Face plates, 6×6, as seen from the front. Keys: e eye, h eye shine, m mouth, t tongue,
     * r blush, b brow, u moustache, '.' transparent. Order matches KolobokModel's face indices.
     */
    static final String[][] FACES = {
            { // 0 happy
                    "......",
                    "he..he",
                    "ee..ee",
                    "r....r",
                    ".m..m.",
                    "..mm.."},
            { // 1 blink
                    "......",
                    "......",
                    "ee..ee",
                    "r....r",
                    ".m..m.",
                    "..mm.."},
            { // 2 sing
                    "......",
                    "he..he",
                    "ee..ee",
                    "r....r",
                    "..mm..",
                    "..tt.."},
            { // 3 scared
                    "b....b",
                    "eh..eh",
                    "ee..ee",
                    "......",
                    "..mm..",
                    "..mm.."},
            { // 4 dizzy
                    "......",
                    "e.e..e",
                    ".e..e.",
                    "e.e..e",
                    "..mm..",
                    ".m..m."},
            { // 5 sleep
                    "......",
                    "......",
                    "ee..ee",
                    "r....r",
                    "......",
                    "..mm.."},
            { // 6 sly
                    "bb....",
                    "......",
                    "ee..he",
                    "r...ee",
                    "...mm.",
                    "..m..."},
            { // 7 angry
                    "b....b",
                    ".b..b.",
                    "ee..ee",
                    "......",
                    "..mm..",
                    ".m..m."},
            { // 8 sad
                    ".b..b.",
                    "b....b",
                    "he..he",
                    "ee..ee",
                    "..mm..",
                    ".m..m."},
            { // 9 puff
                    "......",
                    "he..he",
                    "ee..ee",
                    "rr..rr",
                    "rr..rr",
                    "..mm.."},
            { // 10 golova: moustache, eyes open
                    "bb..bb",
                    "he..he",
                    "ee..ee",
                    "......",
                    "uuuuuu",
                    "u.mm.u"},
            { // 11 golova asleep
                    "bb..bb",
                    "......",
                    "ee..ee",
                    "......",
                    "uuuuuu",
                    "u....u"},
    };

    /** Boxes of model «Б»: origin, size and UV, in model pixels (y points down, -z is the front). */
    record Box(int x, int y, int z, int w, int h, int d, int u, int v) {
    }

    static final Box[] BOXES = {
            new Box(-4, -4, -4, 8, 8, 8, 0, 0),
            new Box(-5, -3, -3, 10, 6, 6, 0, 16),
            new Box(-3, -5, -3, 6, 10, 6, 32, 0),
            new Box(-3, -3, -5, 6, 6, 10, 0, 28),
    };

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources/assets/kolobok/textures");
        File entityDir = new File(root, "entity/kolobok");
        File itemDir = new File(root, "item");
        entityDir.mkdirs();
        itemDir.mkdirs();
        for (Dough dough : DOUGHS) {
            ImageIO.write(atlas(dough), "png", new File(entityDir, dough.name() + ".png"));
        }
        ImageIO.write(spawnEgg(DOUGHS[0]), "png", new File(itemDir, "kolobok_spawn_egg.png"));
        ImageIO.write(rollingPin(), "png", new File(itemDir, "rolling_pin.png"));
        ImageIO.write(songbook(), "png", new File(itemDir, "songbook.png"));
        System.out.println("Wrote " + DOUGHS.length + " atlases, the spawn egg, the rolling pin and the songbook to " + root);
    }

    /**
     * Песенник: a red cloth-bound book with a darker spine and gold bands, cream page edges, and
     * a little Kolobok over a gold title line on the cover.
     */
    static BufferedImage songbook() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int[][] colour = new int[16][16];
        int cover = 0xFFA23E28, spine = 0xFF6E2618, band = 0xFFE2B84E, page = 0xFFEFE3C4;
        for (int y = 1; y <= 13; y++) {
            for (int x = 2; x <= 12; x++) {
                boolean onSpine = x <= 3;
                int c = onSpine ? (y == 3 || y == 11 ? band : spine) : cover;
                // Lit from the upper left, like the vanilla icons.
                if (!onSpine && (x == 4 || y == 1)) {
                    c = 0xFF000000 | shade(c, 1.12F);
                } else if (!onSpine && (x == 12 || y == 13)) {
                    c = 0xFF000000 | shade(c, 0.82F);
                }
                colour[y][x] = c;
            }
        }
        // Page edges along the right and the bottom, a shade line between the sheets.
        for (int y = 2; y <= 14; y++) {
            colour[y][13] = y % 3 == 0 ? 0xFF000000 | shade(page, 0.88F) : page;
        }
        for (int x = 3; x <= 13; x++) {
            colour[14][x] = x % 3 == 0 ? 0xFF000000 | shade(page, 0.88F) : page;
        }
        // Kolobok on the cover: a round bun with two eyes and a smile.
        for (int y = 3; y <= 10; y++) {
            for (int x = 4; x <= 11; x++) {
                double dx = x + 0.5 - 8.0, dy = y + 0.5 - 6.5;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 3.2) {
                    int bun = d > 2.4 ? 0xFFC98A2E : dx + dy < -1.2 ? 0xFFF6D07A : 0xFFE9B44C;
                    colour[y][x] = bun;
                }
            }
        }
        colour[6][6] = 0xFF3A2410;
        colour[6][9] = 0xFF3A2410;
        colour[8][7] = 0xFF8A3A1E;
        colour[8][8] = 0xFF8A3A1E;
        for (int x = 6; x <= 11; x++) {
            colour[11][x] = band;
        }
        int outline = 0xFF3B1A10;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (colour[y][x] != 0) {
                    img.setRGB(x, y, colour[y][x]);
                } else if (touches(colour, x, y)) {
                    img.setRGB(x, y, outline);
                }
            }
        }
        return img;
    }

    /**
     * Grandma's rolling pin, lying diagonally like a vanilla sword: a thick turned body and a thin
     * darker handle at each end, lit from the upper left.
     */
    static BufferedImage rollingPin() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int[][] colour = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double px = x + 0.5, py = y + 0.5;
                // Distance across the pin's axis (from (1,15) to (15,1)) and position along it.
                double across = (px + py - 16.0) / Math.sqrt(2.0);
                double along = (px - py) / Math.sqrt(2.0);
                double r = Math.abs(along) <= 6.0 ? 1.9 : Math.abs(along) <= 9.2 ? 0.8 : -1;
                if (Math.abs(across) <= r) {
                    boolean handle = Math.abs(along) > 6.0;
                    // A turned ring where the body meets each handle.
                    boolean ring = Math.abs(along) > 5.3 && !handle;
                    int base = handle ? 0x9C6B3A : ring ? 0xC89A5E : 0xE2BE84;
                    // Lit from the upper left: the side towards it is lighter.
                    double side = across / Math.max(r, 0.01);
                    colour[y][x] = 0xFF000000 | (side < -0.45 ? shade(base, 1.12F) : side > 0.45 ? shade(base, 0.8F) : base);
                }
            }
        }
        int outline = 0xFF5A3A1C;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (colour[y][x] != 0) {
                    img.setRGB(x, y, colour[y][x]);
                } else if (touches(colour, x, y)) {
                    img.setRGB(x, y, outline);
                }
            }
        }
        return img;
    }

    static boolean touches(int[][] colour, int x, int y) {
        int[][] around = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] d : around) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && colour[ny][nx] != 0) {
                return true;
            }
        }
        return false;
    }

    static BufferedImage atlas(Dough dough) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(dough.seed() * 7919L);
        for (Box box : BOXES) {
            paintBox(img, box, dough, random);
        }
        for (int i = 0; i < FACES.length; i++) {
            paintFace(img, 32 + 12 * (i % 2), 16 + 6 * (i / 2), FACES[i]);
        }
        return img;
    }

    /**
     * Paints the six faces of one box. Tops and bottoms take the colour of their height; sides take
     * the colour of each texel row's height.
     */
    static void paintBox(BufferedImage img, Box b, Dough dough, Random random) {
        int u = b.u(), v = b.v(), w = b.w(), h = b.h(), d = b.d();
        float topUp = -b.y();
        float bottomUp = -(b.y() + h);
        // Top (the box's minimum y in model space) and bottom.
        fill(img, u + d, v, w, d, (px, py) -> crust(dough, topUp + 0.5F, true, false, random));
        fill(img, u + d + w, v, w, d, (px, py) -> crust(dough, bottomUp - 0.5F, false, true, random));
        // Four sides, rows top to bottom.
        int[][] sides = {{u, d}, {u + d, w}, {u + d + w, d}, {u + 2 * d + w, w}};
        for (int[] side : sides) {
            fill(img, side[0], v + d, side[1], h, (px, py) -> crust(dough, -(b.y() + py + 0.5F), false, false, random));
        }
    }

    interface Painter {
        int colour(int px, int py);
    }

    static void fill(BufferedImage img, int x0, int y0, int w, int h, Painter painter) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                img.setRGB(x0 + px, y0 + py, 0xFF000000 | painter.colour(px, py));
            }
        }
    }

    /**
     * @param up height of the point above the ball's centre, -5…5 pixels
     */
    static int crust(Dough dough, float up, boolean topFace, boolean bottomFace, Random random) {
        float t = Math.clamp((up + 5.0F) / 10.0F, 0.0F, 1.0F);
        int base;
        if (t < 0.45F) {
            base = mix(dough.bottom(), dough.side(), smooth(t / 0.45F));
        } else {
            base = mix(dough.side(), dough.top(), smooth(Math.min(1.0F, (t - 0.45F) / 0.45F)));
        }
        if (topFace) {
            base = shade(base, 0.92F);
        }
        if (bottomFace) {
            base = mix(base, dough.bottom(), 0.6F);
        }
        float roll = random.nextFloat();
        // Flour dusts the upper half, burnt crumbs sit on the crust.
        if (t > 0.5F && roll < 0.07F) {
            return dough.dust();
        }
        if (t > 0.6F && roll > 0.97F) {
            return dough.crumb();
        }
        return shade(base, 0.96F + random.nextFloat() * 0.08F);
    }

    static void paintFace(BufferedImage img, int x0, int y0, String[] rows) {
        for (int py = 0; py < 6; py++) {
            for (int px = 0; px < 6; px++) {
                int colour = switch (rows[py].charAt(px)) {
                    case 'e' -> EYE;
                    case 'h' -> SHINE;
                    case 'm' -> MOUTH;
                    case 't' -> TONGUE;
                    case 'r' -> BLUSH;
                    case 'b' -> BROW;
                    case 'u' -> MOUSTACHE;
                    default -> -1;
                };
                img.setRGB(x0 + px, y0 + py, colour < 0 ? 0 : 0xFF000000 | colour);
            }
        }
    }

    /** A spawn egg in the shape of the vanilla ones, baked like Kolobok, with his face. */
    static BufferedImage spawnEgg(Dough dough) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        boolean[][] inside = new boolean[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double cy = 8.6, ry = 7.0;
                double ny = (y + 0.5 - cy) / ry;
                // Narrower toward the top, like an egg.
                double rx = 5.9 * (1.0 - 0.18 * Math.max(0.0, -ny));
                double nx = (x + 0.5 - 8.0) / rx;
                inside[y][x] = nx * nx + ny * ny <= 1.0;
            }
        }
        int outline = shade(dough.top(), 0.55F);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!inside[y][x]) {
                    continue;
                }
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15
                        || !inside[y - 1][x] || !inside[y + 1][x] || !inside[y][x - 1] || !inside[y][x + 1];
                int colour;
                if (edge) {
                    colour = outline;
                } else {
                    float up = (7.5F - y) / 1.4F;
                    colour = crust(dough, up, false, false, random);
                    // Light from the upper left.
                    if (x + y < 12) {
                        colour = mix(colour, 0xFFFFFF, 0.12F);
                    }
                }
                img.setRGB(x, y, 0xFF000000 | colour);
            }
        }
        int[][] face = {{5, 8, EYE}, {5, 9, EYE}, {10, 8, EYE}, {10, 9, EYE}, {5, 7, SHINE}, {10, 7, SHINE},
                {4, 10, BLUSH}, {11, 10, BLUSH}, {6, 11, MOUTH}, {9, 11, MOUTH}, {7, 12, MOUTH}, {8, 12, MOUTH}};
        for (int[] p : face) {
            img.setRGB(p[0], p[1], 0xFF000000 | p[2]);
        }
        return img;
    }

    static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    static int shade(int c, float k) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((c & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }
}
