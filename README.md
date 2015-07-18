# Fim
File Integrity Manager
============

## Introduction

Fim manage the integrity of a complete file tree.

Ensuring the integrity of a files can be done in several ways. The two classical ways are:

-  manage your files using a VCS or a DVCS.
-  put all your file into an archive that will ensure the integrity of all the files.

When you have gigabytes of videos or photos or other kind of binary files you cannot put all of them into a VCS or into an archive.

The solution provided by Fim is to manage an index of the files that contains for each file:

- filename
- file length
- modification date
- hash of the first four kilobytes of the file content
- hash of the first megabyte of the file content
- hash of the full file content

The hash algorithm that is used is SHA-512.

This index is called a `State` and acts like the Central Directory does for a Zip file.
All the data generated by Fim is stored into the `.fim` root directory.

You can use Fim to ensure the integrity of a big amount of data files.
This means having a clear status on files that have been removed, corrupted, modified, copied, renamed, duplicated.
You can also easily detect duplicates and remove them.

Fim does not keep track of the different contents of the files that are managed,
this is why we can call it an __Unversioned Control System (UVCS)__.
Fim keeps only each version of the different State that have been committed.
You cannot use them to retrieve the content of one file that you may have lost.

> __Using Fim does not prevent you to do a backup.__

## Fim Workflow

First you need to initialize the Fim repository using the init command.
This will record the first State of your file tree.

	$ fim init

Then you can compare the differences between the recorded State and the current file tree using the diff command.
You can do a full diff that will compare the hash of all the files. It can be very slow as all the files content will be read.

	$ fim diff

You can compare quickly asking to hash only the first megabyte of the files. **Using this option can produce a non accurate result.**

	$ fim diff -m

You can compare quicker asking to hash only the first four kilobytes of the files. **Using this option can produce a non accurate result.**

	$ fim diff -k

Otherwise you can request to not hash file content using the fast mode (-f option). It will compare only the filenames and modification dates.
You will not be able to detect files that have been renamed or duplicated.

	$ fim diff -f

Each time you want to record the State of the current file tree you can use the commit command.
It's a time consuming operation that will compute the files content hash.

	$ fim ci

## Usage

	$ fim help

	usage: fim <command> [-a <arg>] [-c <arg>] [-f] [-h] [-k] [-l] [-m] [-q] [-t <arg>] [-v] [-y]

	File Integrity Checker

	Available commands:
		 init                      Initialize a Fim repository
		 ci / commit               Commit the current directory State
		 diff                      Compare the current directory State with the previous one
		 rdates / reset-dates      Reset the file modification dates like it's stored in the last committed State
		 fdup / find-duplicates    Find duplicated files
		 rdup / remove-duplicates  Remove duplicated files from local directory based on a master Fim repository
		 log                       Display States log
		 help                      Prints the Fim help
		 version                   Prints the Fim version

	Available options:
		 -a,--master-fim-repository <arg>   Fim repository directory that you want to use as master. Only for the remove
											duplicates command
		 -c,--comment <arg>                 Sets that State comment during commit
		 -f,--fast-mode                     Do not hash file content. Use only filenames and modification dates
		 -h,--help                          Prints the Fim help
		 -k,--hash-only-first-four-kilo     Hash only the first four kilo of the files
		 -l,--use-last-state                Use the last committed State
		 -m,--hash-only-first-mega          Hash only the first mega of the files
		 -q,--quiet                         Do not display details
		 -t,--thread-count <arg>            Number of thread to use to hash files content in parallel
		 -v,--version                       Prints the Fim version
		 -y,--always-yes                    Always yes to every questions

