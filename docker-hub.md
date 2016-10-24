[![Fim](http://evrignaud.github.io/fim/images/icons/fim-96.png)](https://github.com/evrignaud/fim)

# Supported tags and respective Dockerfile links

* `latest`, `1.2.2` ([Dockerfile](https://github.com/evrignaud/fim/blob/1.2.2/Dockerfile))
* `1.2.1` ([Dockerfile](https://github.com/evrignaud/fim/blob/1.2.1/Dockerfile))
* `1.2.0` ([Dockerfile](https://github.com/evrignaud/fim/blob/1.2.0/Dockerfile))

[![Latest version](https://images.microbadger.com/badges/version/evrignaud/fim.svg)](https://microbadger.com/images/evrignaud/fim) &nbsp; [![Download size](https://images.microbadger.com/badges/image/evrignaud/fim.svg)](https://microbadger.com/images/evrignaud/fim)

# What is Fim?

The purpose of [Fim](https://github.com/evrignaud/fim) is to manage a set of files and be able to see quickly the files you are working on and be sure that files are not tampered.
When you do a lot of stuff at the same time, things take time and Fim is here to help you to figure out what you were doing before.

Fim allow you to quickly get the status of all kind of files. It can be photos, videos or a complete disk tree. You can manage them with a spirit that looks like Git.

On a daily workflow, you can know the files that have been added, modified, removed, moved, renamed and duplicated. When you are working with files and you are also very busy, it’s difficult to remind what are the files you were working on previously. A VCS can help you for this. Unfortunately when you are working with photos or videos, or other kind of big binary files you cannot manage them with a tool like Git.
Fim is meant to solve that.

Each time you have a stable set of files you can commit them to store their properties into a State and be able to compare it with your set of files later.

The big difference with Git is that Fim does not keep track of the different contents of the files that are managed.
This is why we can call it an UnVersioned Control System (UVCS).

Fim is able to manage hundreds of thousands of files with a total size of several terabytes. This kind of file tree could not be managed by Git.

Using Fim you can also easily detect duplicates and remove them.

# Documentation

A complete documentation of Fim is available [here](http://evrignaud.github.io/fim)

# How to use this image

### Retrieve the `fim-docker` script:

    $ curl goo.gl/XwERDY -L -o fim-docker && chmod a+rx fim-docker

### Run it:
If you don't have the Fim docker image locally it will pull the image first.
This script takes the same arguments as the `fim` one.

    $ ./fim-docker -h


![Analytics](https://ga-beacon.appspot.com/UA-78135040-1/evrignaud/fim?pixel)
