/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.oracledb;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.Constants;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.util.CryptoUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bhuvnesh.kumar on 1/23/18.
 */
public class OracledbMonitor extends ABaseMonitor {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(OracledbMonitor.class);
    private long previousTimestamp = 0;
    private long currentTimestamp = System.currentTimeMillis();

    @Override
    protected String getDefaultMetricPrefix() {
        return "Custom Metrics|OracleDB";
    }

    @Override
    public String getMonitorName() {
        return "OracleDB Monitor";
    }

    @Override
    protected void doRun(TasksExecutionServiceProvider serviceProvider) {

        List<Map<String, String>> servers = (List<Map<String, String>>) getContextConfiguration().getConfigYml().get("dbServers");

        previousTimestamp = currentTimestamp;
        currentTimestamp = System.currentTimeMillis();
        if (previousTimestamp != 0) {
            for (Map<String, String> server : servers) {
                try {
                    OracledbMonitorTask task = createTask(server, serviceProvider);
                    serviceProvider.submit(server.get("displayName"), task);
                } catch (IOException e) {
                    logger.error("Cannot construct JDBC uri for {}", Util.convertToString(server.get("displayName"), ""));
                }
            }
        }
    }

    @Override
    protected List<Map<String, ?>> getServers() {
        List<Map<String, ?>> servers = (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("dbServers");
        return servers;
    }


    private OracledbMonitorTask createTask(Map server, TasksExecutionServiceProvider serviceProvider) throws IOException {
        String connUrl = createConnectionUrl(server);

        AssertUtils.assertNotNull(serverName(server), "The 'displayName' field under the 'dbServers' section in config.yml is not initialised");
        AssertUtils.assertNotNull(createConnectionUrl(server), "The 'connectionUrl' field under the 'dbServers' section in config.yml is not initialised");
        AssertUtils.assertNotNull(driverName(server), "The 'driver' field under the 'dbServers' section in config.yml is not initialised");
        Map<String, String> connectionProperties = getConnectionProperties(server);
        JDBCConnectionAdapter jdbcAdapter = JDBCConnectionAdapter.create(connUrl, connectionProperties);
        logger.debug("Creating Task");

        return new OracledbMonitorTask.Builder()
                .metricWriter(serviceProvider.getMetricWriteHelper())
                .metricPrefix(getContextConfiguration().getMetricPrefix())
                .jdbcAdapter(jdbcAdapter)
                .previousTimestamp(previousTimestamp)
                .currentTimestamp(currentTimestamp)
                .server(server).build();

    }
    private String serverName(Map server) {
        String name = Util.convertToString(server.get("displayName"), "");
        return name;
    }

    private String driverName(Map server) {
        String name = Util.convertToString(server.get("driver"), "");
        return name;
    }

    private String createConnectionUrl(Map server) {
        String url = Util.convertToString(server.get("connectionUrl"), "");
        return url;
    }

    private Map<String, String> getConnectionProperties(Map server) {
        Map<String, String> connectionProperties = new LinkedHashMap<String, String>();
        List<Map<String, String>> listOfMaps = (List<Map<String, String>>) server.get("connectionProperties");

        if (listOfMaps != null) {
            for (Map amap : listOfMaps) {
                for (Object key : amap.keySet()) {
                    if (key.toString().equals("password")) {
                        String password;

                        if (Strings.isNullOrEmpty((String) amap.get(key))) {
                            password = getPassword(server, "");
                        } else {
                            password = (String) amap.get(key);
                        }
                        connectionProperties.put((String) key, password);
                    } else {
                        connectionProperties.put((String) key, (String) amap.get(key));
                    }
                }
            }
            return connectionProperties;

        }

        return null;
    }

    private String getPassword(Map server, String normal_password) {
        String encryptionPassword = Util.convertToString(server.get("encryptedPassword"), "");
        String encryptionKey = Util.convertToString(server.get("encryptionKey"), "");
        String password;
        if (!Strings.isNullOrEmpty(encryptionKey) && !Strings.isNullOrEmpty(encryptionPassword)) {
            password = getEncryptedPassword(encryptionKey, encryptionPassword);
        } else {
            password = normal_password;
        }
        return password;
    }

    private String getEncryptedPassword(String encryptionKey, String encryptedPassword) {
        Map<String, String> cryptoMap = Maps.newHashMap();
        cryptoMap.put(Constants.ENCRYPTED_PASSWORD, encryptedPassword);
        cryptoMap.put(Constants.ENCRYPTION_KEY, encryptionKey);
        return CryptoUtils.getPassword(cryptoMap);
    }


}
