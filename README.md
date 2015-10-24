# ![Logo](http://evrignaud.github.io/fim/images/icons/fim-96.png) &nbsp; File Integrity Manager

[![Build Status](https://travis-ci.org/evrignaud/fim.svg)](https://travis-ci.org/evrignaud/fim) &nbsp; [![Coverage Status](https://coveralls.io/repos/evrignaud/fim/badge.svg?branch=master&service=github)](https://coveralls.io/github/evrignaud/fim?branch=master)

Fim manage the integrity of a complete file tree

## Fim documentation

  * [Presentation](http://evrignaud.github.io/fim/#_fim_file_integrity_manager)
  * [Why do you need Fim](http://evrignaud.github.io/fim/#_why_do_you_need_fim)
  * [How does it work](http://evrignaud.github.io/fim/#_how_does_it_work)
  * [Fim workflow](http://evrignaud.github.io/fim/#_fim_workflow)
  * [Use case example](http://evrignaud.github.io/fim/#_use_case_example)
  * [Fim usage](http://evrignaud.github.io/fim/#_fim_usage)
  * [How can you use Fim](http://evrignaud.github.io/fim/#_how_can_you_use_fim)
  * [Fim changelog](http://evrignaud.github.io/fim/#_fim_changelog)
  * [Simple example](http://evrignaud.github.io/fim/#_simple_example)
  * [Real life example](http://evrignaud.github.io/fim/#_real_life_example)
  * [File permissions management](http://evrignaud.github.io/fim/#_file_permissions_management)
  * [Hardware corruption detection](http://evrignaud.github.io/fim/#_hardware_corruption_detection)
  * [FAQ](http://evrignaud.github.io/fim/#_faq)
  * [License](http://evrignaud.github.io/fim/LICENSE.html)

## Fim releases

You can download Fim binary distributions from [here](https://github.com/evrignaud/fim/releases/latest)

## Fim requirements

This tool is written in Java.

Fim is compiled using Java 8 and require Java 8 to run. You can download Java 8 from [here](http://www.oracle.com/technetwork/java/javase/downloads/index.html).<br/>
You need at least Java 8 Standard Edition JRE. You can also use the OpenJDK 8.

## Supported OS

Fim can be used on Linux, Mac OS X and Windows

## They talked about it

### &bull; ![Korben.info](http://evrignaud.github.io/fim/images/icons/korben.info.png) &nbsp;&nbsp; [Vérifier l’intégrité de très nombreux fichiers](http://korben.info/verifier-lintegrite-de-tres-nombreux-fichiers.html)

> **Fim (File Integrity Manager)** est un outil vraiment excellent qui permet de gérer l'intégrité de nombreux fichiers.<br/>
> [+ Lire la suite](http://korben.info/verifier-lintegrite-de-tres-nombreux-fichiers.html)
>
>> ![English version](http://evrignaud.github.io/fim/images/english.png) &nbsp; [Check integrity of many files](http://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fkorben.info%2Fverifier-lintegrite-de-tres-nombreux-fichiers.html)<br/>
>> **Fim (File Integrity Manager)** is a really great tool for managing the integrity of many files.<br/>
>> [+ Read more](http://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fkorben.info%2Fverifier-lintegrite-de-tres-nombreux-fichiers.html)

### &bull; ![01net.com](http://evrignaud.github.io/fim/images/icons/01net.com.png) &nbsp;&nbsp; [Fim (File Integrity Manager)](http://www.01net.com/telecharger/linux/Utilitaires/fiches/132315.html)

> **Fim (File Integrity Manager)** est un outil open source qui vous permet de vérifier l'intégrité de tous vos fichiers après les avoir manipulés en lots.<br/>
> [+ Lire la suite pour ![Linux](http://evrignaud.github.io/fim/images/icons/linux.png)](http://www.01net.com/telecharger/linux/Utilitaires/fiches/132315.html) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [+ Lire la suite pour ![Windows](http://evrignaud.github.io/fim/images/icons/windows.png)](http://www.01net.com/telecharger/windows/Utilitaire/manipulation_de_fichier/fiches/132314.html)<br/>
>
>> ![English version](http://evrignaud.github.io/fim/images/english.png) &nbsp; **Fim (File Integrity Manager)** is an open source tool which allows you to check the integrity of all your files after have handled them bulk.<br/>
>> [+ Read more for ![Linux](http://evrignaud.github.io/fim/images/icons/linux.png)](http://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fwww.01net.com%2Ftelecharger%2Flinux%2FUtilitaires%2Ffiches%2F132315.html) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [+ Read more for ![Windows](http://evrignaud.github.io/fim/images/icons/windows.png)](http://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fwww.01net.com%2Ftelecharger%2Fwindows%2FUtilitaire%2Fmanipulation_de_fichier%2Ffiches%2F132314.html)

### &bull; ![linuxfr.org](http://evrignaud.github.io/fim/images/icons/linuxfr.org.png) &nbsp;&nbsp; LinuxFr.org

> &mdash; [Sortie de Fim 1.0.2, qui vérifie l'intégrité de vos fichiers](https://linuxfr.org/news/sortie-de-fim-1-0-2-qui-verifie-l-integrite-de-vos-fichiers)
>> [![English version](http://evrignaud.github.io/fim/images/english.png) &nbsp; Fim release 1.0.2, that verifies the integrity of your files](http://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Flinuxfr.org%2Fnews%2Fsortie-de-fim-1-0-2-qui-verifie-l-integrite-de-vos-fichiers)


## About

> Created by by Etienne Vrignaud &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Twitter icon](http://evrignaud.github.io/fim/images/icons/twitter.png) Follow @evrignaud](https://twitter.com/evrignaud)


![Analytics](https://ga-beacon.appspot.com/UA-65759837-1/fim/README.md?pixel)
