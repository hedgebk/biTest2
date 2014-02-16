import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static final DecimalFormat XX_YYYY = new DecimalFormat("#,##0.0000");

    public static String encodeHexString(byte[] hash) {
        return String.format("%064x", new BigInteger(1, hash));
    }

    public static String millisToDHMSStr(long millis) {
        StringBuilder res = new StringBuilder();
        long millisec = millis % 1000;
        res.append(millisec).append("ms");
        long sec = millis / 1000;
        if(sec > 0) {
            long secNum = sec % 60;
            res.insert(0, "sec ");
            res.insert(0, secNum);
            long minutes = sec / 60;
            if( minutes > 0 ) {
                long minutesNum = minutes % 60;
                res.insert(0, "min ");
                res.insert(0, minutesNum);
                long hours = minutes / 60;
                if( hours > 0 ) {
                    long hoursNum = hours % 24;
                    res.insert(0, "h ");
                    res.insert(0, hoursNum);
                    long days = hoursNum / 24;
                    if( days > 0 ) {
                        res.insert(0, "d ");
                        res.insert(0, days);
                    }
                }
            }
        }
        return res.toString();
    }

    // supports: "0", "-1M", "2w", "-3d", "-4h", "-5m", "-6s"
    public static long toMillis(String str) {
        long delta;
        if( "0".equals(str) ) {
            delta = 0;
        } else {
            Pattern p = Pattern.compile("^([\\+\\-])(\\d+)([a-zA-Z])+$");
            Matcher m = p.matcher(str);
            if( m.matches() ) {
                String sign = m.group(1);
                String count = m.group(2);
                String suffix = m.group(3);

                if(suffix.equals("M")) { // month
                    delta = 2592000000L; // 30L * 24L * 60L * 60L * 1000L;
                } else if(suffix.equals("w")) { // week
                    delta = 7 * 24 * 60 * 60 * 1000;
                } else if(suffix.equals("d")) { // days
                    delta = 24 * 60 * 60 * 1000;
                } else if(suffix.equals("h")) { // hours
                    delta = 60 * 60 * 1000;
                } else if(suffix.equals("m")) { // minutes
                    delta = 60 * 1000;
                } else if(suffix.equals("s")) { // seconds
                    delta = 1000;
                } else {
                    throw new RuntimeException("unsupported suffix '"+suffix+"' in pattern: " + str);
                }
                delta *= Integer.parseInt(count);
                if(sign.equals("-")) {
                    delta *= -1;
                }
            } else {
                throw new RuntimeException("unsupported pattern: " + str);
            }
        }
        return System.currentTimeMillis() + delta;
    }

    static void setToDayStart(Calendar cal) {
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY,0);
    }

    public static double getDouble(JSONObject object, String key) {
        return getDouble(object.get(key));
    }

    public static double getDouble(Object obj) {
        if(obj instanceof Double) {
            return (Double) obj;
        } else if(obj instanceof Long) {
            return ((Long) obj).doubleValue();
        } else if(obj instanceof String) {
            return Double.parseDouble((String) obj);
        } else {
            throw new RuntimeException("un-supported class: " + obj.getClass());
        }
    }

    public static long getLong(Object obj) {
        if(obj instanceof Long) {
            return (Long) obj;
        } else if(obj instanceof String) {
            return Long.parseLong((String) obj);
        } else {
            throw new RuntimeException("un-supported class: " + obj.getClass());
        }
    }

    public static abstract class DoubleAverageCalculator<O> {
        private double m_sum;
        private double m_weightSum;

        public abstract double getDoubleValue(O obj);
        protected double getWeight(O obj) { return 1; }

        DoubleAverageCalculator() { }

        public void addValue(O obj) {
            double value = getDoubleValue(obj);
            double weight = getWeight(obj);
            m_sum += value * weight;
            m_weightSum += weight;
        }

        protected double getAverage() {
            return m_sum/m_weightSum;
        }

        public double getAverage(Iterable<O> data) {
            double sum = 0;
            double weightSum = 0;
            for (O obj : data) {
                double value = getDoubleValue(obj);
                double weight = getWeight(obj);
                sum += value * weight;
                weightSum += weight;
            }
            return sum/weightSum;
        }
    }

    public static abstract class DoubleMinMaxCalculator<O> {
        public Double m_minValue;
        public Double m_maxValue;

        public abstract Double getValue(O obj);

        DoubleMinMaxCalculator(Iterable<O> data) {
            for (O obj : data) {
                Double value = getValue(obj);
                if ((m_maxValue == null) || (value > m_maxValue)) {
                    m_maxValue = value;
                }
                if ((m_minValue == null) || (value < m_minValue)) {
                    m_minValue = value;
                }
            }
        }
    }

    public static abstract class LongMinMaxCalculator<O> {
        public Long m_minValue;
        public Long m_maxValue;

        public abstract Long getValue(O obj);

        LongMinMaxCalculator(Iterable<O> data) {
            for (O obj : data) {
                Long value = getValue(obj);
                if ((m_maxValue == null) || (value > m_maxValue)) {
                    m_maxValue = value;
                }
                if ((m_minValue == null) || (value < m_minValue)) {
                    m_minValue = value;
                }
            }
        }
    }

    public static class AverageCounter {
        // probably better to have average counter which counts older ticks with lower ratio/weight
        public final TreeMap<Long,Double> m_map = new TreeMap<Long, Double>(); // sorted by time
        private final long m_limit;

        public AverageCounter(long limit) {
            m_limit = limit;
        }

        public double add(long millis, double addValue) {
            justAdd(millis, addValue);
            return get();
        }

        void justAdd(long millis, double addValue) {
            long limit = millis - m_limit;
            removeOld(limit, m_map);
            m_map.put(millis, addValue);
        }

        private static <T> void removeOld(long limit, TreeMap<Long, T> map) {
            SortedMap<Long, T> toRemove = map.headMap(limit);
            if (!toRemove.isEmpty()) {
                ArrayList<Long> keys = new ArrayList<Long>(toRemove.keySet());
                for (Long key : keys) {
                    map.remove(key);
                }
            }
        }

        public double get() {
            double summ = 0.0;
            int counter = 0;
            for(Double value: m_map.values()) {
                summ += value;
                counter++;
            }
            return summ/counter;
        }
    } // AverageCounter
}
