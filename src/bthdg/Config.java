package bthdg;

import bthdg.exch.Bitstamp;
import bthdg.exch.Btce;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

//import bthdg.servlet.MemcacheStorage;

public class Config {
    public static boolean s_configured = false;
    public static boolean s_runOnServer = false;

    public static boolean load(String cfg) throws IOException {
        StringReader reader = new StringReader(cfg);
        Properties properties = new Properties();
        properties.load(reader);

        if (Bitstamp.init(properties)) {
            if (Btce.init(properties)) {
                s_configured = true;
                return true;
            }
        }
        return false;
    }

    public static boolean configured() throws IOException {
        if(!s_configured) {
            String config = null; //MemcacheStorage.getConfig();
            if(config != null) {
                return load(config);
            }
        }
        return s_configured;
    }
}
