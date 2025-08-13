################################################################################
#                                     BUILD                                    #
################################################################################

FROM us-docker.pkg.dev/artifact-foundry-prod/docker-3p-trusted/maven@sha256:8472bdbac39626b70d2efcf87e23468ff0bea9ba48c35181b07e6932b5fda001 AS build

# Copy over build files to docker image.
COPY LICENSE ./
COPY CONTRIBUTING.md ./
COPY README.md ./
COPY NOTIFICATIONS.md ./
COPY logging.properties ./
COPY src src/
COPY pom.xml ./
COPY license-checks.xml ./
COPY java.header ./

COPY settings_moss.xml /root/.m2/settings.xml
# Download dependencies
RUN mvn -s /root/.m2/settings.xml dependency:go-offline

# Build from source.
RUN mvn -s /root/.m2/settings.xml package -Passembly -DskipTests

################################################################################
#                                   RELEASE                                    #
################################################################################

FROM us-docker.pkg.dev/artifact-foundry-prod/docker-3p-trusted/eclipse-temurin@sha256:097cb49ae7581f535416849edea25f39dec8ca28b36b9dd10a5a781290769bf3

COPY --from=build target/pgadapter /home/pgadapter
COPY --from=build LICENSE /home/pgadapter/
COPY --from=build CONTRIBUTING.md /home/pgadapter/
COPY --from=build README.md /home/pgadapter/
COPY --from=build NOTIFICATIONS.md /home/pgadapter/
COPY --from=build logging.properties /home/pgadapter/

# Add startup script.
ADD build/startup.sh /home/pgadapter/startup.sh
RUN chmod +x /home/pgadapter/startup.sh

ENTRYPOINT ["/bin/bash", "/home/pgadapter/startup.sh"]
