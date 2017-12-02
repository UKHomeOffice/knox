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
package org.apache.hadoop.gateway.service.knoxtoken;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWT;
import org.apache.hadoop.gateway.util.JsonUtils;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path( TokenResource.RESOURCE_PATH )
public class TokenResource {
  private static final String EXPIRES_IN = "expires_in";
  private static final String TOKEN_TYPE = "token_type";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String TARGET_URL = "target_url";
  private static final String BEARER = "Bearer ";
  private static final String TOKEN_TTL_PARAM = "knox.token.ttl";
  private static final String TOKEN_AUDIENCES_PARAM = "knox.token.audiences";
  private static final String TOKEN_TARGET_URL = "knox.token.target.url";
  private static final String TOKEN_CLIENT_DATA = "knox.token.client.data";
  static final String RESOURCE_PATH = "knoxtoken/api/v1/token";
  private static TokenServiceMessages log = MessagesFactory.get( TokenServiceMessages.class );
  private long tokenTTL = 30000l;
  private String[] targetAudiences = null;
  private String tokenTargetUrl = null;
  private Map<String,Object> tokenClientDataMap = null;

  @Context
  private HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {

    String audiences = context.getInitParameter(TOKEN_AUDIENCES_PARAM);
    if (audiences != null) {
      targetAudiences = audiences.split(",");
    }

    String ttl = context.getInitParameter(TOKEN_TTL_PARAM);
    if (ttl != null) {
      try {
        tokenTTL = Long.parseLong(ttl);
      }
      catch (NumberFormatException nfe) {
        log.invalidTokenTTLEncountered(ttl);
      }
    }
    
    tokenTargetUrl = context.getInitParameter(TOKEN_TARGET_URL);

    String clientData = context.getInitParameter(TOKEN_CLIENT_DATA);
    if (clientData != null) {
      tokenClientDataMap = new HashMap<>();
      String[] tokenClientData = clientData.split(",");
      addClientDataToMap(tokenClientData, tokenClientDataMap);
    }
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doGet() {
    return getAuthenticationToken();
  }

  @POST
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doPost() {
    return getAuthenticationToken();
  }

  private Response getAuthenticationToken() {
    GatewayServices services = (GatewayServices) request.getServletContext()
            .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    JWTokenAuthority ts = services.getService(GatewayServices.TOKEN_SERVICE);
    Principal p = ((HttpServletRequest)request).getUserPrincipal();
    long expires = getExpiry();
    
    try {
      JWT token = null;
      if (targetAudiences == null || targetAudiences.length == 0) {
        token = ts.issueToken(p, "RS256", getExpiry());
      } else {
        ArrayList<String> aud = new ArrayList<String>();
        for (int i = 0; i < targetAudiences.length; i++) {
          aud.add(targetAudiences[i]);
        }
        token = ts.issueToken(p, aud, "RS256", expires);
      }

      if (token != null) {
        String accessToken = token.toString();
  
        HashMap<String, Object> map = new HashMap<>();
        map.put(ACCESS_TOKEN, accessToken);
        map.put(TOKEN_TYPE, BEARER);
        map.put(EXPIRES_IN, expires);
        if (tokenTargetUrl != null) {
          map.put(TARGET_URL, tokenTargetUrl);
        }
        if (tokenClientDataMap != null) {
          map.putAll(tokenClientDataMap);
        }
  
        String jsonResponse = JsonUtils.renderAsJsonString(map);

        response.getWriter().write(jsonResponse);
        return Response.ok().build();
      }
      else {
        return Response.serverError().build();
      }
    }
    catch (TokenServiceException | IOException e) {
      log.unableToIssueToken(e);
    }
    return Response.ok().entity("{ \"Unable to acquire token.\" }").build();
  }

  void addClientDataToMap(String[] tokenClientData,
      Map<String,Object> map) {
    String[] kv = null;
    for (int i = 0; i < tokenClientData.length; i++) {
      kv = tokenClientData[i].split("=");
      if (kv.length == 2) {
        map.put(kv[0], kv[1]);
      }
    }
  }

  private long getExpiry() {
    long expiry = 0l;
    if (tokenTTL == -1) {
      expiry = -1;
    }
    else {
      expiry = System.currentTimeMillis() + tokenTTL;
    }
    return expiry;
  }
}
