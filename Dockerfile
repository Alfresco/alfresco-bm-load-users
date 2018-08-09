# Image provides a container that runs alfresco-bm-load-users to create users on Alfresco Enterprise Content Services.

# Fetch image based on Java 8
FROM alfresco/alfresco-base-java:8

COPY target/alfresco-users-load-bmf-driver-${docker.project_version}.jar /usr/bin
RUN ln /usr/bin/alfresco-users-load-bmf-driver-${docker.project_version}.jar /usr/bin/alfresco-users-load-bmf-driver.jar

EXPOSE 9082

ENV JAVA_OPTS=""
ENTRYPOINT java $JAVA_OPTS -jar /usr/bin/alfresco-users-load-bmf-driver.jar