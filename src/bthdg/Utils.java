package bthdg;

import org.json.simple.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static final DecimalFormat XX_YYYY = new DecimalFormat("#,##0.0000");
    public static final long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
    public static final String PADS = "          ";
    public static final DecimalFormat PLUS_YYY = new DecimalFormat("+0.000;-0.000");
    public static final DecimalFormat X_YYYY = new DecimalFormat("0.0000");
    public static final DecimalFormat X_YYYYY = new DecimalFormat("0.00000");
    public static final DecimalFormat X_YYYYYYYY = new DecimalFormat("0.00000000");
    public static final DecimalFormat X_X = new DecimalFormat("0.0#######");

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
                    long days = hours / 24;
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

    public static void setToDayStart(Calendar cal) {
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

    public static boolean compareAndNotNulls(Object o1, Object o2) {
        if (o1 == null) {
            if (o2 == null) {
                return false;
            } else {
                throw new RuntimeException("null & not_null");
            }
        } else {
            if (o2 == null) {
                throw new RuntimeException("not_null & null");
            } else {
                return true;
            }
        }
    }

    public static double fourDecimalDigits(double amount) { // 1.234567 -> 1.2345
        return round(amount, 4);
    }

    public static double round(double amount, int decimals) {
        return round(amount, decimals, 1);
    }

    private static double round(double amount, int decimals, long mult) {
        if (decimals == 0) {
            return ((double) Math.round(amount * mult)) / mult;
        }
        return round(amount, decimals - 1, mult * 10);
    }

    public static String padRight(String str, int destLen) {
        int currLen = str.length();
        if (currLen < destLen) {
            return padRight(str + PADS.substring(0, Math.min(destLen - currLen, PADS.length())), destLen);
        }
        return str;
    }

    public static String padLeft(String str, int destLen) {
        int currLen = str.length();
        if (currLen < destLen) {
            return padLeft(PADS.substring(0, Math.min(destLen - currLen, PADS.length())) + str, destLen);
        }
        return str;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    public static abstract class DoubleAverageCalculator<O> {
        private double m_sum;
        private double m_weightSum;

        public abstract double getDoubleValue(O obj);
        protected double getWeight(O obj) { return 1; }

        public DoubleAverageCalculator() { }

        public void addValue(O obj) {
            double value = getDoubleValue(obj);
            double weight = getWeight(obj);
            m_sum += value * weight;
            m_weightSum += weight;
        }

        public double getAverage() {
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

        public void serialize(StringBuilder sb) {
            sb.append("DblAvgClcltr[sum=").append(m_sum);
            sb.append("; weightSum=").append(m_weightSum);
            sb.append("]");
        }

        public void deserialize(Deserializer deserializer) throws IOException {
            deserializer.readObjectStart("DblAvgClcltr");
            deserializer.readPropStart("sum");
            String sum = deserializer.readTill("; ");
            deserializer.readPropStart("weightSum");
            String weightSum = deserializer.readTill("]");
            m_sum = Double.valueOf(sum);
            m_weightSum = Double.valueOf(weightSum);
        }

        public void compare(DoubleAverageCalculator<O> other) {
            if (m_sum != other.m_sum) {
                throw new RuntimeException("m_sum");
            }
            if (m_weightSum != other.m_weightSum) {
                throw new RuntimeException("m_weightSum");
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    public static abstract class DoubleMinMaxCalculator<O> {
        public Double m_minValue;
        public Double m_maxValue;
        private Double[] m_ar = new Double[1];

        public abstract Double getValue(O obj);
        public Double[] getValues(O obj) {
            m_ar[0] = getValue(obj);
            return m_ar;
        }

        public DoubleMinMaxCalculator() {}

        public DoubleMinMaxCalculator(Iterable<O> data) {
            calculate(data);
        }

        public void calculate(Iterable<O> data) {
            for (O obj : data) {
                calculate(obj);
            }
        }

        public void calculate(O obj) {
            if (obj != null) {
                Double[] values = getValues(obj);
                for (Double value : values) {
                    if (value != null) {
                        if ((m_maxValue == null) || (value > m_maxValue)) {
                            m_maxValue = value;
                        }
                        if ((m_minValue == null) || (value < m_minValue)) {
                            m_minValue = value;
                        }
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    public static abstract class LongMinMaxCalculator<O> {
        public Long m_minValue;
        public Long m_maxValue;

        public abstract Long getValue(O obj);

        public LongMinMaxCalculator() { }

        public LongMinMaxCalculator(Iterable<O> data) {
            calculate(data);
        }

        public void calculate(Iterable<O> data) {
            for (O obj : data) {
                calculate(obj);
            }
        }

        public void calculate(O obj) {
            if (obj != null) {
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

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    public static class AverageCounter {
        // probably better to have average counter which counts older ticks with lower ratio/weight
        public final TreeMap<Long,Double> m_map; // sorted by time
        private final long m_limit;

        public AverageCounter(long limit) {
            this(limit, new TreeMap<Long, Double>());
        }

        public AverageCounter(long limit, TreeMap<Long, Double> map) {
            m_limit = limit;
            m_map = map;
        }

        public double add(long millis, double addValue) {
            justAdd(millis, addValue);
            return get();
        }

        public void justAdd(long millis, double addValue) {
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

        public void serialize(StringBuilder sb) {
            sb.append("AvgCntr[limit=").append(m_limit);
            sb.append("; map=[");
            for(Map.Entry<Long, Double> e: m_map.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("; ");
            }
            sb.append("]]");
        }

        public static AverageCounter deserialize(Deserializer deserializer) throws IOException {
            if( deserializer.readIf("; ")) {
                return null;
            }
            deserializer.readObjectStart("AvgCntr");
            deserializer.readPropStart("limit");
            String limitStr = deserializer.readTill("; ");
            deserializer.readPropStart("map");
            Map<String,String> map = deserializer.readMap();
            deserializer.readObjectEnd();
            deserializer.readStr("; ");

            TreeMap<Long,Double> map2 = new TreeMap<Long, Double>(); // sorted by time
            for(Map.Entry<String, String> entry: map.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                long millis = Long.parseLong(key);
                double dbl= Double.parseDouble(value);
                map2.put(millis, dbl);
            }
            long limit = Long.parseLong(limitStr);
            return new AverageCounter(limit, map2);
        }

        public void compare(AverageCounter other) {
            if (m_limit != other.m_limit) {
                throw new RuntimeException("m_limit");
            }
            compareMaps(m_map, other.m_map);
        }

        private void compareMaps(TreeMap<Long, Double> map, TreeMap<Long, Double> other) {
            if(Utils.compareAndNotNulls(map, other)) {
                int size = map.size();
                if(size != other.size()) {
                    throw new RuntimeException("map.size");
                }
                Set<Long> keys1 = map.keySet();
                Set<Long> keys2 = other.keySet();
                for (Long key : keys1) {
                    if(!keys2.contains(key)) {
                        throw new RuntimeException("map.key="+key);
                    }
                    Double value1 = map.get(key);
                    Double value2 = other.get(key);
                    if(!value1.equals(value2)) {
                        throw new RuntimeException("map["+key+"].value");
                    }
                }
            }
        }
    } // AverageCounter
}
