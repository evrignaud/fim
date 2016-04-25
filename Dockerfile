#
# Fim docker image
#
FROM java:8-jdk

MAINTAINER Etienne Vrignaud "evrignaud@gmail.com"

ADD target/dist /usr/bin/fim

# install Fim
RUN cd /usr/bin/fim && \
    tar zxvf fim-*-distribution.tar.gz && \
    ls -la

ENV PATH $PATH:/usr/bin/fim

# mount this folder with the project root folder
VOLUME /working_directory

WORKDIR /working_directory

CMD fim help
