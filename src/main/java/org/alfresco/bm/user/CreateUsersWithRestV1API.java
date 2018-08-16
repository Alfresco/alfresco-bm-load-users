/*
 * #%L
 * Alfresco Benchmark Load Users
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

import org.alfresco.bm.common.EventResult;
import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.driver.event.AbstractEventProcessor;
import org.alfresco.bm.driver.event.Event;
import org.alfresco.rest.core.RestWrapper;
import org.alfresco.rest.model.RestGroupMember;
import org.alfresco.rest.model.RestPersonModel;
import org.alfresco.utility.data.DataUser;
import org.alfresco.utility.model.UserModel;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;

import com.jayway.restassured.RestAssured;

import javax.json.Json;
import javax.json.JsonObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

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
 * The user is created in alfresco through V1 REST using the TAS rest api library.
 * When user already existed in alfresco when processed and
 * 'ignoreExistingUsers' property is set to true, the event is considered
 * processed successfully instead of throwing an exception.
 * <p/>
 * <h1>Output</h1>
 * No next event will be scheduled.
 */
public class CreateUsersWithRestV1API extends AbstractEventProcessor implements ApplicationContextAware
{
    private UserDataService userDataService;
    private boolean ignoreExistingUsers = false;
    private String userGroups;
    private Map<String, Double> userGroupsMap;
    private String alfrescoBaseUrl;
    private Integer alfrescoPort;
    private DataUser dataUser;
    private RestWrapper restClient;
    private UserModel adminUser;

    private ApplicationContext context;

    public CreateUsersWithRestV1API(String alfrescoUrl) throws MalformedURLException 
    {
        URL url = new URL(alfrescoUrl);
        this.alfrescoBaseUrl = url.getProtocol() + "://" + url.getHost();
        this.alfrescoPort = url.getPort();
    }

    @Override
    protected EventResult processEvent(Event event) throws Exception
    {
        super.suspendTimer();

        if (userGroupsMap == null)
        {
            initializeUserGroupsMap();
        }
        if (restClient == null)
        {
            initializeRestClient();
        }

        String username = (String) event.getData();

        EventResult eventResult = null;

        // Look up the user data
        UserData user = userDataService.findUserByUsername(username);
        if (user == null)
        {
            // User already existed
            eventResult = new EventResult("User data not found in local database: " + username, Collections.EMPTY_LIST, false);
            return eventResult;
        }
        // Assign random groups
        List<String> groups = getRandomGroups();

        try
        {
            RestPersonModel personModel = RestPersonModel.getRandomPersonModel();
            personModel.setEmail(user.getEmail());
            personModel.setFirstName(user.getFirstName());
            personModel.setLastName(user.getLastName());
            personModel.setPassword(user.getPassword());
            personModel.setId(username);
            personModel.setAvatarId(null);
            personModel.setStatusUpdatedAt(null);
            personModel.setAspectNames(null);

            // Restart timer
            super.resumeTimer();
            personModel = restClient.authenticateUser(adminUser).withCoreAPI().usingAuthUser().createPerson(personModel);
            String code = restClient.getStatusCode();
            super.suspendTimer();

            if (HttpStatus.CREATED.toString().equals(code))
            {
                //associate user with some groups.
                handleGroupsAssociation(username, groups, restClient);

                //success, created the user
                return markAsSuccess(username);
            }
            else if (HttpStatus.CONFLICT.toString().equals(code))
            {
                if (isIgnoreExistingUsers())
                {
                    // user already exists, but we don't care, so... success
                    return markAsSuccess(username);
                }
                else
                {
                    // user already exists and we consider this a problem, failed
                    return markAsFailure(username);
                }
            }
            else
            {
                // failed
                return markAsFailure(username);
            }
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
            return markAsFailure(username);
        }
    }

    private void initializeRestClient()
    {
        dataUser = (DataUser) this.context.getBean("dataUser");
        restClient = (RestWrapper) this.context.getBean("restWrapper");
        RestAssured.baseURI = alfrescoBaseUrl;
        RestAssured.port = alfrescoPort;
        restClient.configureRequestSpec().setBaseUri(alfrescoBaseUrl).setPort(alfrescoPort);
        adminUser = dataUser.getAdminUser();
    }

    private void handleGroupsAssociation(String username, List<String> groups, RestWrapper restClient)
    {
        // NOTE that this code currently does not create the missing groups. It assumes they are present on the Alfresco system
        // failing to associate the user with a group is not considered a problem
        for (String group : groups)
        {
            try
            {
                createUserMembership(username, restClient, group);
            }
            catch (Exception e)
            {
                // just log it, we don't care that much
                logger.error("error adding user to a group: " + group + " message: " + e.getMessage(), e);
            }
        }
    }

    private void createUserMembership(String username, RestWrapper restClient, String group) throws Exception
    {
        JsonObject groupMembershipBody = Json.createObjectBuilder().add("id", username).add("memberType", "PERSON").build();
        String groupMembershipBodyCreate = groupMembershipBody.toString();
        //MembershipCreation
        super.resumeTimer();
        RestGroupMember groupMembership = restClient.withCoreAPI().usingGroups().createGroupMembership("GROUP_" + group, groupMembershipBodyCreate);
        String createGroupCode = restClient.getStatusCode();
        super.suspendTimer();

        if (HttpStatus.CREATED.toString().equals(createGroupCode))
        {
            //log this as success
            logger.info("User: " + username + " added to group: " + group);
        }
        else
        {
            //log this as failure
            logger.warn("FAILED to add user: " + username + " to group: " + group + ". Make sure this group is created on your Alfresco system!");
        }
    }

    private EventResult markAsSuccess(String username)
    {
        userDataService.setUserCreationState(username, DataCreationState.Created);
        return new EventResult("User created in Alfresco:" + username, Collections.EMPTY_LIST, true);
    }

    private EventResult markAsFailure(String username)
    {
        userDataService.setUserCreationState(username, DataCreationState.Failed);
        return new EventResult("Failed to create user:" + username, false);
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
     *
     * @throws IllegalArgumentException if the userGroups string is not well-formed
     */
    void initializeUserGroupsMap()
    {
        if (userGroups == null)
        {
            throw new IllegalArgumentException("'userGroups' may not be null.");
        }

        if (userGroupsMap == null)
        {
            userGroupsMap = new HashMap<>();
        }

        // Split by comma
        StringTokenizer commaTokenizer = new StringTokenizer(userGroups, ",");
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
            userGroupsMap.put(group, chance);
        }
    }

    /**
     * Using the current {@link #setUserGroups(String) user group chances}, generate a set of random groups
     * according to the chances.
     */
    public List<String> getRandomGroups()
    {
        List<String> groups = new ArrayList<String>(5);
        for (Map.Entry<String, Double> groupChance : userGroupsMap.entrySet())
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

    public UserDataService getUserDataService()
    {
        return userDataService;
    }

    public void setUserDataService(UserDataService userDataService)
    {
        this.userDataService = userDataService;
    }

    public boolean isIgnoreExistingUsers()
    {
        return ignoreExistingUsers;
    }

    public void setIgnoreExistingUsers(boolean ignoreExistingUsers)
    {
        this.ignoreExistingUsers = ignoreExistingUsers;
    }

    public String getUserGroups()
    {
        return userGroups;
    }

    public void setUserGroups(String userGroups)
    {
        this.userGroups = userGroups;
    }

    public Map<String, Double> getUserGroupsMap()
    {
        return userGroupsMap;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        this.context = applicationContext;
    }
}
