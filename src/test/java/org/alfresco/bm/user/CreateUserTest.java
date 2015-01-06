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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.alfresco.http.AuthenticationDetailsProvider;
import org.alfresco.http.HttpClientProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * Run the <b>Enterprise Signup</b> tests using the default arguments.
 * 
 * @author Derek Hulley
 * @since 2.1
 */
@RunWith(JUnit4.class)
public class CreateUserTest
{
    private CreateUser createUser;
    
    @Before
    public void setUp()
    {
        HttpClientProvider httpClientProvider = Mockito.mock(HttpClientProvider.class);
        AuthenticationDetailsProvider authenticationDetailsProvider = Mockito.mock(AuthenticationDetailsProvider.class);
        String baseUrl = "http://localhost:8080/";
        UserDataService userDataService = Mockito.mock(UserDataService.class);
        
        createUser = new CreateUser(httpClientProvider, authenticationDetailsProvider, baseUrl, userDataService);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGroupsNull()
    {
        createUser.setUserGroups(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGroupsNonNumericChance()
    {
        createUser.setUserGroups("A:x");
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testGroupsEmpty()
    {
        createUser.setUserGroups("");
        assertEquals(0, createUser.getUserGroups().size());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testGroupsNoChance()
    {
        createUser.setUserGroups("A: , B: ");
        assertEquals(2, createUser.getUserGroups().size());
        assertEquals(1.0, createUser.getUserGroups().get("A"), 0.01);
        assertEquals(1.0, createUser.getUserGroups().get("B"), 0.01);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testGroupsWithChances()
    {
        createUser.setUserGroups("A : 0.05 , B : 0.8 , C:1.5,D:-0.5");
        assertEquals(4, createUser.getUserGroups().size());
        assertEquals(0.05, createUser.getUserGroups().get("A"), 0.01);
        assertEquals(0.80, createUser.getUserGroups().get("B"), 0.01);
        assertEquals(1.00, createUser.getUserGroups().get("C"), 0.01);
        assertEquals(0.00, createUser.getUserGroups().get("D"), 0.01);
        
        // Check that we sometimes get 3 groups, always get C and never get D
        int minGroupCount = 100;
        int maxGroupCount = 0;
        for (int i = 0; i < 1000; i++)
        {
            Set<String> groups = createUser.getRandomGroups();
            int groupCount = groups.size();
            if (groupCount > maxGroupCount)
            {
                maxGroupCount = groupCount;
            }
            if (groupCount < minGroupCount)
            {
                minGroupCount = groupCount;
            }
            // We must always get C
            assertTrue(groups.contains("C"));
            // but never D
            assertFalse(groups.contains("D"));
        }
        // Check weights
        assertEquals(1, minGroupCount);
        assertEquals(3, maxGroupCount);
    }
}
