#-----------------------------------------------------------------------------------------------------------------------
# This file is part of Fim - File Integrity Manager
#
# Copyright (C) 2025 Etienne Vrignaud
#
# Fim is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Fim is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Fim.  If not, see <https://www.gnu.org/licenses/>.
#-----------------------------------------------------------------------------------------------------------------------

FROM alpine/java:21-jdk

MAINTAINER Etienne Vrignaud "evrignaud@gmail.com"

ADD build/distributions /build/distributions

# install Fim
RUN \
    cd /build/distributions     && \
    tar xvf fim-shadow*.tar     && \
    mkdir /fim                  && \
    mv fim-shadow*/* /fim       && \
    ls -la /fim                 && \
    ls -la /fim/bin


ENV PATH $PATH:/fim/bin

# mount this folder with the Fim repository root folder
VOLUME /fim_repository

WORKDIR /fim_repository

# Default command is help
CMD fim help

#-----------------------------------------------------------------------------------------------------------------------
