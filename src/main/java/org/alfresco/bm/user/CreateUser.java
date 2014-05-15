/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
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
 */
package org.alfresco.bm.user;

import java.util.Collections;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
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
 * username present in the event and inserts an entry in mongo. <h1>Input</h1>
 * Username of user to create <h1>Data</h1> Collection containing users. User
 * will be marked as created and alfresco node-id will be stored. <h1>Actions</h1>
 * The user is created in alfresco through REST. An exception is thrown when
 * user creation fails. When user already existed in alfresco when processed and
 * 'ignoreExistingUsers' property is set to true, the event is considered
 * processed successfuly instead of throwing an exception. <h1>Output</h1> No
 * next event will be scheduled.
 * 
 * @author Frederik Heremans
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
    public static final String PEOPLE_JSON_NODEREF = "nodeRef";

    private UserDataService userDataService;
    private boolean ignoreExistingUsers = false;

    public CreateUser(
            HttpClientProvider httpClientProvider,
            AuthenticationDetailsProvider authenticationDetailsProvider,
            String baseUrl,
            UserDataService userDataService)
    {
        super(httpClientProvider, authenticationDetailsProvider, baseUrl);
        this.userDataService = userDataService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public EventResult processEvent(Event event) throws Exception
    {
        String username = (String) event.getDataObject();
        
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

        // Create request body containing user details
        JSONObject json = new JSONObject();
        json.put(CreateUser.PEOPLE_JSON_USERNAME, username);
        json.put(CreateUser.PEOPLE_JSON_LASTNAME, user.getLastName());
        json.put(CreateUser.PEOPLE_JSON_FIRSTNAME, user.getFirstName());
        json.put(CreateUser.PEOPLE_JSON_EMAIL, user.getEmail());
        json.put(CreateUser.PEOPLE_JSON_PASSWORD, user.getPassword());

        HttpPost createUser = new HttpPost(getFullUrlForPath(CreateUser.PEOPLE_URL));
        StringEntity content = JSONUtil.setMessageBody(json);
        createUser.setEntity(content);

        // Get the status
        HttpResponse httpResponse = executeHttpMethodAsAdmin(
                createUser,
                SimpleHttpRequestCallback.getInstance());
        StatusLine httpStatus = httpResponse.getStatusLine();

        // Expecting "OK" status
        if (httpStatus.getStatusCode() != HttpStatus.SC_OK)
        {
            if (httpStatus.getStatusCode() == HttpStatus.SC_CONFLICT && ignoreExistingUsers)
            {
                // User already existed
                eventResult = new EventResult(
                        "Ignoring existing user, already present in alfresco: " + username,
                        Collections.EMPTY_LIST);
            }
            else
            {
                throw new RuntimeException(String.format(
                        "Creating user failed, REST-call resulted in status:%d with error %s ",
                        httpStatus.getStatusCode(),
                        httpStatus.getReasonPhrase()));
            }
        }
        else
        {
            // TODO: Look in the response to see if there is a NodeRef available.
            //       See PickUserCallback in bm-wf-api project.
            // Event execution was successful
            eventResult = new EventResult("User created in alfresco: " + username, Collections.EMPTY_LIST);
        }
        // User should be usable
        userDataService.setUserCreated(username, true);

        return eventResult;
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
}
