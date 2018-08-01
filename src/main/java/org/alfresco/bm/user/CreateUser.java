/*
 * #%L
 * Alfresco Users Load BMF Driver
 * %%
 * Copyright (C) 2005 - 2018 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.bm.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.http.AuthenticatedHttpEventProcessor;
import org.alfresco.http.AuthenticationDetailsProvider;
import org.alfresco.http.HttpClientProvider;
import org.alfresco.http.SimpleHttpRequestCallback;
import org.alfresco.json.JSONUtil;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.simple.JSONObject;

/**
 * Event processor that creates a test-user in the alfresco-system based on the
 * username present in the event and inserts an entry in mongo.
 * <p/>
 * <h1>Input</h1><br/>
 * Username of user to create
 * <p/>
 * <h1>Data</h1><br/>
 * Collection containing users. User* will be marked as created.
 * <p/>
 * <h1>Actions</h1><br/>
 * The user is created in alfresco through REST. An exception is thrown when
 * user creation fails. When user already existed in alfresco when processed and
 * 'ignoreExistingUsers' property is set to true, the event is considered
 * processed successfuly instead of throwing an exception.
 * <p/>
 * <h1>Output</h1>
 * No next event will be scheduled.
 * 
 * @author Frederik Heremans
 * @author Derek Hulley
 * @since 1.1
 */
public class CreateUser extends AuthenticatedHttpEventProcessor
{
    /**
     * URL for people-related operations
     */
    private static final String PEOPLE_URL = "/alfresco/service/api/people";

    public static final String PEOPLE_JSON_USERNAME = "userName";
    public static final String PEOPLE_JSON_FIRSTNAME = "firstName";
    public static final String PEOPLE_JSON_LASTNAME = "lastName";
    public static final String PEOPLE_JSON_EMAIL = "email";
    public static final String PEOPLE_JSON_PASSWORD = "password";
    public static final String PEOPLE_JSON_GROUPS = "groups";
    public static final String PEOPLE_JSON_NODEREF = "nodeRef";

    private UserDataService userDataService;
    private boolean ignoreExistingUsers = false;
    private final Map<String, Double> userGroups;

    public CreateUser(
            HttpClientProvider httpClientProvider,
            AuthenticationDetailsProvider authenticationDetailsProvider,
            String baseUrl,
            UserDataService userDataService)
    {
        super(httpClientProvider, authenticationDetailsProvider, baseUrl);
        this.userDataService = userDataService;
        this.userGroups = new HashMap<String, Double>(7);
    }

    /**
     * @param ignoreExistingUsers whether or not to ignore existing users when
     *            creating. If set to true the event will be successful when
     *            executed. If set to false, an exception will be thrown when
     *            user already exists.
     */
    public void setIgnoreExistingUsers(boolean ignoreExistingUsers)
    {
        this.ignoreExistingUsers = ignoreExistingUsers;
    }
    
    /**
     * A description of the groups users should be added to with percentage chances.
     * The following string:
     * <pre>
     *    SITE_ADMINISTRATORS:0.05, DATA_ANALYSTS:0.25
     * </pre>
     * will result in users having a 5% chance of being assigned to the 'SITE_ADMINISTRATORS' group
     * and a 25% chance of being assigned to the 'DATA_ANALYSTS' group.  The group assignments are
     * always considered separately i.e. being assigned to one group does not change the chances of
     * being assigned to another group.
     * <p/>
     * <b>NOTE:</b> The {@link #PEOPLE_URL API used} requires that the group names are prepended with the
     * <b>GROUP_</b> prefix; this is done automatically and should not be specified here.  Use the group
     * names as they appear on the administrator's group management screens.
     * 
     * @param userGroupStr          a string description of groups to assign users to
     * 
     * @throws IllegalArgumentException     if the input string is not well-formed
     */
    public void setUserGroups(String userGroupStr)
    {
        if (userGroupStr == null)
        {
            throw new IllegalArgumentException("'userGroups' may not be null.");
        }
        // Split by comma
        StringTokenizer commaTokenizer = new StringTokenizer(userGroupStr, ",");
        while (commaTokenizer.hasMoreTokens())
        {
            String groupAndChance = commaTokenizer.nextToken();
            groupAndChance = groupAndChance.trim();
            StringTokenizer colonTokenizer = new StringTokenizer(groupAndChance, ":");
            double chance = 1.0;
            if (colonTokenizer.countTokens() == 0)
            {
                // Nothing here e.g. " ,,,"
                continue;
            }
            String group = colonTokenizer.nextToken().trim();
            if (group.length() == 0)
            {
                // No group name present e.g. " :0.4"
                continue;
            }
            if (colonTokenizer.hasMoreTokens())
            {
                String groupChanceStr = colonTokenizer.nextToken().trim();
                try
                {
                    chance = Double.parseDouble(groupChanceStr);
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalArgumentException("'userGroups' format is 'GROUP1:CHANCE1, GROUP2:CHANCE2' where the chances are values between 0 and 1.");
                }
            }
            // else there is no chance specified, so we assume 1.0
            if (chance > 1.0)
            {
                chance = 1.0;
            }
            else if (chance < 0.0)
            {
                chance = 0.0;
            }
            
            // Store the chance
            userGroups.put(group, chance);
        }
    }
    
