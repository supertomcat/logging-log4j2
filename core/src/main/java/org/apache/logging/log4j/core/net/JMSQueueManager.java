/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.net;

import org.apache.logging.log4j.core.appender.ManagerFactory;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.Serializable;

/**
 * Manager for a JMS Queue.
 */
public class JMSQueueManager extends AbstractJMSManager {

    private static final JMSQueueManagerFactory factory = new JMSQueueManagerFactory();

    private QueueInfo info;
    private final String factoryBindingName;
    private final String queueBindingName;
    private final String userName;
    private final String password;
    private final Context context;

    /**
     * The Constructor.
     * @param name The unique name of the connection.
     * @param context The context.
     * @param factoryBindingName The factory binding name.
     * @param queueBindingName The queue binding name.
     * @param userName The user name.
     * @param password The credentials for the user.
     * @param info The Queue connection info.
     */
    protected JMSQueueManager(String name, Context context, String factoryBindingName, String queueBindingName,
                              String userName, String password, QueueInfo info) {
        super(name);
        this.context = context;
        this.factoryBindingName = factoryBindingName;
        this.queueBindingName = queueBindingName;
        this.userName = userName;
        this.password = password;
        this.info = info;
    }

    /**
     * Obtain a JMSQueueManager.
     * @param factoryName The fully qualified class name of the InitialContextFactory.
     * @param providerURL The URL of the provider to use.
     * @param urlPkgPrefixes A colon-separated list of package prefixes for the class name of the factory class that
     * will create a URL context factory
     * @param securityPrincipalName The name of the identity of the Principal.
     * @param securityCredentials The security credentials of the Principal.
     * @param factoryBindingName The name to locate in the Context that provides the QueueConnectionFactory.
     * @param queueBindingName The name to use to locate the Queue.
     * @param userName The userid to use to create the Queue Connection.
     * @param password The password to use to create the Queue Connection.
     * @return The JMSQueueManager.
     */
    public static JMSQueueManager getJMSQueueManager(String factoryName, String providerURL, String urlPkgPrefixes,
                                                     String securityPrincipalName, String securityCredentials,
                                                     String factoryBindingName, String queueBindingName,
                                                     String userName, String password) {

        if (factoryBindingName == null) {
            LOGGER.error("No factory name provided for JMSQueueManager");
            return null;
        }
        if (queueBindingName == null) {
            LOGGER.error("No topic name provided for JMSQueueManager");
            return null;
        }

        String name = "JMSQueue:" + factoryBindingName + '.' + queueBindingName;
        return getManager(name, factory, new FactoryData(factoryName, providerURL, urlPkgPrefixes,
            securityPrincipalName, securityCredentials, factoryBindingName, queueBindingName, userName, password));
    }

    @Override
    public synchronized void send(Serializable object) throws Exception {
        if (info == null) {
            info = connect(context, factoryBindingName, queueBindingName, userName, password, false);
        }
        super.send(object, info.session, info.sender);
    }

    @Override
    public void releaseSub() {
        try {
            if (info != null) {
                info.session.close();
                info.conn.close();
            }
        } catch (JMSException ex) {
            LOGGER.error("Error closing " + getName(), ex);
        } finally {
            info = null;
        }
    }

    /**
     * Data for the factory.
     */
    private static class FactoryData {
        private String factoryName;
        private String providerURL;
        private String urlPkgPrefixes;
        private String securityPrincipalName;
        private String securityCredentials;
        private String factoryBindingName;
        private String queueBindingName;
        private String userName;
        private String password;

        public FactoryData(String factoryName, String providerURL, String urlPkgPrefixes, String securityPrincipalName,
                           String securityCredentials, String factoryBindingName, String queueBindingName,
                           String userName, String password) {
            this.factoryName = factoryName;
            this.providerURL = providerURL;
            this.urlPkgPrefixes = urlPkgPrefixes;
            this.securityPrincipalName = securityPrincipalName;
            this.securityCredentials = securityCredentials;
            this.factoryBindingName = factoryBindingName;
            this.queueBindingName = queueBindingName;
            this.userName = userName;
            this.password = password;
        }
    }

    private static QueueInfo connect(Context context, String factoryBindingName, String queueBindingName,
                                     String userName, String password, boolean suppress) throws Exception {
        try {
            QueueConnectionFactory factory = (QueueConnectionFactory) lookup(context, factoryBindingName);
            QueueConnection conn;
            if (userName != null) {
                conn = factory.createQueueConnection(userName, password);
            } else {
                conn = factory.createQueueConnection();
            }
            QueueSession sess = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = (Queue) lookup(context, queueBindingName);
            QueueSender sender = sess.createSender(queue);
            conn.start();
            return new QueueInfo(conn, sess, sender);
        } catch (NamingException ex) {
            LOGGER.warn("Unable to locate connection factory " + factoryBindingName, ex);
            if (!suppress) {
                throw ex;
            }
        } catch (JMSException ex) {
            LOGGER.warn("Unable to create connection to queue " + queueBindingName, ex);
            if (!suppress) {
                throw ex;
            }
        }
        return null;
    }

    private static class QueueInfo {
        private final QueueConnection conn;
        private final QueueSession session;
        private final QueueSender sender;

        public QueueInfo(QueueConnection conn, QueueSession session, QueueSender sender) {
            this.conn = conn;
            this.session = session;
            this.sender = sender;
        }
    }

    /**
     * Factory to create the JMSQueueManager.
     */
    private static class JMSQueueManagerFactory implements ManagerFactory<JMSQueueManager, FactoryData> {

        public JMSQueueManager createManager(String name, FactoryData data) {
            try {
                Context ctx = createContext(data.factoryName, data.providerURL, data.urlPkgPrefixes,
                                            data.securityPrincipalName, data.securityCredentials);
                QueueInfo info = connect(ctx, data.factoryBindingName, data.queueBindingName, data.userName,
                    data.password, true);
                return new JMSQueueManager(name, ctx, data.factoryBindingName, data.queueBindingName,
                    data.userName, data.password, info);
            } catch (NamingException ex) {
                LOGGER.error("Unable to locate resource", ex);
            } catch (Exception ex) {
                LOGGER.error("Unable to connect", ex);
            }

            return null;
        }
    }
}
