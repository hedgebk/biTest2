package bthdg.util;

import java.awt.*;

public class Colors {
    public static final Color LIGHT_RED = new Color(255, 0, 0, 51);
    public static final Color LIGHT_BLUE = new Color(101, 98, 255);
    public static final Color TRANSP_GRAY = new Color(100, 100, 100, 50);
    public static final Color DARK_GREEN = new Color(0, 100, 0);
    public static final Color DARK_RED = new Color(140, 0, 0);
    public static final Color DARK_BLUE = new Color(0, 0, 120);
    public static final Color LIGHT_CYAN = new Color(150, 255, 255);
    public static final Color TRANSP_LIGHT_CYAN = new Color(150, 255, 255, 100);
    public static final Color SEMI_TRANSPARENT_GRAY = new Color(128, 128, 128, 128); // Color.gray
    public static final Color LIGHT_ORANGE = new Color(200, 100, 0, 90);
    public static final Color LIGHT_MAGNETA = new Color(255, 0, 255, 100);
    public static final Color BEGIE = new Color(255, 212, 63);
    public static final Color SKY = new Color(35, 152, 255);
    public static final Color BROWN = new Color(108, 67, 0);

    public static Color setAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha) ;
    }
}
