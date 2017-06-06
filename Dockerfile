#-----------------------------------------------------------------------------------------------------------------------
# This file is part of Fim - File Integrity Manager
#
# Copyright (C) 2017  Etienne Vrignaud
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

FROM java:8-jre-alpine

MAINTAINER Etienne Vrignaud "evrignaud@gmail.com"

ADD target/dist /fim

# install Fim
RUN cd /fim && \
    tar zxvf fim-*-distribution.tar.gz && \
    ls -la

ENV PATH $PATH:/fim

# mount this folder with the Fim repository root folder
VOLUME /fim_repository

WORKDIR /fim_repository

# Default command is help
CMD fim help

#-----------------------------------------------------------------------------------------------------------------------
