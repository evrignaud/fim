#
# Fim docker image
#
FROM java:8-jre-alpine

MAINTAINER Etienne Vrignaud "evrignaud@gmail.com"

ADD target/dist /usr/bin/fim

# install Fim
RUN cd /usr/bin/fim && \
    tar zxvf fim-*-distribution.tar.gz && \
    ls -la

ENV PATH $PATH:/usr/bin/fim

# mount this folder with the Fim repository root folder
VOLUME /fim_repository

WORKDIR /fim_repository

# Default command is help
CMD fim help
