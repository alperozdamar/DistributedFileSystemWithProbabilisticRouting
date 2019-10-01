package edu.usfca.cs.dfs.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Singleton Configuration Manager for Project1.
 * 
 */
public class ConfigurationManagerSn {

    public static final String            PROJECT_1_SN_CONFIG_FILE = "config" + File.separator
            + "project1_sn.properties";
    private static ConfigurationManagerSn instance;
    private final static Object           classLock                = new Object();
    private String                        controllerIp             = "";
    private int                           controllerPort           = 9090;
    private int                           snId                     = 0;
    private int                           snPort                   = 9090;

    private ConfigurationManagerSn() {
        readConfigFile();
    }

    /**
     * Singleton
     *  
     * @return
     */
    public static ConfigurationManagerSn getInstance() {
        synchronized (classLock) {
            if (instance == null) {
                instance = new ConfigurationManagerSn();
            }
            return instance;
        }
    }

    public void readConfigFile() {

        Properties props = new Properties();
        try {
            props.load(new FileInputStream(PROJECT_1_SN_CONFIG_FILE));

            try {
                String snIdString = props.getProperty("snId").trim();
                snId = (snIdString == null) ? 8080 : Integer.parseInt(snIdString);
            } catch (Exception e) {
                snId = 8800;
                e.printStackTrace();
            }

            try {
                String snPortString = props.getProperty("snPort").trim();
                snPort = (snPortString == null) ? 8080 : Integer.parseInt(snPortString);
            } catch (Exception e) {
                snPort = 8800;
                e.printStackTrace();
            }

            controllerIp = props.getProperty("controllerIp");
            if (controllerIp == null) {
                System.out.println("controllerIp property is Null! Please Check configuration file.");
            } else {
                controllerIp = controllerIp.trim();
            }

            try {
                String controllerPortString = props.getProperty("controllerPort").trim();
                controllerPort = (controllerPortString == null) ? 8080
                        : Integer.parseInt(controllerPortString);
            } catch (Exception e) {
                controllerPort = 8800;
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("Exception occured while parsing Configuration File:"
                    + PROJECT_1_SN_CONFIG_FILE);
            // e.printStackTrace(); //professor doesn't want stackTrace.
            e.getMessage();
        }
    }

    public String getControllerIp() {
        return controllerIp;
    }

    public void setControllerIp(String controllerIp) {
        this.controllerIp = controllerIp;
    }

    public int getControllerPort() {
        return controllerPort;
    }

    public void setControllerPort(int controllerPort) {
        this.controllerPort = controllerPort;
    }

    public int getSnId() {
        return snId;
    }

    public void setSnId(int snId) {
        this.snId = snId;
    }

    public int getSnPort() {
        return snPort;
    }

    public void setSnPort(int snPort) {
        this.snPort = snPort;
    }

    @Override
    public String toString() {
        return "ConfigurationManagerSn [controllerIp=" + controllerIp + ", controllerPort="
                + controllerPort + ", snId=" + snId + ", snPort=" + snPort + "]";
    }

}