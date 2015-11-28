package bthdg.exch;

import bthdg.Encryptor;
import bthdg.Log;

import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.Properties;

public class Config {
    public final Properties m_keys;

    private static void log(String s) { Log.log(s); }

    public Config() {
        m_keys = load();
    }

    public String getProperty(String key) { return m_keys.getProperty(key); }

    public Properties load() {
        Properties ret = loadKeys();
        return ret;
    }

    protected String getEncryptedFile() { return "keys.encrypted.txt"; }

    public void loadEncrypted(String pwd) {
        String eFileName = getEncryptedFile();
        if ((eFileName != null) && (eFileName.length() > 0)) {
            log("try to use encrypted file: " + eFileName);
            File eFile = new File(eFileName);
            if (eFile.exists()) {
                Properties eProperties = new Properties();
                try {
                    FileReader eReader = new FileReader(eFile);
                    try {
                        eProperties.load(eReader);
                    } finally {
                        eReader.close();
                    }
                } catch (Exception e) {
                    String msg = "error loading encr config properties: " + e;
                    log(msg);
                    throw new RuntimeException(msg);
                }

                Properties eRet = new Properties();
                for (Map.Entry<Object, Object> entry : eProperties.entrySet()) {
                    String encrypted = (String) entry.getValue();
                    try {
                        String decrypted = Encryptor.decrypt(encrypted, pwd);
                        Object key = entry.getKey();
//log("decrypted: " + key + "=" + decrypted);
                        eRet.put(key, decrypted);
                    } catch (Exception e) {
                        String msg = "error loading encr config properties. decrypt error : " + e;
                        log(msg);
                        throw new RuntimeException(msg);
                    }
                }
                m_keys.putAll(eRet);
            } else {
                log("encrypted file not exists: " + eFile.getAbsolutePath());
            }
        } else {
            log("encrypted file is not specified");
        }
    }

    public static Properties loadKeys() {
        return loadKeys("keys.txt");
    }

    protected static Properties loadKeys(String fileName) {
        Properties properties = new Properties();
        try {
            FileReader reader = new FileReader(fileName);
            try {
                properties.load(reader);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            String msg = "error loading config properties: " + e;
            log(msg);
            throw new RuntimeException(msg);
        }

        Properties ret = new Properties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String value = (String) entry.getValue();
            int index = value.indexOf('#');
            if (index != -1) { // cut comments
                value = value.substring(0, index).trim();
            }
            Object key = entry.getKey();
            ret.put(key, value);
        }
        return ret;
    }
}
