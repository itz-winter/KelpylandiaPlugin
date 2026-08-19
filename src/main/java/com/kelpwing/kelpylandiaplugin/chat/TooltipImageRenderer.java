package com.kelpwing.kelpylandiaplugin.chat;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Renders a Minecraft-style tooltip as a PNG image using Java2D.
 * <p>
 * Supports multi-coloured lore lines: § colour/format codes embedded in lore
 * strings are parsed into coloured segments so each segment is drawn with its
 * correct colour.  All other tooltip lines (name, enchants, attributes …) are
 * still created with a single explicit colour via the simple constructor.
 */
public class TooltipImageRenderer {

    // Colour palette (Minecraft tooltip colours) 
    private static final Color BG_FILL             = new Color(16, 0, 16, 240);
    private static final Color BORDER_OUTER        = new Color(16, 0, 16, 255);
    private static final Color BORDER_INNER_TOP    = new Color(80, 0, 255, 80);
    private static final Color BORDER_INNER_BOTTOM = new Color(40, 0, 127, 80);
    private static final Color TEXT_WHITE          = new Color(255, 255, 255);
    private static final Color TEXT_YELLOW         = new Color(255, 255, 85);
    private static final Color TEXT_AQUA           = new Color(85, 255, 255);
    private static final Color TEXT_LIGHT_PURPLE   = new Color(255, 85, 255);
    private static final Color TEXT_GRAY           = new Color(170, 170, 170);
    private static final Color TEXT_DARK_GRAY      = new Color(85, 85, 85);
    private static final Color TEXT_RED            = new Color(255, 85, 85);
    private static final Color TEXT_PURPLE         = new Color(170, 0, 170);
    private static final Color TEXT_DARK_GREEN     = new Color(0, 170, 0);
    private static final Color TEXT_BLUE           = new Color(85, 85, 255);

    // Layout constants 
    private static final int PADDING_X   = 10;
    private static final int PADDING_Y   = 8;
    private static final int LINE_HEIGHT = 18;
    private static final int BORDER_WIDTH = 2;
    private static final String FONT_NAME = "Consolas";
    private static final int FONT_SIZE    = 14;

    // Fonts (created once) 
    private static final Font FONT_BASE        = new Font(FONT_NAME, Font.PLAIN,        FONT_SIZE);
    private static final Font FONT_ITALIC      = new Font(FONT_NAME, Font.ITALIC,       FONT_SIZE);
    private static final Font FONT_BOLD        = new Font(FONT_NAME, Font.BOLD,         FONT_SIZE);
    private static final Font FONT_BOLD_ITALIC = new Font(FONT_NAME, Font.BOLD | Font.ITALIC, FONT_SIZE);

    
    // Inner classes
    

    /**
     * A single coloured run of text within a tooltip line.
     */
    private static class ColorSegment {
        final String text;
        final Color  color;
        final boolean bold;
        final boolean italic;

        ColorSegment(String text, Color color, boolean bold, boolean italic) {
            this.text   = text;
            this.color  = color;
            this.bold   = bold;
            this.italic = italic;
        }
    }

    /**
     * One logical line in the tooltip - may contain multiple {@link ColorSegment}s
     * (for lore lines that embed § colour codes) or just a single segment (for all
     * other lines where we supply the colour explicitly).
     */
    private static class TooltipLine {
        final List<ColorSegment> segments;

        /** Simple single-colour, single-style constructor used by most lines. */
        TooltipLine(String text, Color color, boolean italic) {
            this.segments = Collections.singletonList(
                    new ColorSegment(text, color, false, italic));
        }

        /** Multi-segment constructor - used for colour-parsed lore lines. */
        TooltipLine(List<ColorSegment> segments) {
            this.segments = segments;
        }

        boolean isEmpty() {
            return segments.isEmpty()
                    || segments.stream().allMatch(s -> s.text.isEmpty());
        }
    }

    
    // Public API
    

