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
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of URLManager intended for query of Zookeeper for active HBase RegionServer hosts.
 *  
 * The assumption is that the HBase REST Server will be installed on the same host.  For safety
 * reasons, the REST Server is pinged for access before inclusion in the list of returned hosts.
 * 
 * In the event of a failure via markFailed, Zookeeper is queried again for active
 * host information.
 * 
 * When configuring the HAProvider in the topology, the zookeeperEnsemble
 * attribute must be set to a comma delimited list of the host and port number,
 * i.e. host1:2181,host2:2181.
 */
public class HBaseZookeeperURLManager extends BaseZookeeperURLManager {
	/**
	 * Default Port Number for HBase REST Server
	 */
	private static final int PORT_NUMBER = 8080;
	
	private String zookeeperNamespace = "hbase-unsecure";
	
	// -------------------------------------------------------------------------------------
	// Abstract methods
	// -------------------------------------------------------------------------------------

	/**
	 * Look within Zookeeper under the /hbase-unsecure/rs branch for active HBase RegionServer hosts
	 * 
	 * @return A List of URLs (never null)
	 */
	@Override
	protected List<String> lookupURLs() {
		// Retrieve list of potential hosts from ZooKeeper
		List<String> hosts = retrieveHosts();
		
		// Validate access to hosts using cheap ping style operation
		List<String> validatedHosts = validateHosts(hosts,"/","text/xml");

		// Randomize the hosts list for simple load balancing
		if (!validatedHosts.isEmpty()) {
			Collections.shuffle(validatedHosts);
		}

		return validatedHosts;
	}

	protected String getServiceName() {
		return "WEBHBASE";
	};

	// -------------------------------------------------------------------------------------
	// Private methods
	// -------------------------------------------------------------------------------------

	/**
	 * @return Retrieve lists of hosts from ZooKeeper
	 */
	private List<String> retrieveHosts()
	{
		List<String> serverHosts = new ArrayList<>();
		
		CuratorFramework zooKeeperClient = CuratorFrameworkFactory.builder()
				.connectString(getZookeeperEnsemble())
				.retryPolicy(new ExponentialBackoffRetry(1000, 3))
				.build();
		
		try {
			zooKeeperClient.start();
			
			// Retrieve list of all region server hosts
			List<String> serverNodes = zooKeeperClient.getChildren().forPath("/" + zookeeperNamespace + "/rs");
			
			for (String serverNode : serverNodes) {
				String serverURL = constructURL(serverNode);
				serverHosts.add(serverURL);
			}
		} catch (Exception e) {
			LOG.failedToGetZookeeperUrls(e);
			throw new RuntimeException(e);
		} finally {
			// Close the client connection with ZooKeeper
			if (zooKeeperClient != null) {
				zooKeeperClient.close();
			}
		}
		
		return serverHosts;
	}
	
	/**
	 * Given a String of the format "host,number,number" convert to a URL of the format
	 * "http://host:port".
	 * 
	 * @param serverInfo Server Info from Zookeeper (required)
	 * 
	 * @return URL to HBASE
	 */
	private String constructURL(String serverInfo) {
		String scheme = "http";

		StringBuffer buffer = new StringBuffer();
		buffer.append(scheme);
		buffer.append("://");
		// Strip off the host name 
		buffer.append(serverInfo.substring(0,serverInfo.indexOf(",")));
		buffer.append(":");
		buffer.append(PORT_NUMBER);
		
		return buffer.toString();
	}
}
