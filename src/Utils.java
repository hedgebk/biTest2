import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Calendar;
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
}
