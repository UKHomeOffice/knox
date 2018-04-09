/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.topology.simple;

import org.apache.knox.gateway.topology.validation.TopologyValidator;
import org.apache.knox.gateway.util.XmlUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.easymock.EasyMock;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class SimpleDescriptorHandlerTest {

    private static final String TEST_PROVIDER_CONFIG =
            "    <gateway>\n" +
                    "        <provider>\n" +
                    "            <role>authentication</role>\n" +
                    "            <name>ShiroProvider</name>\n" +
                    "            <enabled>true</enabled>\n" +
                    "            <param>\n" +
                    "                <!-- \n" +
                    "                session timeout in minutes,  this is really idle timeout,\n" +
                    "                defaults to 30mins, if the property value is not defined,, \n" +
                    "                current client authentication would expire if client idles contiuosly for more than this value\n" +
                    "                -->\n" +
                    "                <name>sessionTimeout</name>\n" +
                    "                <value>30</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapRealm</name>\n" +
                    "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapContextFactory</name>\n" +
                    "                <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapRealm.contextFactory</name>\n" +
                    "                <value>$ldapContextFactory</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapRealm.userDnTemplate</name>\n" +
                    "                <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapRealm.contextFactory.url</name>\n" +
                    "                <value>ldap://localhost:33389</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>\n" +
                    "                <value>simple</value>\n" +
                    "            </param>\n" +
                    "            <param>\n" +
                    "                <name>urls./**</name>\n" +
                    "                <value>authcBasic</value>\n" +
                    "            </param>\n" +
                    "        </provider>\n" +
                    "\n" +
                    "        <provider>\n" +
                    "            <role>identity-assertion</role>\n" +
                    "            <name>Default</name>\n" +
                    "            <enabled>true</enabled>\n" +
                    "        </provider>\n" +
                    "\n" +
                    "        <!--\n" +
                    "        Defines rules for mapping host names internal to a Hadoop cluster to externally accessible host names.\n" +
                    "        For example, a hadoop service running in AWS may return a response that includes URLs containing the\n" +
                    "        some AWS internal host name.  If the client needs to make a subsequent request to the host identified\n" +
                    "        in those URLs they need to be mapped to external host names that the client Knox can use to connect.\n" +
                    "\n" +
                    "        If the external hostname and internal host names are same turn of this provider by setting the value of\n" +
                    "        enabled parameter as false.\n" +
                    "\n" +
                    "        The name parameter specifies the external host names in a comma separated list.\n" +
                    "        The value parameter specifies corresponding internal host names in a comma separated list.\n" +
                    "\n" +
                    "        Note that when you are using Sandbox, the external hostname needs to be localhost, as seen in out\n" +
                    "        of box sandbox.xml.  This is because Sandbox uses port mapping to allow clients to connect to the\n" +
                    "        Hadoop services using localhost.  In real clusters, external host names would almost never be localhost.\n" +
                    "        -->\n" +
                    "        <provider>\n" +
                    "            <role>hostmap</role>\n" +
                    "            <name>static</name>\n" +
                    "            <enabled>true</enabled>\n" +
                    "            <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>\n" +
                    "        </provider>\n" +
                    "    </gateway>\n";


    /**
     * KNOX-1006
     *
     * N.B. This test depends on the PropertiesFileServiceDiscovery extension being configured:
     *             org.apache.knox.gateway.topology.discovery.test.extension.PropertiesFileServiceDiscovery
     */
    @Test
    public void testSimpleDescriptorHandler() throws Exception {

        final String type = "PROPERTIES_FILE";
        final String clusterName = "dummy";

        // Create a properties file to be the source of service discovery details for this test
        final File discoveryConfig = File.createTempFile(getClass().getName() + "_discovery-config", ".properties");

        final String address = discoveryConfig.getAbsolutePath();

        final Properties DISCOVERY_PROPERTIES = new Properties();
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".name", clusterName);
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".NAMENODE", "hdfs://namenodehost:8020");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".JOBTRACKER", "rpc://jobtrackerhostname:8050");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHDFS", "http://webhdfshost:1234");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHCAT", "http://webhcathost:50111/templeton");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".OOZIE", "http://ooziehost:11000/oozie");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".WEBHBASE", "http://webhbasehost:1234");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".HIVE", "http://hivehostname:10001/clipath");
        DISCOVERY_PROPERTIES.setProperty(clusterName + ".RESOURCEMANAGER", "http://remanhost:8088/ws");

        try {
            DISCOVERY_PROPERTIES.store(new FileOutputStream(discoveryConfig), null);
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        }

        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("JOBTRACKER", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("WEBHCAT", null);
        serviceURLs.put("OOZIE", null);
        serviceURLs.put("WEBHBASE", null);
        serviceURLs.put("HIVE", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));
        serviceURLs.put("KNOXSSO", null);

        // Write the externalized provider config to a temp file
        File providerConfig = new File(System.getProperty("java.io.tmpdir"), "ambari-cluster-policy.xml");
        FileUtils.write(providerConfig, TEST_PROVIDER_CONFIG);

        File topologyFile = null;
        try {
            File destDir = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile();

            Map<String, Map<String, String>> serviceParameters = new HashMap<>();
            Map<String, String> knoxssoParams = new HashMap<>();
            knoxssoParams.put("knoxsso.cookie.secure.only", "true");
            knoxssoParams.put("knoxsso.token.ttl", "100000");
            serviceParameters.put("KNOXSSO", knoxssoParams);

            // Mock out the simple descriptor
            SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
            EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(type).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
            EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
            EasyMock.expect(testDescriptor.getClusterName()).andReturn(clusterName).anyTimes();
            List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
            for (String serviceName : serviceURLs.keySet()) {
                SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
                EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
                EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
                EasyMock.expect(svc.getParams()).andReturn(serviceParameters.get(serviceName)).anyTimes();
                EasyMock.replay(svc);
                serviceMocks.add(svc);
            }
            EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
            EasyMock.replay(testDescriptor);

            // Invoke the simple descriptor handler
            Map<String, File> files =
                           SimpleDescriptorHandler.handle(testDescriptor,
                                                          providerConfig.getParentFile(), // simple desc co-located with provider config
                                                          destDir);
            topologyFile = files.get("topology");

            // Validate the resulting topology descriptor
            assertTrue(topologyFile.exists());

            // Validate the topology descriptor's correctness
            TopologyValidator validator = new TopologyValidator( topologyFile.getAbsolutePath() );
            if( !validator.validateTopology() ){
                throw new SAXException( validator.getErrorString() );
            }

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Parse the topology descriptor
            Document topologyXml = XmlUtils.readXml(topologyFile);

            // KNOX-1105 Mark generated topology files
            assertThat("Expected the \"generated\" marker element in the topology XML, with value of \"true\".",
                       topologyXml,
                       hasXPath("/topology/generated", is("true")));

            // Validate the provider configuration
            Document extProviderConf = XmlUtils.readXml(new ByteArrayInputStream(TEST_PROVIDER_CONFIG.getBytes()));
            Node gatewayNode = (Node) xpath.compile("/topology/gateway").evaluate(topologyXml, XPathConstants.NODE);
            assertTrue("Resulting provider config should be identical to the referenced content.",
                       extProviderConf.getDocumentElement().isEqualNode(gatewayNode));

            // Validate the service declarations
            Map<String, List<String>> topologyServiceURLs = new HashMap<>();
            NodeList serviceNodes =
                        (NodeList) xpath.compile("/topology/service").evaluate(topologyXml, XPathConstants.NODESET);
            for (int serviceNodeIndex=0; serviceNodeIndex < serviceNodes.getLength(); serviceNodeIndex++) {
                Node serviceNode = serviceNodes.item(serviceNodeIndex);

                // Validate the role
                Node roleNode = (Node) xpath.compile("role/text()").evaluate(serviceNode, XPathConstants.NODE);
                assertNotNull(roleNode);
                String role = roleNode.getNodeValue();

                // Validate the URLs
                NodeList urlNodes = (NodeList) xpath.compile("url/text()").evaluate(serviceNode, XPathConstants.NODESET);
                for(int urlNodeIndex = 0 ; urlNodeIndex < urlNodes.getLength(); urlNodeIndex++) {
                    Node urlNode = urlNodes.item(urlNodeIndex);
                    assertNotNull(urlNode);
                    String url = urlNode.getNodeValue();

                    // If the service should have a URL (some don't require it)
                    if (serviceURLs.containsKey(role)) {
                        assertNotNull("Declared service should have a URL.", url);
                        if (!topologyServiceURLs.containsKey(role)) {
                            topologyServiceURLs.put(role, new ArrayList<>());
                        }
                        topologyServiceURLs.get(role).add(url); // Add it for validation later
                    }
                }

                // If params were declared in the descriptor, then validate them in the resulting topology file
                Map<String, String> params = serviceParameters.get(role);
                if (params != null) {
                    NodeList paramNodes = (NodeList) xpath.compile("param").evaluate(serviceNode, XPathConstants.NODESET);
                    for (int paramNodeIndex = 0; paramNodeIndex < paramNodes.getLength(); paramNodeIndex++) {
                        Node paramNode = paramNodes.item(paramNodeIndex);
                        String paramName = (String) xpath.compile("name/text()").evaluate(paramNode, XPathConstants.STRING);
                        String paramValue = (String) xpath.compile("value/text()").evaluate(paramNode, XPathConstants.STRING);
                        assertTrue(params.keySet().contains(paramName));
                        assertEquals(params.get(paramName), paramValue);
                    }
                }

            }
            assertEquals("Unexpected number of service declarations.", (serviceURLs.size() - 1), topologyServiceURLs.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            providerConfig.delete();
            discoveryConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }


    /**
     * KNOX-1006
     *
     * Verify the behavior of the SimpleDescriptorHandler when service discovery fails to produce a valid URL for
     * a service.
     *
     * N.B. This test depends on the PropertiesFileServiceDiscovery extension being configured:
     *             org.apache.knox.gateway.topology.discovery.test.extension.PropertiesFileServiceDiscovery
     */
    @Test
    public void testInvalidServiceURLFromDiscovery() throws Exception {
        final String CLUSTER_NAME = "myproperties";

        // Configure the PropertiesFile Service Discovery implementation for this test
        final String DEFAULT_VALID_SERVICE_URL = "http://localhost:9999/thiswillwork";
        Properties serviceDiscoverySourceProps = new Properties();
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".NAMENODE",
                                                DEFAULT_VALID_SERVICE_URL.replace("http", "hdfs"));
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".JOBTRACKER",
                                                DEFAULT_VALID_SERVICE_URL.replace("http", "rpc"));
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".WEBHDFS",         DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".WEBHCAT",         DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".OOZIE",           DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".WEBHBASE",        DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".HIVE",            "{SCHEME}://localhost:10000/");
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".RESOURCEMANAGER", DEFAULT_VALID_SERVICE_URL);
        serviceDiscoverySourceProps.setProperty(CLUSTER_NAME + ".AMBARIUI",        DEFAULT_VALID_SERVICE_URL);
        File serviceDiscoverySource = File.createTempFile("service-discovery", ".properties");
        serviceDiscoverySourceProps.store(new FileOutputStream(serviceDiscoverySource),
                                          "Test Service Discovery Source");

        // Prepare a mock SimpleDescriptor
        final String type = "PROPERTIES_FILE";
        final String address = serviceDiscoverySource.getAbsolutePath();
        final Map<String, List<String>> serviceURLs = new HashMap<>();
        serviceURLs.put("NAMENODE", null);
        serviceURLs.put("JOBTRACKER", null);
        serviceURLs.put("WEBHDFS", null);
        serviceURLs.put("WEBHCAT", null);
        serviceURLs.put("OOZIE", null);
        serviceURLs.put("WEBHBASE", null);
        serviceURLs.put("HIVE", null);
        serviceURLs.put("RESOURCEMANAGER", null);
        serviceURLs.put("AMBARIUI", Collections.singletonList("http://c6401.ambari.apache.org:8080"));

        // Write the externalized provider config to a temp file
        File providerConfig = writeProviderConfig("ambari-cluster-policy.xml", TEST_PROVIDER_CONFIG);

        File topologyFile = null;
        try {
            File destDir = (new File(".")).getCanonicalFile();

            // Mock out the simple descriptor
            SimpleDescriptor testDescriptor = EasyMock.createNiceMock(SimpleDescriptor.class);
            EasyMock.expect(testDescriptor.getName()).andReturn("mysimpledescriptor").anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryAddress()).andReturn(address).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryType()).andReturn(type).anyTimes();
            EasyMock.expect(testDescriptor.getDiscoveryUser()).andReturn(null).anyTimes();
            EasyMock.expect(testDescriptor.getProviderConfig()).andReturn(providerConfig.getAbsolutePath()).anyTimes();
            EasyMock.expect(testDescriptor.getClusterName()).andReturn(CLUSTER_NAME).anyTimes();
            List<SimpleDescriptor.Service> serviceMocks = new ArrayList<>();
            for (String serviceName : serviceURLs.keySet()) {
                SimpleDescriptor.Service svc = EasyMock.createNiceMock(SimpleDescriptor.Service.class);
                EasyMock.expect(svc.getName()).andReturn(serviceName).anyTimes();
                EasyMock.expect(svc.getURLs()).andReturn(serviceURLs.get(serviceName)).anyTimes();
                EasyMock.replay(svc);
                serviceMocks.add(svc);
            }
            EasyMock.expect(testDescriptor.getServices()).andReturn(serviceMocks).anyTimes();
            EasyMock.replay(testDescriptor);

            // Invoke the simple descriptor handler
            Map<String, File> files =
                    SimpleDescriptorHandler.handle(testDescriptor,
                                                   providerConfig.getParentFile(), // simple desc co-located with provider config
                                                   destDir);

            topologyFile = files.get("topology");

            // Validate the resulting topology descriptor
            assertTrue(topologyFile.exists());

            // Validate the topology descriptor's correctness
            TopologyValidator validator = new TopologyValidator( topologyFile.getAbsolutePath() );
            if( !validator.validateTopology() ){
                throw new SAXException( validator.getErrorString() );
            }

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Parse the topology descriptor
            Document topologyXml = XmlUtils.readXml(topologyFile);

            // Validate the provider configuration
            Document extProviderConf = XmlUtils.readXml(new ByteArrayInputStream(TEST_PROVIDER_CONFIG.getBytes()));
            Node gatewayNode = (Node) xpath.compile("/topology/gateway").evaluate(topologyXml, XPathConstants.NODE);
            assertTrue("Resulting provider config should be identical to the referenced content.",
                    extProviderConf.getDocumentElement().isEqualNode(gatewayNode));

            // Validate the service declarations
            List<String> topologyServices = new ArrayList<>();
            Map<String, List<String>> topologyServiceURLs = new HashMap<>();
            NodeList serviceNodes =
                    (NodeList) xpath.compile("/topology/service").evaluate(topologyXml, XPathConstants.NODESET);
            for (int serviceNodeIndex=0; serviceNodeIndex < serviceNodes.getLength(); serviceNodeIndex++) {
                Node serviceNode = serviceNodes.item(serviceNodeIndex);
                Node roleNode = (Node) xpath.compile("role/text()").evaluate(serviceNode, XPathConstants.NODE);
                assertNotNull(roleNode);
                String role = roleNode.getNodeValue();
                topologyServices.add(role);
                NodeList urlNodes = (NodeList) xpath.compile("url/text()").evaluate(serviceNode, XPathConstants.NODESET);
                for(int urlNodeIndex = 0 ; urlNodeIndex < urlNodes.getLength(); urlNodeIndex++) {
                    Node urlNode = urlNodes.item(urlNodeIndex);
                    assertNotNull(urlNode);
                    String url = urlNode.getNodeValue();
                    assertNotNull("Every declared service should have a URL.", url);
                    if (!topologyServiceURLs.containsKey(role)) {
                        topologyServiceURLs.put(role, new ArrayList<>());
                    }
                    topologyServiceURLs.get(role).add(url);
                }
            }

            // There should not be a service element for HIVE, since it had no valid URLs
            assertEquals("Unexpected number of service declarations.", serviceURLs.size() - 1, topologyServices.size());
            assertFalse("The HIVE service should have been omitted from the generated topology.", topologyServices.contains("HIVE"));

            assertEquals("Unexpected number of service URLs.", serviceURLs.size() - 1, topologyServiceURLs.size());

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            serviceDiscoverySource.delete();
            providerConfig.delete();
            if (topologyFile != null) {
                topologyFile.delete();
            }
        }
    }


    private File writeProviderConfig(String path, String content) throws IOException {
        File f = new File(path);
        FileUtils.write(f, content);
        return f;
    }

}
