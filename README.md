<h1 align="center"><img src="http://evrignaud.github.io/fim/images/icons/fim-96.png" alt="Fim"/> &nbsp; File Integrity Manager</h1>

# Docker image

[![Latest version](https://images.microbadger.com/badges/version/evrignaud/fim.svg)](https://microbadger.com/images/evrignaud/fim) &nbsp; [![Download size](https://images.microbadger.com/badges/image/evrignaud/fim.svg)](https://microbadger.com/images/evrignaud/fim)

# Build

#### Linux & Mac OS X: [![Build Status](https://travis-ci.org/evrignaud/fim.svg)](https://goo.gl/QfQTE8) &nbsp; Windows: [![Build Status](https://ci.appveyor.com/api/projects/status/txadqci1hrr3lkko?svg=true)](https://goo.gl/foWAWQ) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Coverage Status](https://coveralls.io/repos/evrignaud/fim/badge.svg?branch=master&service=github)](https://goo.gl/hJGXqj) &nbsp;&nbsp; [![Coverity Scan Build Status](https://scan.coverity.com/projects/8749/badge.svg)](https://goo.gl/lbM77o)

Fim manages the integrity of a complete file tree

## Fim documentation

  * [Presentation](http://evrignaud.github.io/fim/#_fim_file_integrity_manager)
  * [Why do you need Fim](http://evrignaud.github.io/fim/#_why_do_you_need_fim)
  * [How does it work](http://evrignaud.github.io/fim/#_how_does_it_work)
  * [Fim workflow](http://evrignaud.github.io/fim/#_fim_workflow)
  * [Most Common use cases](http://evrignaud.github.io/fim/#_most_common_use_cases)
  * [Fim usage](http://evrignaud.github.io/fim/#_fim_usage)
  * [How can you use Fim](http://evrignaud.github.io/fim/#_how_can_you_use_fim)
  * [Fim changelog](http://evrignaud.github.io/fim/#_fim_changelog)
  * [Run Fim using Docker](http://evrignaud.github.io/fim/#_run_fim_using_docker)
  * [Simple example](http://evrignaud.github.io/fim/#_simple_example)
  * [Real life example](http://evrignaud.github.io/fim/#_real_life_example)
  * [Super-fast commit](http://evrignaud.github.io/fim/#_super_fast_commit)
  * [Dealing with duplicates](http://evrignaud.github.io/fim/#_dealing_with_duplicates)
  * [File permissions management](http://evrignaud.github.io/fim/#_file_permissions_management)
  * [Hardware corruption detection](http://evrignaud.github.io/fim/#_hardware_corruption_detection)
  * [FAQ](http://evrignaud.github.io/fim/#_faq)
  * [License](http://evrignaud.github.io/fim/LICENSE.html)

## Fim releases

You can download Fim binary distributions from [here](https://github.com/evrignaud/fim/releases/latest)

## Fim requirements

This tool is written in Java.

Fim is compiled using Java 8 and require Java 8 to run. You can download Java 8 from [here](http://goo.gl/p8iYjm).<br/>
You need at least Java 8 Standard Edition JRE. You can also use the OpenJDK 8.

## Supported OS

Fim can be used on Linux, Mac OS X and Windows

## They talked about it

### &bull; ![Korben.info](http://evrignaud.github.io/fim/images/icons/korben.info.png) &nbsp;&nbsp; [Vérifier l’intégrité de très nombreux fichiers](http://goo.gl/1gwX1g)

> **Fim (File Integrity Manager)** est un outil vraiment excellent qui permet de gérer l'intégrité de nombreux fichiers.<br/>
> [+ Lire la suite](http://goo.gl/1gwX1g)
>
>> ![English version](http://evrignaud.github.io/fim/images/icons/english.png) &nbsp; [Check integrity of many files](http://goo.gl/jBE2XY)<br/>
>> **Fim (File Integrity Manager)** is a really great tool for managing the integrity of many files.<br/>
>> [+ Read more](http://goo.gl/jBE2XY)

### &bull; ![01net.com](http://evrignaud.github.io/fim/images/icons/01net.com.png) &nbsp;&nbsp; [Fim (File Integrity Manager)](http://goo.gl/OYKGxe)

> **Fim (File Integrity Manager)** est un outil open source qui vous permet de vérifier l'intégrité de tous vos fichiers après les avoir manipulés en lots.<br/>
> [+ Lire la suite pour ![Linux](http://evrignaud.github.io/fim/images/icons/linux.png)](http://goo.gl/OYKGxe) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [+ Lire la suite pour ![Windows](http://evrignaud.github.io/fim/images/icons/windows.png)](http://goo.gl/Bn2CMH)<br/>
>
>> ![English version](http://evrignaud.github.io/fim/images/icons/english.png) &nbsp; **Fim (File Integrity Manager)** is an open source tool which allows you to check the integrity of all your files after have handled them bulk.<br/>
>> [+ Read more for ![Linux](http://evrignaud.github.io/fim/images/icons/linux.png)](http://goo.gl/nhzJxH) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [+ Read more for ![Windows](http://evrignaud.github.io/fim/images/icons/windows.png)](http://goo.gl/JwfoPA)

### &bull; ![linuxfr.org](http://evrignaud.github.io/fim/images/icons/linuxfr.org.png) &nbsp;&nbsp; LinuxFr.org

> &mdash; [Sortie de Fim 1.0.2, qui vérifie l'intégrité de vos fichiers](https://goo.gl/yjMH4U)
>> [![English version](http://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Fim release 1.0.2, that verifies the integrity of your files](http://goo.gl/HToiWd)

> &mdash; [Fim 1.1.0](https://goo.gl/LAuKqp)
>> [![English version](http://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Fim 1.1.0](http://goo.gl/KaO0Hm)

> &mdash; [Focus sur les performances avec Fim 1.2.0](https://goo.gl/UrZK7J)
>> [![English version](http://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Focus on performance with Fim 1.2.0](https://goo.gl/cZsQLN)


## About

Created by Etienne Vrignaud &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Twitter icon](http://evrignaud.github.io/fim/images/icons/twitter.png) Follow @evrignaud](https://goo.gl/5jFdRK)


![Analytics](https://ga-beacon.appspot.com/UA-65759837-1/fim/README.md?pixel)
