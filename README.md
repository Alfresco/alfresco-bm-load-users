###  Alfresco Users Load BMF Driver

This project provides the creation of a large number of users in Alfresco, using multiple email domains.
The user's details are record  in a local data mirror for reuse by subsequent tests.

More information on the <a href="https://community.alfresco.com/docs/DOC-6246">Alfresco Community Site -> Running Benchmark Applications: Alfresco Sign Up</a>
* Videos related to the <a href="https://www.youtube.com/watch?v=CXFH_1lFvsk&list=PLktNOqTikHe_Uy6UNIic0U_ga44XK0voi">Benchmark Framework 2.0</a>
* A video <a href="https://www.youtube.com/watch?v=gQjIYGU5-Kc&index=5&list=PLktNOqTikHe_Uy6UNIic0U_ga44XK0voi"> showing the creation of users and the user data mirror.</a>

### Get the code

Git:

    git clone https://github.com/Alfresco/alfresco-users-load-bmf-driver.git
    cd alfresco-users-load-bmf-driver

Subversion:

    svn checkout https://github.com/Alfresco/alfresco-users-load-bmf-driver.git
    cd alfresco-users-load-bmf-driver

### Prerequisites

Use the Benchmark Testing with Alfresco page for version compatibility.
<pre><code>
    Java 1.7.0_51 or later
    MongoDB 2.6.3 or later installed and running on port 27017 on some server: mongo-host
    A compatible version of the Benchmark Server running on a Tomcat7 at port 9080: bmserver-host
    Alfresco with /alfresco available: alfresco-host
</code></pre>
### Use Maven

1. Build
<pre><code>
    mvn clean install
</code></pre>
2. We will kick off 2 drivers with this test. Start Driver 1 as follows:

```
    $ mvn tomcat7:run -Dmongo.config.host=localhost
         …
    INFO: Starting ProtocolHandler ["http-bio-9082"]
```

To run the Driver server from maven we again use the tomcat7-maven-plugin. For more information about the Tomcat plugin configuration see the project file. It kicks off an embedded Apache Tomcat instance with the Sign Up test suite web application deployed. To kick off a second Driver just use a different console window and supply a different port number:
```
    $ alfresco-benchmark-signup$ mvn tomcat7:run -Dmongo.config.host=localhost -Dbm.tomcat.port=9083
        ...
    INFO: Starting ProtocolHandler ["http-bio-9083"]
```

3. Access benchmark server UI
<pre><code>
    Browse to http://localhost:9080/alfresco-benchmark-server
</code></pre>
4. Create a Test

<pre><code>
    Click [+] if not presented with "Create Test" options.  
    Fill in test details:   
        - Test Name: MyFirstTest01  
        - Test Description: Getting started 
        - Test Definition: alfresco-benchmark-tests-ent-signup-xxx
    Click "Ok".
</code></pre>
5. Edit test properties
<pre><code>
    It is a requirement that all test runs get told where to store the generated results.   
    Change property "mongo.test.host" to your mongo-host (e.g 127.0.0.1:27017)
    Click: "MyFirstTest01" on top left
</code></pre>
6. Create a Test Run
<pre><code>
    Click [+] if not presented with "Create Test Run" options.  
    Fill in test run details:   
        - Test run name: 01     
    Click "Ok".
</code></pre>
7. Start the Test Run
<pre><code>
    Click "Play" button next to Test Run "01".  
    The progress bar will auto-refresh as the test run completion estimate changes.
</code></pre>
8. Download results
<pre><code>
    At any time - usually when the test run completes - click through on the test run.  
    Click the download button and open the CSV file in a spreadsheet.
</code></pre>
