import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws Kolobok's outfits.
 *
 * <p>Run from the project root:
 * {@code java tools/outfits/GenOutfits.java src/main/resources/assets/kolobok}
 *
 * <p>Two kinds of thing come out of it:
 * <ul>
 *   <li>Textures for the vanilla items he wears as something else — the шелом of each metal, the
 *   ушанка, the лапти of each material and the sunglasses — in {@code textures/entity/kolobok/outfit/}.
 *   They follow the box layouts in {@code OutfitModels}; each box face is coloured by what it is.</li>
 *   <li>His own hats, the crown, the wreath and the kokoshnik: three-dimensional item models made of
 *   elements, their palette textures and item definitions. A player wears the same models: the game
 *   draws a head item without an equipment asset as its item model in the {@code head} context.</li>
 * </ul>
 *
 * <p>The hats are designed in a player's head pixels, the head filling 4…12 on every axis with its
 * top at y = 12 and its face to the north. Each model is then moved so that its middle is the middle
 * of the item, which keeps it centred in the inventory, and {@code display.head} moves it back onto
 * the head: the game shows a whole item model on a head at 10/16 of a block, so a scale of 1.6 makes
 * one unit one pixel of the head.
 */
public final class GenOutfits {

    record Metal(String name, int a, int b, int band) {
    }

    static final Metal[] METALS = {
            new Metal("copper", 0xE38A5E, 0xC46A3F, 0x8C4428),
            new Metal("chainmail", 0xA3ADB7, 0x7F8A95, 0x5F6973),
            new Metal("iron", 0xD3D8DE, 0xB4BCC5, 0x7E8791),
            new Metal("gold", 0xF4C531, 0xD9A520, 0xA8780C),
            new Metal("diamond", 0x6FEFF6, 0x3CC9D3, 0x1E7F87),
            new Metal("netherite", 0x544B50, 0x3E373A, 0x7A6870),
    };
    /** Undyed leather boots are лапти of plain bast. */
    static final Metal BAST = new Metal("leather", 0xC8A15E, 0xA57D3E, 0x7A4E25);
    /** Dyed ones are drawn in grey and tinted with the dye. */
    static final Metal GREY = new Metal("dyed", 0xE6E6E6, 0xBDBDBD, 0x8C8C8C);

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/kolobok");
        Path outfit = root.resolve("textures/entity/kolobok/outfit");
        Files.createDirectories(outfit);
        for (Metal metal : METALS) {
            write(helmet(metal), outfit.resolve("helmet_" + metal.name() + ".png"));
            write(lapti(metal), outfit.resolve("lapti_" + metal.name() + ".png"));
        }
        write(lapti(BAST), outfit.resolve("lapti_leather.png"));
        write(lapti(GREY), outfit.resolve("lapti_dyed.png"));
        write(ushanka(), outfit.resolve("ushanka.png"));
        write(glasses(), outfit.resolve("glasses.png"));