    /**
     * Render a tooltip image for the given item data.
     *
     * @param data the item display data snapshot
     * @return PNG image bytes as an InputStream, or null if the item is air/nothing
     */
    public static InputStream renderTooltip(ItemDisplayData data) {
        if (data == null || data.getType() != ItemDisplayData.DisplayType.ITEM) return null;
        if ("AIR".equals(data.getMaterialName()) || data.getAmount() == 0) return null;

        List<TooltipLine> lines = buildTooltipLines(data);
        if (lines.isEmpty()) return null;

        try {
            BufferedImage image = renderLines(lines);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Render a tooltip for an inventory/ender chest display.
     */
    public static InputStream renderInventoryTooltip(ItemDisplayData data) {
        if (data == null) return null;
        if (data.getType() != ItemDisplayData.DisplayType.INVENTORY
                && data.getType() != ItemDisplayData.DisplayType.ENDER_CHEST) return null;

        List<TooltipLine> lines = buildInventoryLines(data);
        if (lines.isEmpty()) return null;

        try {
            BufferedImage image = renderLines(lines);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    
    // Line building
    

    private static List<TooltipLine> buildTooltipLines(ItemDisplayData data) {
        List<TooltipLine> lines = new ArrayList<>();

        // 1. Item name - parse § codes so custom formatting is preserved.
        //   Custom-renamed items use their own § codes; default colour is rarity-based.
        //   Vanilla names have no § codes so parseLegacyLine falls back to rarityColor.
        String name = data.getItemName();
        if (data.getAmount() > 1) name += " x" + data.getAmount();
        if (data.hasCustomName()) {
            // Custom names: parse § codes (default to aqua+italic if no code present)
            lines.add(parseLegacyLine(name, TEXT_AQUA, true));
        } else {
            lines.add(new TooltipLine(name, rarityToColor(data.getRarity()), false));
        }

        // 2. Potion effects (aqua)
        for (String effect : data.getPotionEffects()) {
            lines.add(new TooltipLine(effect, TEXT_AQUA, false));
        }

        // 3. Enchantments
        if (!data.isHideEnchants()) {
            for (Map.Entry<String, Integer> ench : data.getEnchantments().entrySet()) {
                String enchName = ench.getKey();
                int level = ench.getValue();
                boolean isCurse = enchName.toLowerCase().contains("curse");
                Color color = isCurse ? TEXT_RED : TEXT_GRAY;
                String levelStr = level > 1 ? " " + toRoman(level) : "";
                lines.add(new TooltipLine(enchName + levelStr, color, false));
            }
        }

        // 4. Lore - parse § colour codes so each line renders with its correct colours.
        //   Vanilla default for lore: dark-purple italic; codes in the line override this.
        for (String loreLine : data.getLore()) {
            lines.add(parseLegacyLine(loreLine, TEXT_PURPLE, true));
        }

        // 5. Attribute modifier lines
        if (!data.isHideAttributes()) {
            for (String attrLine : data.getAttributeLines()) {
                if (attrLine.startsWith("When in ")) {
                    lines.add(new TooltipLine("", TEXT_GRAY, false)); // blank separator
                    lines.add(new TooltipLine(attrLine, TEXT_GRAY, false));
                } else {
                    boolean negative = attrLine.trim().startsWith("-");
                    lines.add(new TooltipLine(attrLine, negative ? TEXT_RED : TEXT_DARK_GREEN, false));
                }
            }
        }

        // 6. Unbreakable
        if (data.isUnbreakable() && !data.isHideDurability()) {
            lines.add(new TooltipLine("Unbreakable", TEXT_BLUE, false));
        }

        // 7. Durability
        if (!data.isUnbreakable() && !data.isHideDurability()
                && data.getMaxDurability() > 0
                && data.getDurability() < data.getMaxDurability()) {
            lines.add(new TooltipLine(
                    "Durability: " + data.getDurability() + " / " + data.getMaxDurability(),
                    TEXT_WHITE, false));
        }

        // 8. Material ID
        lines.add(new TooltipLine(
                "minecraft:" + data.getMaterialName().toLowerCase(),
                TEXT_DARK_GRAY, false));

        return lines;
    }

    private static List<TooltipLine> buildInventoryLines(ItemDisplayData data) {
        List<TooltipLine> lines = new ArrayList<>();

        boolean isEC = data.getType() == ItemDisplayData.DisplayType.ENDER_CHEST;
        lines.add(new TooltipLine(isEC ? "Ender Chest" : "Inventory",
                isEC ? TEXT_LIGHT_PURPLE : TEXT_WHITE, false));
        lines.add(new TooltipLine(
                "Slots: " + data.getUsedSlots() + "/" + data.getTotalSlots(),
                TEXT_GRAY, false));
        for (String item : data.getTopItems()) {
            lines.add(new TooltipLine("  " + item, TEXT_GRAY, false));
        }
        int remaining = data.getUsedSlots() - data.getTopItems().size();
        if (remaining > 0) {
            lines.add(new TooltipLine("  ..." + remaining + " more", TEXT_DARK_GRAY, true));
        }
        return lines;
    }

    
    // Legacy colour-code parser
    

    /**
     * Parses a string that may contain § colour/format codes into a {@link TooltipLine}
     * whose segments each carry the correct colour and style.
     *
     * <p>Colour codes reset bold/italic to defaults.  Format codes {@code §l} (bold),
     * {@code §o} (italic) accumulate on top of the current colour.  {@code §r} resets
     * everything to {@code defaultColor}/{@code defaultItalic}.</p>
     */
    private static TooltipLine parseLegacyLine(String legacy,
                                                Color defaultColor,
                                                boolean defaultItalic) {
        if (legacy == null || legacy.isEmpty()) {
            return new TooltipLine(legacy == null ? "" : legacy, defaultColor, defaultItalic);
        }

        List<ColorSegment> segments = new ArrayList<>();
        Color   currentColor  = defaultColor;
        boolean currentBold   = false;
        boolean currentItalic = defaultItalic;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if ((c == '§' || c == '\u00a7') && i + 1 < legacy.length()) {
                // Flush accumulated text before applying the code
                if (buf.length() > 0) {
                    segments.add(new ColorSegment(buf.toString(), currentColor, currentBold, currentItalic));
                    buf = new StringBuilder();
                }
                char code = Character.toLowerCase(legacy.charAt(++i));
                Color newColor = legacyCodeToColor(code);
                if (newColor != null) {
                    // Colour code: also resets bold/italic (vanilla behaviour)
                    currentColor  = newColor;
                    currentBold   = false;
                    currentItalic = false;
                } else {
                    switch (code) {
                        case 'l': currentBold   = true;  break;
                        case 'o': currentItalic = true;  break;
                        case 'r':
                            currentColor  = defaultColor;
                            currentBold   = false;
                            currentItalic = defaultItalic;
                            break;
                        // k (obfuscated), m (strikethrough), n (underline) - ignored visually
                        default: break;
                    }
                }
            } else {
                buf.append(c);
            }
        }
        if (buf.length() > 0) {
            segments.add(new ColorSegment(buf.toString(), currentColor, currentBold, currentItalic));
        }
        if (segments.isEmpty()) {
            segments.add(new ColorSegment("", defaultColor, false, defaultItalic));
        }
        return new TooltipLine(segments);
    }

    /** Maps a legacy colour char ('0'–'f') to a Java2D {@link Color}, or {@code null} for format codes. */
    private static Color legacyCodeToColor(char code) {
        switch (code) {
            case '0': return new Color(0,   0,   0  ); // BLACK
            case '1': return new Color(0,   0,   170); // DARK_BLUE
            case '2': return new Color(0,   170, 0  ); // DARK_GREEN
            case '3': return new Color(0,   170, 170); // DARK_AQUA
            case '4': return new Color(170, 0,   0  ); // DARK_RED
            case '5': return new Color(170, 0,   170); // DARK_PURPLE
            case '6': return new Color(255, 170, 0  ); // GOLD
            case '7': return new Color(170, 170, 170); // GRAY
            case '8': return new Color(85,  85,  85 ); // DARK_GRAY
            case '9': return new Color(85,  85,  255); // BLUE
            case 'a': return new Color(85,  255, 85 ); // GREEN
            case 'b': return new Color(85,  255, 255); // AQUA
            case 'c': return new Color(255, 85,  85 ); // RED
            case 'd': return new Color(255, 85,  255); // LIGHT_PURPLE
            case 'e': return new Color(255, 255, 85 ); // YELLOW
            case 'f': return new Color(255, 255, 255); // WHITE
            default:  return null; // format code, not a colour
        }
    }

    
    // Image rendering
    

    private static BufferedImage renderLines(List<TooltipLine> lines) {
        // First pass: measure maximum line width 
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = measure.createGraphics();
        mg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int maxWidth = 0;
        for (TooltipLine line : lines) {
            if (line.isEmpty()) continue;
            int lineWidth = 0;
            for (ColorSegment seg : line.segments) {
                if (seg.text.isEmpty()) continue;
                mg.setFont(fontFor(seg));
                lineWidth += mg.getFontMetrics().stringWidth(seg.text);
            }
            if (lineWidth > maxWidth) maxWidth = lineWidth;
        }
        mg.dispose();

        int imgWidth  = Math.max(100, maxWidth + PADDING_X * 2 + BORDER_WIDTH * 2 + 4);
        int imgHeight = Math.max(30,  lines.size() * LINE_HEIGHT + PADDING_Y * 2 + BORDER_WIDTH * 2);

        // Second pass: render 
        BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // Background
        g.setColor(BORDER_OUTER);
        g.fillRect(0, 0, imgWidth, imgHeight);
        g.setColor(BG_FILL);
        g.fillRect(BORDER_WIDTH, BORDER_WIDTH,
                imgWidth - BORDER_WIDTH * 2, imgHeight - BORDER_WIDTH * 2);

        // Inner gradient border (characteristic MC purple fade)
        g.setPaint(new GradientPaint(0, BORDER_WIDTH, BORDER_INNER_TOP,
                0, imgHeight - BORDER_WIDTH, BORDER_INNER_BOTTOM));
        g.fillRect(BORDER_WIDTH, BORDER_WIDTH + 1, 1, imgHeight - BORDER_WIDTH * 2 - 2);
        g.fillRect(imgWidth - BORDER_WIDTH - 1, BORDER_WIDTH + 1, 1, imgHeight - BORDER_WIDTH * 2 - 2);
        g.setColor(BORDER_INNER_TOP);
        g.fillRect(BORDER_WIDTH + 1, BORDER_WIDTH, imgWidth - BORDER_WIDTH * 2 - 2, 1);
        g.setColor(BORDER_INNER_BOTTOM);
        g.fillRect(BORDER_WIDTH + 1, imgHeight - BORDER_WIDTH - 1, imgWidth - BORDER_WIDTH * 2 - 2, 1);

        // Text
        int baseX = PADDING_X + BORDER_WIDTH;
        int y     = PADDING_Y + BORDER_WIDTH + FONT_SIZE; // baseline

        for (TooltipLine line : lines) {
            int segX = baseX;
            for (ColorSegment seg : line.segments) {
                if (seg.text.isEmpty()) continue;
                Font f = fontFor(seg);
                g.setFont(f);
                FontMetrics fm = g.getFontMetrics();

                // Drop shadow (MC-style: 1 px offset at ¼ brightness)
                Color shadow = new Color(
                        seg.color.getRed()   / 4,
                        seg.color.getGreen() / 4,
                        seg.color.getBlue()  / 4,
                        seg.color.getAlpha());
                g.setColor(shadow);
                g.drawString(seg.text, segX + 1, y + 1);

                g.setColor(seg.color);
                g.drawString(seg.text, segX, y);

                segX += fm.stringWidth(seg.text);
            }
            y += LINE_HEIGHT;
        }

        g.dispose();
        return image;
    }

    
    // Utility
    

    private static Font fontFor(ColorSegment seg) {
        if (seg.bold && seg.italic) return FONT_BOLD_ITALIC;
        if (seg.bold)               return FONT_BOLD;
        if (seg.italic)             return FONT_ITALIC;
        return FONT_BASE;
    }

    private static Color rarityToColor(ItemDisplayData.Rarity rarity) {
        switch (rarity) {
            case UNCOMMON: return TEXT_YELLOW;
            case RARE:     return TEXT_AQUA;
            case EPIC:     return TEXT_LIGHT_PURPLE;
            default:       return TEXT_WHITE;
        }
    }

    private static String toRoman(int value) {
        if (value <= 0 || value > 10) return String.valueOf(value);
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return numerals[value];
    }
}

