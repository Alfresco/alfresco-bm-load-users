/*
 * #%L
 * Alfresco Enterprise Repository
 * %%
 * Copyright (C) 2005 - 2018 Alfresco Software Limited
 * %%
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 * #L%
 */
package org.alfresco.bm.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;

@ComponentScan({ "org.alfresco.bm.user", "org.alfresco.rest", "org.alfresco.utility" })
@SpringBootApplication
@ImportResource({ "classpath:config/spring/app-context.xml", "dataprep-context.xml" })
@PropertySource({ "classpath:config/startup/app.properties", "classpath:config/startup/mongo.properties", "classpath:dataprep.properties" })
public class Application
{
    public static void main(String[] args)
    {
        SpringApplication.run(Application.class, args);
    }
}