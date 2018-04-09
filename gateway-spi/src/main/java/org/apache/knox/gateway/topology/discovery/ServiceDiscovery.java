/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery;

import java.util.List;
import java.util.Map;


/**
 * Implementations provide the means by which Hadoop service endpoint URLs are discovered from a source with knowledge
 * about the service topology of one or more clusters.
 */
public interface ServiceDiscovery {

    /**
     * This is the type specified in a simple descriptor to indicate which ServiceDiscovery implementation to employ.
     *
     * @return The identifier for the service discovery type.
     */
    String getType();


    /**
     * Discover details of all the clusters known to the target registry.
     *
     * @param config The configuration for the discovery invocation
     *
     * @return A Map of the discovered service data, keyed by the cluster name.
     */
    Map<String, Cluster> discover(ServiceDiscoveryConfig config);


    /**
     * Discover details for a single cluster.
     *
     * @param config The configuration for the discovery invocation
     * @param clusterName The name of a particular cluster
     *
     * @return The discovered service data for the specified cluster
     */
    Cluster discover(ServiceDiscoveryConfig config, String clusterName);


    /**
     * A handle to the service discovery result.
     */
    interface Cluster {

        /**
         * @return The name of the cluster
         */
        String getName();

        /**
         * @param serviceName The name of the service
         * @return The URLs for the specified service in this cluster.
         */
        List<String> getServiceURLs(String serviceName);
    }


}
