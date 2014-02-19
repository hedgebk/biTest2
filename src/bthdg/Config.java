package bthdg;

import java.util.Properties;

public class Config {
    public static boolean s_configured = false;

    public static boolean load(Properties properties) {
        if (Bitstamp.init(properties)) {
            if (Btce.init(properties)) {
                s_configured = true;
                return true;
            }
        }
        return false;
    }
}
