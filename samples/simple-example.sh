#!/bin/bash
#-----------------------------------------------------------------------------------------------------------------------
# This file is part of Fim - File Integrity Manager
#
# Copyright (C) 2015  Etienne Vrignaud
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

# You can see a run of this script here:
#	http://evrignaud.github.io/fim/#_simple_example

rm -rf simple-example
mkdir simple-example

set +x
(
	cd simple-example
	echo
	echo \# Create a set of files
	for i in 01 02 03 04 05 06 07 08 09 10 ; do echo "New File $i" > file$i ; done
	ls -la

	echo
	echo \# Initialize the Fim repository
	fim init -c "First State"

	echo
	echo \# A new .fim directory have been created
	ls -la

	echo
	echo \# Do some modifications
	mkdir dir01

	echo
	echo \# Move file01 to dir01
	mv file01 dir01

	echo
	echo \# Change the file02 modification date
	touch file02

	echo
	echo \# Duplicate twice file03
	cp file03 file03.dup1
	cp file03 file03.dup2

	echo
	echo \# Add content to file04
	echo foo >> file04

	echo
	echo \# Copy file05
	cp file05 file11

	echo
	echo \# And add content to it
	echo bar >> file05

	echo
	echo \# Remove file06
	rm file06

	echo
	echo \# Duplicate once file07
	cp file07 file07.dup1

	echo
	echo \# Create the new file12
	echo "New File 12" > file12

	ls -la
	ls -la dir01/

	echo
	echo \# Fim detects the modifications
	fim diff

	echo
	echo \# Search for duplicated files
	fim fdup

	echo
	echo \# From the dir01 sub-directory
	cd dir01

	echo
	echo \# Inside this directory only one file is added
	fim diff

	echo
	echo \# No duplicated files as we are looking only inside the dir01
	fim fdup

	echo
	echo \# Commit only the local modifications done inside this directory
	fim ci -c "Modifications from dir01" -y

	echo
	echo \# No more local modifications
	fim diff

	cd ..

	echo
	echo \# Commit the modifications
	fim ci -c "All modifications" -y

	echo
	echo \# Nothing is modified now
	fim diff

	echo
	echo \# Display the Fim log
	fim log
)
