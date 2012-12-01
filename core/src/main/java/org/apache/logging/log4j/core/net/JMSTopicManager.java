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
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.NamingException;
import java.io.Serializable;

/**
 * Manager for JMS Topic connections.
 */
public class JMSTopicManager extends AbstractJMSManager {

    private static final JMSTopicManagerFactory factory = new JMSTopicManagerFactory();

    private TopicInfo info;
    private final String factoryBindingName;
    private final String topicBindingName;
    private final String userName;
    private final String password;
    private final Context context;
    /**
     * Constructor.
     * @param name The unique name of the connection.
     * @param context The context.
     * @param factoryBindingName The factory binding name.
     * @param topicBindingName The queue binding name.
     * @param userName The user name.
     * @param password The credentials for the user.
     * @param info The Queue connection info.
     */
    protected JMSTopicManager(String name, Context context, String factoryBindingName, String topicBindingName,
                              String userName, String password, TopicInfo info) {
        super(name);
        this.context = context;
        this.factoryBindingName = factoryBindingName;
        this.topicBindingName = topicBindingName;
        this.userName = userName;
        this.password = password;
        this.info = info;
    }

    /**
     * Obtain a JSMTopicManager.
     * @param factoryName The fully qualified class name of the InitialContextFactory.
     * @param providerURL The URL of the provider to use.
     * @param urlPkgPrefixes A colon-separated list of package prefixes for the class name of the factory class that
     * will create a URL context factory
     * @param securityPrincipalName The name of the identity of the Principal.
     * @param securityCredentials The security credentials of the Principal.
     * @param factoryBindingName The name to locate in the Context that provides the TopicConnectionFactory.
     * @param topicBindingName The name to use to locate the Topic.
     * @param userName The userid to use to create the Topic Connection.
     * @param password The password to use to create the Topic Connection.
     * @return A JMSTopicManager.
     */
    public static JMSTopicManager getJMSTopicManager(String factoryName, String providerURL, String urlPkgPrefixes,
                                                     String securityPrincipalName, String securityCredentials,
                                                     String factoryBindingName, String topicBindingName,
                                                     String userName, String password) {

        if (factoryBindingName == null) {
            LOGGER.error("No factory name provided for JMSTopicManager");
            return null;
        }
        if (topicBindingName == null) {
            LOGGER.error("No topic name provided for JMSTopicManager");
            return null;
        }

        String name = "JMSTopic:" + factoryBindingName + '.' + topicBindingName;
        return getManager(name, factory, new FactoryData(factoryName, providerURL, urlPkgPrefixes,
            securityPrincipalName, securityCredentials, factoryBindingName, topicBindingName, userName, password));
    }


    @Override
    public void send(Serializable object) throws Exception {
        if (info == null) {
            info = connect(context, factoryBindingName, topicBindingName, userName, password, false);
        }
        super.send(object, info.session, info.publisher);
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
        private String topicBindingName;
        private String userName;
        private String password;

        public FactoryData(String factoryName, String providerURL, String urlPkgPrefixes, String securityPrincipalName,
                           String securityCredentials, String factoryBindingName, String topicBindingName,
                           String userName, String password) {
            this.factoryName = factoryName;
            this.providerURL = providerURL;
            this.urlPkgPrefixes = urlPkgPrefixes;
            this.securityPrincipalName = securityPrincipalName;
            this.securityCredentials = securityCredentials;
            this.factoryBindingName = factoryBindingName;
            this.topicBindingName = topicBindingName;
            this.userName = userName;
            this.password = password;
        }
    }

    private static TopicInfo connect(Context context, String factoryBindingName, String queueBindingName,
                                     String userName, String password, boolean suppress) throws Exception {
        try {
            TopicConnectionFactory factory = (TopicConnectionFactory) lookup(context, factoryBindingName);
            TopicConnection conn;
            if (userName != null) {
                conn = factory.createTopicConnection(userName, password);
            } else {
                conn = factory.createTopicConnection();
            }
            TopicSession sess = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = (Topic) lookup(context, queueBindingName);
            TopicPublisher publisher = sess.createPublisher(topic);
            conn.start();
            return new TopicInfo(conn, sess, publisher);
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

    private static class TopicInfo {
        private final TopicConnection conn;
        private final TopicSession session;
        private final TopicPublisher publisher;

        public TopicInfo(TopicConnection conn, TopicSession session, TopicPublisher publisher) {
            this.conn = conn;
            this.session = session;
            this.publisher = publisher;
        }
    }

    /**
     * Factory to create the JMSQueueManager.
     */
    private static class JMSTopicManagerFactory implements ManagerFactory<JMSTopicManager, FactoryData> {

        public JMSTopicManager createManager(String name, FactoryData data) {
            try {
                Context ctx = createContext(data.factoryName, data.providerURL, data.urlPkgPrefixes,
                    data.securityPrincipalName, data.securityCredentials);
                TopicInfo info = connect(ctx, data.factoryBindingName, data.topicBindingName, data.userName,
                    data.password, true);
                return new JMSTopicManager(name, ctx, data.factoryBindingName, data.topicBindingName,
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