        Files.createDirectories(root.resolve("models/item"));
        Files.createDirectories(root.resolve("textures/item"));
        Files.createDirectories(root.resolve("items"));
        crown(root);
        wreath(root);
        kokoshnik(root);
        System.out.println("Wrote the outfits for Kolobok and the crown, wreath and kokoshnik to " + root);
    }

    // =========================================================================== entity outfit textures

    interface Painter {
        /**
         * @param face one of top, bottom, west, north, east, south (top as seen, the model's min y)
         * @param x    texel across the face
         * @param y    texel down the face; on the sides, rows go from the top of the box down
         */
        int colour(String face, int x, int y, int w, int h);
    }

    /**
     * Paints one box at its texture offset, the way {@code ModelPart.Cube} lays the faces out.
     */
    static void box(BufferedImage img, int u, int v, int w, int h, int d, Painter p) {
        face(img, u + d, v, w, d, "top", p);
        face(img, u + d + w, v, w, d, "bottom", p);
        face(img, u, v + d, d, h, "west", p);
        face(img, u + d, v + d, w, h, "north", p);
        face(img, u + d + w, v + d, d, h, "east", p);
        face(img, u + d + w + d, v + d, w, h, "south", p);
    }

    static void face(BufferedImage img, int x0, int y0, int w, int h, String face, Painter p) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = p.colour(face, x, y, w, h);
                img.setRGB(x0 + x, y0 + y, c == 0 ? 0 : 0xFF000000 | c);
            }
        }
    }

    /**
     * The шелом: a band with rivets, a dome of hammered plates lighter towards the top, a spike, a nose
     * guard, and the бармица in rings (drawn for every metal; only the chainmail one shows it).
     */
    static BufferedImage helmet(Metal m) {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int rivet = shade(m.a(), 1.15F);
        box(img, 0, 0, 10, 1, 10, (f, x, y, w, h) -> switch (f) {
            case "top" -> plate(m, x, y, 0, 0.95F);
            case "bottom" -> shade(m.band(), 0.7F);
            default -> x % 3 == 1 ? rivet : m.band();
        });
        // Four tiers of the dome, each a little lighter: light from above.
        int[][] tiers = {{0, 11, 9, 2}, {0, 22, 7, 2}, {28, 22, 5, 2}, {48, 22, 3, 1}};
        for (int i = 0; i < tiers.length; i++) {
            int[] t = tiers[i];
            float light = 0.92F + i * 0.06F;
            int seed = i + 1;
            box(img, t[0], t[1], t[2], t[3], t[2], (f, x, y, w, h) -> switch (f) {
                case "bottom" -> shade(m.b(), 0.75F);
                case "top" -> plate(m, x, y, seed, light + 0.06F);
                default -> plate(m, x, y, seed + 10, light - y * 0.08F);
            });
        }
        box(img, 40, 0, 1, 2, 1, (f, x, y, w, h) -> shade(m.a(), 1.1F));
        box(img, 44, 0, 1, 2, 1, (f, x, y, w, h) -> m.band());
        Painter rings = (f, x, y, w, h) -> ((x + y) & 1) == 0 ? m.a() : shade(m.b(), 0.85F);
        box(img, 0, 32, 11, 4, 1, rings);
        box(img, 0, 37, 1, 4, 8, rings);
        box(img, 18, 37, 1, 4, 8, rings);
        return img;
    }

    static int plate(Metal m, int x, int y, int seed, float light) {
        return shade(hash(x, y, seed) < 0.5F ? m.a() : m.b(), light);
    }

    /**
     * The ушанка in greys for the dye to tint: a darker fur cap, the turned-up band and flaps of paler
     * sheepskin, and a golden wheat ear, which is drawn untinted.
     */
    static BufferedImage ushanka() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[] cap = {0xD2D2D2, 0xC2C2C2, 0xB4B4B4};
        int[] skin = {0xF6F6F6, 0xEAEAEA};
        Painter fur = (f, x, y, w, h) -> cap[(int) (hash(x, y, w * 7 + h) * cap.length)];
        Painter sheepskin = (f, x, y, w, h) -> f.equals("bottom") ? 0xCFCFCF : skin[(int) (hash(x, y, w * 5 + h) * skin.length)];
        box(img, 0, 0, 11, 2, 11, sheepskin);
        box(img, 0, 13, 10, 2, 10, fur);
        box(img, 0, 25, 8, 1, 8, fur);
        box(img, 32, 25, 5, 1, 5, fur);
        box(img, 44, 0, 1, 6, 5, sheepskin);
        box(img, 44, 13, 1, 6, 5, sheepskin);
        box(img, 0, 34, 9, 3, 1, sheepskin);
        // The badge, 2×3: a wheat ear on its stalk.
        String[] ear = {"lg", "gd", ".s"};
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 2; x++) {
                int c = switch (ear[y].charAt(x)) {
                    case 'l' -> 0xFFE58A;
                    case 'g' -> 0xF0C040;
                    case 'd' -> 0xC8901E;
                    case 's' -> 0x9A6A1E;
                    default -> -1;
                };
                img.setRGB(20 + x, 34 + y, c < 0 ? 0 : 0xFF000000 | c);
                img.setRGB(22 + x, 34 + y, c < 0 ? 0 : 0xFF000000 | c);
            }
        }
        return img;
    }

    /**
     * A лапоть: woven strips in a checker, a darker binding along the top edge, the inside dark where
     * the foot would go.
     */
    static BufferedImage lapti(Metal m) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Painter weave = (f, x, y, w, h) -> switch (f) {
            case "bottom" -> shade(m.b(), 0.7F);
            default -> ((x + y) & 1) == 0 ? m.a() : m.b();
        };
        Painter upper = (f, x, y, w, h) -> f.equals("top") ? (((x + y) & 1) == 0 ? shade(m.a(), 1.05F) : m.b())
                : weave.colour(f, x, y, w, h);
        box(img, 0, 0, 4, 1, 8, (f, x, y, w, h) -> f.equals("top") ? shade(m.band(), 0.6F) : weave.colour(f, x, y, w, h));
        box(img, 0, 9, 4, 1, 5, upper);
        box(img, 0, 16, 3, 1, 1, upper);
        box(img, 0, 19, 4, 1, 1, (f, x, y, w, h) -> m.band());
        return img;
    }

    /**
     * Sunglasses, 8×2 in front of his face: two dark lenses with a glint, a bridge between them along
     * the top, the frame's ends at the sides.
     */
    static BufferedImage glasses() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        String[] rows = {"fLlbbLLf", ".LL..LL."};
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 8; x++) {
                int c = switch (rows[y].charAt(x)) {
                    case 'f', 'b' -> 0x262626;
                    case 'L' -> 0x121212;
                    case 'l' -> 0xD8E4EA;
                    default -> -1;
                };
                int argb = c < 0 ? 0 : 0xFF000000 | c;
                img.setRGB(x, y, argb);
                img.setRGB(8 + 7 - x, y, argb);
            }
        }
        return img;
    }

    // =========================================================================== hats as item models

    /** One element of an item model; every face takes one cell of the palette texture. */
    record Element(double[] from, double[] to, Map<String, Cell> faces) {
    }

    /** A rectangle of the texture, and whether the dye tints it. */
    record Cell(int u, int v, int w, int h, boolean tinted) {
        Cell(int u, int v) {
            this(u, v, 1, 1, false);
        }

        Cell tint() {
            return new Cell(u, v, w, h, true);
        }
    }

    static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    static Element cube(double x0, double y0, double z0, double x1, double y1, double z1, Cell all) {
        Map<String, Cell> faces = new LinkedHashMap<>();
        for (String f : FACES) {
            faces.put(f, all);
        }
        return new Element(new double[]{x0, y0, z0}, new double[]{x1, y1, z1}, faces);
    }

    static Element cube(double x0, double y0, double z0, double x1, double y1, double z1, Cell sides, Cell up, Cell down) {
        Element e = cube(x0, y0, z0, x1, y1, z1, sides);
        e.faces().put("up", up);
        e.faces().put("down", down);
        return e;
    }

    /**
     * Корона Царь-Колобка. A gold band set with a ruby in front, sapphires at the sides and an emerald
     * behind; above it eight teeth: tall pointed ones in the middle of each side, each with a pearl,
     * and lower ones at the corners. The teeth are what make it a crown and not a wreath.
     */
    static void crown(Path root) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int[] band = {0xFFE58A, 0xF4C531, 0xE0AE22, 0xA8780C};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 2; x++) {
                img.setRGB(x, y, 0xFF000000 | band[y]);
            }
        }
        int[] cells = {0xFFE07A, 0xF4C531, 0xD9A520, 0x8A6208, 0xD6263B, 0x2D6BD8, 0x1FA85A, 0xFFF4D6, 0xE8D4A8};
        for (int i = 0; i < cells.length; i++) {
            img.setRGB(2 + i, 0, 0xFF000000 | cells[i]);
        }
        Cell bandSide = new Cell(0, 0, 2, 4, false);
        Cell light = new Cell(2, 0), gold = new Cell(3, 0), dark = new Cell(4, 0), inner = new Cell(5, 0);
        Cell ruby = new Cell(6, 0), sapphire = new Cell(7, 0), emerald = new Cell(8, 0), pearl = new Cell(9, 0);

        List<Element> e = new ArrayList<>();
        double lo = 3.25, hi = 12.75, t = 0.5, y0 = 10.5, y1 = 13.0;
        // The band, four walls round the head; gold inside, darker.
        e.add(withInside(cube(lo, y0, lo, hi, y1, lo + t, bandSide, light, dark), "south", inner));
        e.add(withInside(cube(lo, y0, hi - t, hi, y1, hi, bandSide, light, dark), "north", inner));
        e.add(withInside(cube(lo, y0, lo + t, lo + t, y1, hi - t, bandSide, light, dark), "east", inner));
        e.add(withInside(cube(hi - t, y0, lo + t, hi, y1, hi - t, bandSide, light, dark), "west", inner));
        // Stones in the middle of each side, standing a little proud of the band.
        e.add(cube(7.25, 11.0, lo - 0.3, 8.75, 12.5, lo, ruby));
        e.add(cube(7.25, 11.0, hi, 8.75, 12.5, hi + 0.3, emerald));
        e.add(cube(lo - 0.3, 11.0, 7.25, lo, 12.5, 8.75, sapphire));
        e.add(cube(hi, 11.0, 7.25, hi + 0.3, 12.5, 8.75, sapphire));
        // Tall teeth in the middle of each side: three steps narrowing to a point, a pearl on top.
        double[][] steps = {{1.3, y1, 14.5}, {0.8, 14.5, 15.75}, {0.4, 15.75, 16.5}};
        for (int side = 0; side < 4; side++) {
            for (double[] s : steps) {
                e.add(side(side, 8.0 - s[0], 8.0 + s[0], s[1], s[2], lo, hi, t, gold, light));
            }
            e.add(side(side, 7.45, 8.55, 16.5, 17.6, lo - 0.05, hi + 0.05, t + 0.1, pearl, pearl));
        }
        // Lower teeth on the corners, square posts with a smaller pearl.
        for (double x : new double[]{lo, hi - 1.5}) {
            for (double z : new double[]{lo, hi - 1.5}) {
                e.add(cube(x, y1, z, x + 1.5, 14.25, z + 1.5, gold, light, dark));
                e.add(cube(x + 0.3, 14.25, z + 0.3, x + 1.2, 15.0, z + 1.2, gold, light, dark));
                e.add(cube(x + 0.35, 15.0, z + 0.35, x + 1.15, 15.8, z + 1.15, pearl));
            }
        }
        write(img, root.resolve("textures/item/crown.png"));
        writeModel(root, "crown", "crown", e);
        writeItem(root, "crown", "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"kolobok:item/crown\"\n  }\n}\n");
    }

    /** A wall of the band seen from inside the ring: that face is the darker inside gold. */
    static Element withInside(Element e, String face, Cell inside) {
        e.faces().put(face, inside);
        return e;
    }

    /**
     * A piece standing on one side of the band: across the side from {@code a} to {@code b}, from
     * {@code y0} to {@code y1}, as thick as the band.
     *
     * @param side 0 north, 1 south, 2 west, 3 east
     */
    static Element side(int side, double a, double b, double y0, double y1, double lo, double hi, double t, Cell sides, Cell up) {
        return switch (side) {
            case 0 -> cube(a, y0, lo, b, y1, lo + t, sides, up, sides);
            case 1 -> cube(a, y0, hi - t, b, y1, hi, sides, up, sides);
            case 2 -> cube(lo, y0, a, lo + t, y1, b, sides, up, sides);
            default -> cube(hi - t, y0, a, hi, y1, b, sides, up, sides);
        };
    }

    /**
     * Венок: a ring of leaves round the forehead with eight flowers in it, of every colour; dyed, the
     * petals all take the dye's colour (the {@code _dyed} model, tinted), the leaves stay green.
     */
    static void wreath(Path root) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int[] greens = {0x3E8E2E, 0x2F6E24, 0x4FA03A, 0x367F28};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                img.setRGB(x, y, 0xFF000000 | greens[(int) (hash(x, y, 3) * greens.length)]);
            }
        }
        int[] petals = {0xD7262B, 0xF5D21F, 0x3C6EDB, 0xF4F1EA, 0xE46AA8, 0x8A4FD0, 0xF08A1D, 0xF5D21F};
        for (int i = 0; i < petals.length; i++) {
            img.setRGB(4 + i, 0, 0xFF000000 | petals[i]);
        }
        img.setRGB(4, 1, 0xFF3A2410);
        img.setRGB(5, 1, 0xFFE8B030);
        img.setRGB(6, 1, 0xFFEDEDED);
        Cell leaves = new Cell(0, 0, 4, 4, false);
        Cell dark = new Cell(4, 1), yellow = new Cell(5, 1), white = new Cell(6, 1);

        for (boolean dyed : new boolean[]{false, true}) {
            List<Element> e = new ArrayList<>();
            double lo = 3.0, hi = 13.0, t = 1.0, y0 = 9.5, y1 = 11.0;
            e.add(cube(lo, y0, lo, hi, y1, lo + t, leaves));
            e.add(cube(lo, y0, hi - t, hi, y1, hi, leaves));
            e.add(cube(lo, y0, lo + t, lo + t, y1, hi - t, leaves));
            e.add(cube(hi - t, y0, lo + t, hi, y1, hi - t, leaves));
            // Tufts of leaves sticking up between the flowers.
            for (double[] tuft : new double[][]{{5.6, lo}, {10.4, lo}, {5.6, hi - t}, {10.4, hi - t}}) {
                e.add(cube(tuft[0] - 0.5, y1, tuft[1] + 0.25, tuft[0] + 0.5, y1 + 0.75, tuft[1] + 0.75, leaves));
            }
            // Flowers: two on the front, two on the back, two on each side, each a blossom of petals
            // standing out of the ring with its heart in front.
            double[][] spots = {{4.3, 0}, {8.0, 0}, {11.7, 0}, {6.0, 1}, {10.0, 1}, {6.2, 2}, {9.8, 3}, {8.0, 3}};
            for (int i = 0; i < spots.length; i++) {
                Cell petal = dyed ? white.tint() : new Cell(4 + i, 0);
                // A yellow heart, but a dark one in pale petals, where yellow would not show.
                boolean pale = !dyed && (petals[i] == 0xF5D21F || petals[i] == 0xF4F1EA || petals[i] == 0xF08A1D);
                Cell heart = dyed || pale ? dark : yellow;
                double c = spots[i][0];
                int s = (int) spots[i][1];
                e.add(flowerPart(s, c - 1.0, c + 1.0, 9.25, 11.25, lo, hi, -0.4, petal));
                e.add(flowerPart(s, c - 0.4, c + 0.4, 9.85, 10.65, lo, hi, -0.6, heart));
            }
            writeModel(root, dyed ? "wreath_dyed" : "wreath", "wreath", e);
        }
        write(img, root.resolve("textures/item/wreath.png"));
        writeItem(root, "wreath", """
                {
                  "model": {
                    "type": "minecraft:condition",
                    "property": "minecraft:has_component",
                    "component": "minecraft:dyed_color",
                    "on_false": {
                      "type": "minecraft:model",
                      "model": "kolobok:item/wreath"
                    },
                    "on_true": {
                      "type": "minecraft:model",
                      "model": "kolobok:item/wreath_dyed",
                      "tints": [
                        {
                          "type": "minecraft:dye",
                          "default": -1
                        }
                      ]
                    }
                  }
                }
                """);
    }

    /**
     * Part of a flower on the outside of the ring.
     *
     * @param side  0 north, 1 south, 2 west, 3 east
     * @param depth how far it stands out of the ring's outer face (negative: outwards)
     */
    static Element flowerPart(int side, double a, double b, double y0, double y1, double lo, double hi, double depth, Cell cell) {
        double out = -depth;
        return switch (side) {
            case 0 -> cube(a, y0, lo - out, b, y1, lo + 0.3, cell);
            case 1 -> cube(a, y0, hi - 0.3, b, y1, hi + out, cell);
            case 2 -> cube(lo - out, y0, a, lo + 0.3, y1, b, cell);
            default -> cube(hi - 0.3, y0, a, hi + out, y1, b, cell);
        };
    }

    /**
     * Кокошник: a tall rounded shield over the forehead on a gold headband, red unless dyed, edged with
     * gold and pearls, with a sapphire in a gold setting in the middle and a row of gold dots.
     */
    static void kokoshnik(Path root) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int[] cloth = {0xF2F2F2, 0xDCDCDC};
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                img.setRGB(x, y, 0xFF000000 | cloth[(x + y) & 1]);
            }
        }
        int[] cells = {0xE9B627, 0xB58A12, 0xFFDD70, 0xF4EFE3, 0xD8CDB8, 0x2D6BD8, 0x6FA0F0, 0xC8C8C8};
        for (int i = 0; i < cells.length; i++) {
            img.setRGB(2 + i, 0, 0xFF000000 | cells[i]);
        }
        Cell red = new Cell(0, 0, 2, 2, true), redBack = new Cell(9, 0).tint();
        Cell gold = new Cell(2, 0), goldDark = new Cell(3, 0), goldLight = new Cell(4, 0);
        Cell pearl = new Cell(5, 0), sapphire = new Cell(7, 0);

        List<Element> e = new ArrayList<>();
        double lo = 3.25, hi = 12.75, t = 0.5, y0 = 10.0, y1 = 11.5;
        e.add(cube(lo, y0, lo, hi, y1, lo + t, gold, goldLight, goldDark));
        e.add(cube(lo, y0, hi - t, hi, y1, hi, gold, goldLight, goldDark));
        e.add(cube(lo, y0, lo + t, lo + t, y1, hi - t, gold, goldLight, goldDark));
        e.add(cube(hi - t, y0, lo + t, hi, y1, hi - t, gold, goldLight, goldDark));
        // The shield: a gold arch behind, the red one in front of it a little smaller, so a gold edge
        // shows all round.
        double[][] arch = {{5.5, 10.0, 12.5}, {5.0, 12.5, 14.0}, {4.0, 14.0, 15.25}, {2.5, 15.25, 16.25}, {1.0, 16.25, 16.85}};
        double zf = 2.75, zm = 3.25, zb = 3.75;
        for (double[] a : arch) {
            e.add(cube(8.0 - a[0] - 0.35, a[1], zm, 8.0 + a[0] + 0.35, a[2] + 0.35, zb, gold, goldLight, goldDark));
            Element front = cube(8.0 - a[0], a[1], zf, 8.0 + a[0], a[2], zm, red);
            front.faces().put("south", redBack);
            e.add(front);
        }
        // Pearls along the edge of the arch.
        double[][] pearls = {{2.8, 12.6}, {3.35, 14.15}, {4.4, 15.35}, {5.9, 16.3}, {8.0, 16.95}, {10.1, 16.3}, {11.6, 15.35},
                {12.65, 14.15}, {13.2, 12.6}};
        for (double[] p : pearls) {
            e.add(cube(p[0] - 0.4, p[1] - 0.4, zf - 0.3, p[0] + 0.4, p[1] + 0.4, zf + 0.2, pearl));
        }
        // The sapphire in its setting, and gold dots along the bottom of the shield.
        e.add(cube(6.9, 12.4, zf - 0.2, 9.1, 14.6, zf, gold, goldLight, goldDark));
        e.add(cube(7.3, 12.8, zf - 0.45, 8.7, 14.2, zf - 0.2, sapphire));
        for (double x = 3.6; x <= 12.5; x += 1.6) {
            if (Math.abs(x - 8.0) < 1.5) {
                continue;
            }
            e.add(cube(x - 0.3, 11.0, zf - 0.2, x + 0.3, 11.6, zf, gold));
        }
        write(img, root.resolve("textures/item/kokoshnik.png"));
        writeModel(root, "kokoshnik", "kokoshnik", e);
        // The dye's default is the kokoshnik's own red.
        writeItem(root, "kokoshnik", """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "kolobok:item/kokoshnik",
                    "tints": [
                      {
                        "type": "minecraft:dye",
                        "default": %d
                      }
                    ]
                  }
                }
                """.formatted(0xFFB02E26));
    }

    /**
     * Writes the model, moved so that its middle is the item's middle, with the display transforms:
     * on a head moved back up onto it at a pixel a unit, in the inventory a little from above and from
     * the front-left.
     */
    static void writeModel(Path root, String name, String texture, List<Element> elements) throws IOException {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Element e : elements) {
            for (int i = 0; i < 3; i++) {
                min[i] = Math.min(min[i], e.from()[i]);
                max[i] = Math.max(max[i], e.to()[i]);
            }
        }
        double[] shift = new double[3];
        for (int i = 0; i < 3; i++) {
            shift[i] = 8.0 - (min[i] + max[i]) / 2.0;
        }
        double size = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2]));
        double gui = round(Math.min(1.6, 12.5 / size));
        // On a head: back up by the shift, in pixels, at 10/16 of a block per item.
        double headUp = round(-shift[1] / 0.625);
        double headBack = round(-shift[2] / 0.625);
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"gui_light\": \"side\",\n");
        out.append("  \"textures\": {\n");
        out.append("    \"hat\": \"kolobok:item/").append(texture).append("\",\n");
        out.append("    \"particle\": \"kolobok:item/").append(texture).append("\"\n");
        out.append("  },\n");
        out.append("  \"display\": {\n");
        out.append(display("head", new double[]{0, 0, 0}, new double[]{0, headUp, headBack}, 1.6)).append(",\n");
        out.append(display("gui", new double[]{25, 155, 0}, new double[]{0, 0, 0}, gui)).append(",\n");
        out.append(display("ground", new double[]{0, 0, 0}, new double[]{0, 2, 0}, 0.5)).append(",\n");
        out.append(display("fixed", new double[]{0, 180, 0}, new double[]{0, 0, 0}, gui)).append(",\n");
        out.append(display("thirdperson_righthand", new double[]{75, 45, 0}, new double[]{0, 2.5, 0}, 0.4)).append(",\n");
        out.append(display("thirdperson_lefthand", new double[]{75, 45, 0}, new double[]{0, 2.5, 0}, 0.4)).append(",\n");
        out.append(display("firstperson_righthand", new double[]{0, 135, 0}, new double[]{0, 2, 0}, 0.4)).append(",\n");
        out.append(display("firstperson_lefthand", new double[]{0, 135, 0}, new double[]{0, 2, 0}, 0.4)).append("\n");
        out.append("  },\n");
        out.append("  \"elements\": [\n");
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            out.append("    {\n");
            out.append("      \"from\": ").append(vec(e.from(), shift)).append(",\n");
            out.append("      \"to\": ").append(vec(e.to(), shift)).append(",\n");
            out.append("      \"faces\": {\n");
            int k = 0;
            for (Map.Entry<String, Cell> f : e.faces().entrySet()) {
                Cell c = f.getValue();
                out.append("        \"").append(f.getKey()).append("\": {\"uv\": [")
                        .append(c.u()).append(", ").append(c.v()).append(", ")
                        .append(c.u() + c.w()).append(", ").append(c.v() + c.h()).append("], \"texture\": \"#hat\"");
                if (c.tinted()) {
                    out.append(", \"tintindex\": 0");
                }
                out.append("}").append(++k < e.faces().size() ? ",\n" : "\n");
            }
            out.append("      }\n");
            out.append("    }").append(i + 1 < elements.size() ? ",\n" : "\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        Files.writeString(root.resolve("models/item/" + name + ".json"), out.toString(), StandardCharsets.UTF_8);
    }

    static String display(String name, double[] rotation, double[] translation, double scale) {
        return "    \"" + name + "\": {\"rotation\": " + vec(rotation, null) + ", \"translation\": " + vec(translation, null)
                + ", \"scale\": " + vec(new double[]{scale, scale, scale}, null) + "}";
    }

    static String vec(double[] v, double[] shift) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 3; i++) {
            double x = v[i] + (shift != null ? shift[i] : 0.0);
            b.append(num(round(x))).append(i < 2 ? ", " : "]");
        }
        return b.toString();
    }

    static double round(double x) {
        return Math.round(x * 1000.0) / 1000.0;
    }

    static String num(double x) {
        if (x == Math.rint(x)) {
            return Long.toString((long) x);
        }
        return String.format(Locale.ROOT, "%s", x);
    }

    static void writeItem(Path root, String name, String json) throws IOException {
        Files.writeString(root.resolve("items/" + name + ".json"), json, StandardCharsets.UTF_8);
    }

    // =========================================================================== helpers

    static void write(BufferedImage img, Path file) throws IOException {
        ImageIO.write(img, "png", new File(file.toString()));
    }

    /** A stable pseudo-random number in 0…1 for a texel. */
    static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static int shade(int c, float k) {
        int r = Math.min(255, Math.round(((c >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((c >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((c & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }
}
