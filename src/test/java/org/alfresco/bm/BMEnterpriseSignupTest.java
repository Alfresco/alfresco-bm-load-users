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
package org.alfresco.bm;

import java.util.Properties;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.ResultService;
import org.alfresco.bm.test.TestRunServicesCache;
import org.alfresco.bm.tools.BMTestRunner;
import org.alfresco.bm.tools.BMTestRunnerListenerAdaptor;
import org.alfresco.mongo.MongoDBForTestsFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.context.ApplicationContext;

import com.mongodb.MongoClientURI;

/**
 * Run the <b>Enterprise Signup</b> tests using the default arguments.
 * 
 * @author Derek Hulley
 * @since 2.0
 */
@RunWith(JUnit4.class)
public class BMEnterpriseSignupTest
{
    /** In order to avoid name clashes on the target repo, we need to use a unique last name for each test */
    private Properties testProperties;
    
    @Before
    public void setUp()
    {
        testProperties = new Properties();
        testProperties.setProperty("user.lastNamePattern", "BMEnterpriseSignupTest-" + System.currentTimeMillis());
        testProperties.setProperty("user.groups", "SITE_ADMINISTRATORS:1.0");
    }
    
    @Test
    public void runDefaultSignup() throws Exception
    {
        BMTestRunner runner = new BMTestRunner(60000L);         // Should be done in 60s
        runner.addListener(new TestRunSignupListener());
        runner.run(null, null, testProperties);
    }
    
    /**
     * The Alfresco server will already contain all the default users, so modify the test to
     * generate non-default names.  The test is run twice and the results checked to ensure that,
     * on the second time, no actual user creation processes are performed.
     */
    @Test
    public void runModifiedSignupTwice() throws Exception
    {
        // For this test we need to provide the database so that it does not get closed between runs
        MongoDBForTestsFactory mongoDBForTestsFactory = new MongoDBForTestsFactory();
        String uriWithoutDB = mongoDBForTestsFactory.getMongoURIWithoutDB();
        String mongoConfigHost = new MongoClientURI(uriWithoutDB).getHosts().get(0);
        
        BMTestRunner runner = new BMTestRunner(60000L);         // Should be done in 60s
        runner.addListener(new TestRunSignupListener());
        runner.run(mongoConfigHost, null, testProperties);

        // Run a second time using exactly the same config
        runner.run(mongoConfigHost, null, testProperties);
    }
    
    /**
     * @see BMEnterpriseSignupTest#runModifiedSignupTwice()
     * 
     * @author Derek Hulley
     * @since 2.0
     */
    private class TestRunSignupListener extends BMTestRunnerListenerAdaptor
    {
        boolean firstRun = true;
        @Override
        public void testRunFinished(ApplicationContext testCtx, String test, String run)
        {
            TestRunServicesCache services = testCtx.getBean(TestRunServicesCache.class);
            ResultService resultService = services.getResultService(test, run);
            
            if (firstRun)
            {
                firstRun = false;
                // Check the first run
                Assert.assertEquals(1, resultService.countResultsByEventName(Event.EVENT_NAME_START));
            }
            else
            {
                // Check the second run
            }
        }
    }
}
