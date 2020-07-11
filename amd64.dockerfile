ARG OPENJDK_VERSION=11.0.7
FROM openjdk:${OPENJDK_VERSION}-jre
EXPOSE 10203/tcp

# Copy the server's artifact
COPY barbarissa-backend.tgz /usr/share/barbarissa-backend.tgz
RUN tar -C /usr/share -xzf /usr/share/barbarissa-backend.tgz && rm /usr/share/barbarissa-backend.tgz && \
    mkdir -p /usr/share/barbarissa-backend/conf/barbarissa-backend && touch /usr/share/barbarissa-backend/conf/barbarissa-backend/main.conf

CMD /bin/bash /usr/share/barbarissa-backend/bin/barbarissa-main -Dconfig.file=/usr/share/barbarissa-backend/conf/barbarissa-backend/main.conf
