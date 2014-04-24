package bthdg;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class BaseChartPaint extends DbReady {
    public static final Color SEMI_TRANSPARENT_GRAY = new Color(128, 128, 128, 128); // Color.gray
    public static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f}, 0.0f);

    protected static void paintLeftAxeAndGrid(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe,
                                              Graphics2D g, double priceStep, double priceStart, int width) {
        paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, width, null);
    }

    protected static void paintLeftAxeAndGrid(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe,
                                              Graphics2D g, double priceStep, double priceStart, int width, Double highlightY) {
        Stroke oldStroke = g.getStroke();
        g.setStroke(DASHED_STROKE);
        for (double price = priceStart; price < maxPrice; price += priceStep) {
            if (price > minPrice) {
                int y = priceAxe.getPointReverse(price);
                boolean highlight = (highlightY != null) && (Math.abs(highlightY - price) < priceStep / 10);
                g.setPaint(highlight ? Color.BLACK : SEMI_TRANSPARENT_GRAY);
                g.drawLine(0, y, width - 1, y);
            }
        }
        g.setStroke(oldStroke);
    }

    protected static void paintLeftAxeLabels(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe, Graphics2D g, double priceStep, double priceStart, float xFactor) {
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, xFactor, DecimalFormat.getIntegerInstance());
    }

    protected static void paintLeftAxeLabels(double minPrice, double maxPrice, PaintChart.ChartAxe priceAxe, Graphics2D g, double priceStep, double priceStart,
                                             float xFactor, NumberFormat format) {
        g.setFont(g.getFont().deriveFont(20.0f * xFactor));
        for (double price = priceStart; price < maxPrice; price += priceStep) {
            if (price >= minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawString(format.format(price), 2, y - 1);
            }
        }
    }

    static void paintRightAxeLabels(double minDif, double maxDif, PaintChart.ChartAxe difAxe, Graphics2D g, int width, int priceDifStep, float xFactor, int yStart) {
        int priceDifStart = ((int) minDif) / priceDifStep * priceDifStep;
//        System.out.println("priceDifStart=" + priceDifStart);
        g.setFont(g.getFont().deriveFont(20.0f * xFactor));
        FontMetrics fontMetrics = g.getFontMetrics();
        for (int price = priceDifStart; price < maxDif; price += priceDifStep) {
            if (price >= minDif) {
                int y = difAxe.getPointReverse(price) + yStart;
                g.drawLine(width - 20, y, width - 1, y);
                String str = Integer.toString(price);
                Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
                g.drawString(str, (float) (width - 20 - bounds.getWidth()), y - 1);
            }
        }
    }

    public static void paintTimeAxeLabels(long minTimestamp, long maxTimestamp, PaintChart.ChartAxe timeAxe, Graphics2D g, int height, float xFactor) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(minTimestamp);
        Utils.setToDayStart(cal);

        long timePeriod = maxTimestamp - minTimestamp;
        int timePeriodDays = (int) (timePeriod / Utils.ONE_DAY_IN_MILLIS);

        // paint DAYS
        g.setFont(g.getFont().deriveFont(30.0f * xFactor));
        SimpleDateFormat format = new SimpleDateFormat("d MMM");
        while (true) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            long millis = cal.getTimeInMillis();
            if (millis >= minTimestamp) {
                if (millis > maxTimestamp) {
                    break;
                }
                String label = format.format(cal.getTime());
                int x = timeAxe.getPoint(millis);
                g.drawLine(x, height - 10, x, height - 1);
                g.drawString(label, x + 2, height - 8);
            }
        }

        // paint HOURS
        if (timePeriodDays <= 15) {
            cal.setTimeInMillis(minTimestamp);
            Utils.setToDayStart(cal);

            int hourPlus = (timePeriodDays > 6) ? 6 : ((timePeriodDays > 1) ? 2 : 1);

            g.setFont(g.getFont().deriveFont(20.0f * xFactor));
            format = new SimpleDateFormat("HH:mm");
            while (true) {
                cal.add(Calendar.HOUR_OF_DAY, hourPlus);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                if (hour != 0) {
                    long millis = cal.getTimeInMillis();
                    if (millis >= minTimestamp) {
                        if (millis > maxTimestamp) {
                            break;
                        }
                        String label = format.format(cal.getTime());
                        int x = timeAxe.getPoint(millis);
                        g.drawLine(x, height - 10, x, height - 1);
                        g.drawString(label, x + 2, height - 4);
                    }
                }
            }
        }
    }

    protected static void writeAndShowImage(BufferedImage image) {
        try {
            long millis = System.currentTimeMillis();

            File output = new File("imgout/" + Long.toString(millis, 32) + ".png");
            ImageIO.write(image, "png", output);

            System.out.println("write done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));

            Desktop.getDesktop().open(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void setupGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY );
    }

}
