#-----------------------------------------------------------------------------------------------------------------------
# This file is part of Fim - File Integrity Manager
#
# Copyright (C) 2026 Etienne Vrignaud
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

#-----------------------------------------------------------------------------------------------------------------------
# Build stage: produce the Fim distribution inside the image.
#
# This used to expect build/distributions to already be present in the build context, so building from a
# fresh clone stopped at:
#   ADD failed: file not found in build context or excluded by .dockerignore: stat build/distributions
# because the archive only exists after a Gradle build has been run on the host. Building it here makes
# `docker build -t evrignaud/fim .` work on its own.
#
# The Java version is the one build.gradle.kts pins the toolchain to, so Gradle uses this JDK directly
# instead of trying to provision another one.
#-----------------------------------------------------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# The wrapper and the build definition change less often than the sources, so copying them on their own
# first lets Docker reuse the cached layer holding the downloaded Gradle distribution.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts checkstyle.xml ./

# Only what the distribution is built from. The tests and the asciidoc sources are not needed to package.
COPY src/main/java ./src/main/java
COPY src/main/resources ./src/main/resources

RUN chmod +x gradlew && \
    ./gradlew --no-daemon --console=plain shadowDistTar

#-----------------------------------------------------------------------------------------------------------------------
# Runtime stage
#-----------------------------------------------------------------------------------------------------------------------
FROM alpine/java:21-jdk

LABEL maintainer="Etienne Vrignaud <evrignaud@gmail.com>"
LABEL description="Fim - File Integrity Manager"

# Fim holds file names as text, so the JVM has to be able to represent the names it reads from disk.
# Without a UTF-8 locale the default is ASCII, and names carrying accents come back mangled and no
# longer resolve to the file they came from.
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# shadowDistTar writes an uncompressed tar, so it is extracted without -z: the previous `tar zxvf` could
# only have failed on it. Its single top-level directory carries the version, which is stripped so that
# the launcher always ends up at the same /fim/bin/fim regardless of the version being built.
COPY --from=builder /build/build/distributions/fim-shadow-*.tar /tmp/dist/

RUN mkdir -p /fim && \
    tar -xf /tmp/dist/fim-shadow-*.tar -C /fim --strip-components=1 && \
    rm -rf /tmp/dist && \
    chmod +x /fim/bin/fim

# /fim/bin, not /fim: the launcher lives in the bin directory of the distribution.
ENV PATH="$PATH:/fim/bin"

# Mount this folder with the Fim repository root folder
VOLUME /fim_repository

WORKDIR /fim_repository

# Default command is help
CMD ["fim", "help"]

#-----------------------------------------------------------------------------------------------------------------------