    /**
     * Used for testing
     */
    public Map<String, Double> getUserGroups()
    {
        return new HashMap<String, Double>(this.userGroups);        // Copy for safety
    }
    
    /**
     * Using the current {@link #setUserGroups(String) user group chances}, generate a set of random groups
     * according to the chances.
     */
    public List<String> getRandomGroups()
    {
        List<String> groups = new ArrayList<String>(5);
        for (Map.Entry<String, Double> groupChance : userGroups.entrySet())
        {
            String group = groupChance.getKey();
            double chance = groupChance.getValue();     // unboxed but any NPE will be a bug in this class
            // See if we use this group or not
            if (Math.random() < chance)
            {
                // We can use it
                groups.add(group);
            }
        }
        return groups;
    }

    @Override
    @SuppressWarnings("unchecked")
    public EventResult processEvent(Event event) throws Exception
    {
        super.suspendTimer();
        
        String username = (String) event.getData();
        
        EventResult eventResult = null;

        // Look up the user data
        UserData user = userDataService.findUserByUsername(username);
        if (user == null)
        {
            // User already existed
            eventResult = new EventResult(
                    "User data not found in local database: " + username,
                    Collections.EMPTY_LIST,
                    false);
            return eventResult;
        }
        
        // Assign random groups
        List<String> groups = getRandomGroups();

        // Create request body containing user details
        JSONObject json = new JSONObject();
        json.put(CreateUser.PEOPLE_JSON_USERNAME, username);
        json.put(CreateUser.PEOPLE_JSON_LASTNAME, user.getLastName());
        json.put(CreateUser.PEOPLE_JSON_FIRSTNAME, user.getFirstName());
        json.put(CreateUser.PEOPLE_JSON_EMAIL, user.getEmail());
        json.put(CreateUser.PEOPLE_JSON_PASSWORD, user.getPassword());
        if (groups.size() > 0)
        {
            List<String> prefixedGroups = new ArrayList<String>(groups.size());
            for (String group : groups)
            {
                prefixedGroups.add("GROUP_" + group);
            }
            json.put(CreateUser.PEOPLE_JSON_GROUPS, prefixedGroups);
        }

        // Restart timer
        super.resumeTimer();
        HttpPost createUser = new HttpPost(getFullUrlForPath(CreateUser.PEOPLE_URL));
        StringEntity content = JSONUtil.setMessageBody(json);
        createUser.setEntity(content);

        // Get the status
        HttpResponse httpResponse = executeHttpMethodAsAdmin(
                createUser,
                SimpleHttpRequestCallback.getInstance());
        StatusLine httpStatus = httpResponse.getStatusLine();
        // Pause timer
        super.suspendTimer();

        // Expecting "OK" status
        if (httpStatus.getStatusCode() != HttpStatus.SC_OK)
        {
            if (httpStatus.getStatusCode() == HttpStatus.SC_CONFLICT && ignoreExistingUsers)
            {
                // User already existed
                eventResult = new EventResult(
                        "Ignoring existing user, already present in alfresco: " + username,
                        Collections.EMPTY_LIST);
                // User should be OK
                userDataService.setUserCreationState(username, DataCreationState.Created);
            }
            else
            {
                // User creation failed
                String msg = String.format(
                        "Creating user failed, REST-call resulted in status:%d with error %s ",
                        httpStatus.getStatusCode(),
                        httpStatus.getReasonPhrase());
                eventResult = new EventResult(msg, false);
                // User is unusable
                userDataService.setUserCreationState(username, DataCreationState.Failed);
            }
        }
        else
        {
            // Event execution was successful
            eventResult = new EventResult("User created in alfresco: " + username, Collections.EMPTY_LIST);
            // User should be usable
            userDataService.setUserCreationState(username, DataCreationState.Created);
        }

        return eventResult;
    }
}
