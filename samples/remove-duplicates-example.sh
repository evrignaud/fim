#!/bin/bash
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
# along with Fim.  If not, see <http://www.gnu.org/licenses/>.
#-----------------------------------------------------------------------------------------------------------------------

# You can see this script in action here:
#	http://evrignaud.github.io/fim/#_simple_duplicates_removing

set -x

ROOT_DIR=rdup-example
rm -rf ${ROOT_DIR} || exit $?

echo \# Create a source directory with some files in it
mkdir ${ROOT_DIR} || exit $?
cd ${ROOT_DIR}
mkdir source
cd source/
for i in 01 02 03 04 05 06 07 08 09 10 ; do echo "New File $i" > file${i} ; done
ls -la

echo \# Initialize the Fim repository
fim init -y || exit $?

echo \# Create a backup of this directory
cd ..
cp -a source backup || exit $?

echo \# Modify two files into the source directory and move two others
cd source/
echo modif1 >> file02
echo modif2 >> file04

mkdir subdir
mv file01 subdir
mv file03 subdir

ls -la
ls -la subdir

echo \# Commit all the modifications
fim ci -s -m "Modifications"

echo \# Remove the duplicates
cd ../backup/
fim rdup -M ../source || exit $?

echo \# Only the two modified files remains
ls -la

#-----------------------------------------------------------------------------------------------------------------------
