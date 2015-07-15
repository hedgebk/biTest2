package bthdg;

import bthdg.util.Colors;
import bthdg.util.Utils;

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
    public static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{10.0f}, 0.0f);
    public static final BasicStroke DASHED_BOLD_STROKE = new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{15.0f}, 0.0f);

    public static void paintLeftAxeAndGrid(double minPrice, double maxPrice, ChartAxe priceAxe,
                                              Graphics2D g, double priceStep, double priceStart, int width) {
        paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, width, null);
    }

    public static void paintLeftAxeAndGrid(double minPrice, double maxPrice, ChartAxe priceAxe,
                                           Graphics2D g, double priceStep, double priceStart, int width, Double highlightY) {
        Stroke oldStroke = g.getStroke();
        for (double price = priceStart; price < maxPrice; price += priceStep) {
            if ((price >= minPrice) && (price <= maxPrice)) {
                g.setStroke((price % 10 == 0) ? DASHED_BOLD_STROKE : DASHED_STROKE);
                int y = priceAxe.getPointReverse(price);
                boolean highlight = (highlightY != null) && (Math.abs(highlightY - price) < priceStep / 10);
                g.setPaint(highlight ? Color.BLACK : Colors.SEMI_TRANSPARENT_GRAY);
                g.drawLine(0, y, width - 1, y);
            }
        }
        g.setStroke(oldStroke);
    }

    public static void paintLeftAxeLabels(double minPrice, double maxPrice, ChartAxe priceAxe, Graphics2D g, double priceStep,
                                          double priceStart, float xFactor) {
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, xFactor, DecimalFormat.getIntegerInstance());
    }

    public static void paintLeftAxeLabels(double minPrice, double maxPrice, ChartAxe priceAxe, Graphics2D g, double priceStep, double priceStart,
                                             float xFactor, NumberFormat format) {
        g.setFont(g.getFont().deriveFont(20.0f * xFactor));
        for (double price = priceStart; price < maxPrice; price += priceStep) {
            if (price >= minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawString(format.format(price), 2, y - 1);
            }
        }
    }

    public static void paintRightAxeLabels(double minDif, double maxDif, ChartAxe difAxe, Graphics2D g, int width, int priceDifStep, float xFactor, int yStart) {
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

    public static void paintTimeAxeLabels(long minTimestamp, long maxTimestamp, ChartAxe timeAxe, Graphics2D g, int height, float xFactor) {
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
            Utils.setToHourStart(cal);

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

            if (timePeriodDays <= 2) {
                cal.setTimeInMillis(minTimestamp);
                cal.set(Calendar.MILLISECOND, 0);
                cal.set(Calendar.SECOND, 0);
                int minute = cal.get(Calendar.MINUTE);
                cal.set(Calendar.MINUTE, (minute/10) * 10);

                int mintesStep = (timePeriodDays <= 1) ? 5 : 10;

                g.setFont(g.getFont().deriveFont(15.0f * xFactor));
                format = new SimpleDateFormat("mm");
                while (true) {
                    cal.add(Calendar.MINUTE, mintesStep);
                    int min = cal.get(Calendar.MINUTE);
                    if (min != 0) {
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
    }

    public static void writeAndShowImage(BufferedImage image) {
        try {
            long millis = System.currentTimeMillis();

            System.out.println("writing image...");

            File output = new File("imgout/" + Long.toString(millis, 32) + ".png");
            ImageIO.write(image, "png", output);

            System.out.println("write done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));

            Desktop.getDesktop().open(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setupGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    public static void drawX(Graphics2D g, int x, int y, int d) {
        g.drawLine(x - d, y - d, x + d, y + d);
        g.drawLine(x - d, y + d, x + d, y - d);
    }

    public static class Tick implements Comparable<Long> {
        public final int m_src;
        public final long m_stamp;
        public final double m_price;
        public final double m_volume;

        public Tick(long stamp, double price, int src, double volume) {
            m_stamp = stamp;
            m_price = price;
            m_src = src;
            m_volume = volume;
        }

        @Override public int compareTo(Long other) {
            return Long.compare(m_stamp, other);
        }

        @Override public String toString() {
            return "Tick[" +
                    "m_src=" + m_src +
                    ", m_stamp=" + m_stamp +
                    ", m_price=" + m_price +
                    ", m_volume=" + m_volume +
                    ']';
        }
    }

}
