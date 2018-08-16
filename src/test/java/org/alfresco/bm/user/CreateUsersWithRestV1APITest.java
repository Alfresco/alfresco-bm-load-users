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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Run the <b>Create users</b> tests using the default arguments.
 * 
 * @author Derek Hulley
 * @since 2.1
 */
@RunWith(JUnit4.class)
public class CreateUsersWithRestV1APITest
{
    private CreateUsersWithRestV1API createUser;

    @Before
    public void setUp() throws MalformedURLException
    {
        createUser = new CreateUsersWithRestV1API();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGroupsNull()
    {
        createUser.setUserGroups(null);
        createUser.initializeUserGroupsMap();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGroupsNonNumericChance()
    {
        createUser.setUserGroups("A:x");
        createUser.initializeUserGroupsMap();
    }

    @Test
    public void testGroupsEmpty()
    {
        createUser.setUserGroups("");
        createUser.initializeUserGroupsMap();
        assertEquals(0, createUser.getUserGroupsMap().size());
    }

    @Test
    public void testGroupsNoChance()
    {
        createUser.setUserGroups("A: , B: ");
        createUser.initializeUserGroupsMap();
        assertEquals(2, createUser.getUserGroupsMap().size());
        assertEquals(1.0, createUser.getUserGroupsMap().get("A"), 0.01);
        assertEquals(1.0, createUser.getUserGroupsMap().get("B"), 0.01);
    }

    @Test
    public void testGroupsWithChances()
    {
        createUser.setUserGroups("A : 0.05 , B : 0.8 , C:1.5,D:-0.5");
        createUser.initializeUserGroupsMap();
        assertEquals(4, createUser.getUserGroupsMap().size());
        assertEquals(0.05, createUser.getUserGroupsMap().get("A"), 0.01);
        assertEquals(0.80, createUser.getUserGroupsMap().get("B"), 0.01);
        assertEquals(1.00, createUser.getUserGroupsMap().get("C"), 0.01);
        assertEquals(0.00, createUser.getUserGroupsMap().get("D"), 0.01);

        // Check that we sometimes get 3 groups, always get C and never get D
        int minGroupCount = 100;
        int maxGroupCount = 0;
        for (int i = 0; i < 1000; i++)
        {
            List<String> groups = createUser.getRandomGroups();
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
