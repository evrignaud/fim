<h1 align="center"><img src="https://evrignaud.github.io/fim/images/icons/fim-96.png" alt="Fim"/> &nbsp; File Integrity Manager</h1>

Fim manages the integrity of a complete file tree

# Docker image

#### [Docker Hub](https://hub.docker.com/r/evrignaud/fim/)

The image builds Fim itself, so it needs neither a JDK nor a prior build on the host:

    $ ./build-docker-image

# Build

Fim is built with Gradle, through the wrapper committed in the repository. You need a JDK 17 or later:

    $ ./gradlew build

[![Coverage Status](https://coveralls.io/repos/evrignaud/fim/badge.svg?branch=master&service=github)](https://coveralls.io/github/evrignaud/fim?branch=master) &nbsp;&nbsp; [![Coverity Scan Build Status](https://scan.coverity.com/projects/8749/badge.svg)](https://scan.coverity.com/projects/evrignaud-fim)
[![Latest tag](https://img.shields.io/github/tag/evrignaud/fim.svg)](https://github.com/evrignaud/fim/releases/latest) [![License](https://img.shields.io/github/license/evrignaud/fim.svg)](https://evrignaud.github.io/fim/LICENSE.html)

## Slides

  * [English slides](https://evrignaud.github.io/fim/slides/en.html)<br/>
  [![English slides](https://evrignaud.github.io/fim/slides/images/slides-preview-en.jpg)](https://evrignaud.github.io/fim/slides/en.html)

  * [French slides](https://evrignaud.github.io/fim/slides/fr.html)<br/>
  [![French slides](https://evrignaud.github.io/fim/slides/images/slides-preview-fr.jpg)](https://evrignaud.github.io/fim/slides/fr.html)

## Documentation

  * [Presentation](https://evrignaud.github.io/fim/#_fim_file_integrity_manager)
  * [Why do you need Fim](https://evrignaud.github.io/fim/#_why_do_you_need_fim)
  * [How does it work](https://evrignaud.github.io/fim/#_how_does_it_work)
  * [Fim workflow](https://evrignaud.github.io/fim/#_fim_workflow)
  * [Most Common use cases](https://evrignaud.github.io/fim/#_most_common_use_cases)
  * [Fim usage](https://evrignaud.github.io/fim/#_fim_usage)
  * [How can you use Fim](https://evrignaud.github.io/fim/#_how_can_you_use_fim)
  * [Fim changelog](https://evrignaud.github.io/fim/#_fim_changelog)
  * [Run Fim using Docker](https://evrignaud.github.io/fim/#_run_fim_using_docker)
  * [Simple example](https://evrignaud.github.io/fim/#_simple_example)
  * [Real life example](https://evrignaud.github.io/fim/#_real_life_example)
  * [Super-fast commit](https://evrignaud.github.io/fim/#_super_fast_commit)
  * [Dealing with duplicates](https://evrignaud.github.io/fim/#_dealing_with_duplicates)
  * [File permissions management](https://evrignaud.github.io/fim/#_file_permissions_management)
  * [Hardware corruption detection](https://evrignaud.github.io/fim/#_hardware_corruption_detection)
  * [FAQ](https://evrignaud.github.io/fim/#_faq)
  * [License](https://evrignaud.github.io/fim/LICENSE.html)

## Releases

You can download Fim binary distributions from [here](https://github.com/evrignaud/fim/releases/latest)

## Requirements

This tool is written in Java.

Fim is compiled using Java 17 and requires Java 17 or later to run. You can download it from [Adoptium](https://adoptium.net), or use the JDK your package manager provides.<br/>
If you would rather not install Java, the [Docker image](https://evrignaud.github.io/fim/#_run_fim_using_docker) carries everything Fim needs.

Run Fim under a UTF-8 locale, for example `LANG=C.UTF-8`. Fim holds file names as text, so a name carrying characters the JVM's encoding cannot represent comes back altered and no longer refers to the file it came from.

## Supported OS

Fim can be used on Linux, Mac OS X and Windows

## They talked about it

### &bull; ![Korben.info](https://evrignaud.github.io/fim/images/icons/korben.info.png) &nbsp;&nbsp; [Vérifier l’intégrité de très nombreux fichiers](https://korben.info/verifier-lintegrite-de-tres-nombreux-fichiers.html)

> **Fim (File Integrity Manager)** est un outil vraiment excellent qui permet de gérer l'intégrité de nombreux fichiers.<br/>
> [+ Lire la suite](https://korben.info/verifier-lintegrite-de-tres-nombreux-fichiers.html)
>
>> ![English version](https://evrignaud.github.io/fim/images/icons/english.png) &nbsp; [Verify the integrity of many files](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fkorben.info%2Fverifier-lintegrite-de-tres-nombreux-fichiers.html)<br/>
>> **Fim (File Integrity Manager)** is a really great tool for managing the integrity of many files.<br/>
>> [+ Read more](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fkorben.info%2Fverifier-lintegrite-de-tres-nombreux-fichiers.html)

### &bull; ![01net.com](https://evrignaud.github.io/fim/images/icons/01net.com.png) &nbsp;&nbsp; [Fim (File Integrity Manager)](http://www.01net.com/telecharger/linux/Utilitaires/fiches/132315.html)

> **Fim (File Integrity Manager)** est un outil open source qui vous permet de vérifier l'intégrité de tous vos fichiers après les avoir manipulés en lots.<br/>
> [+ Lire la suite pour ![Linux](https://evrignaud.github.io/fim/images/icons/linux.png)](http://www.01net.com/telecharger/linux/Utilitaires/fiches/132315.html) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [+ Lire la suite pour ![Windows](https://evrignaud.github.io/fim/images/icons/windows.png)](http://www.01net.com/telecharger/windows/Utilitaire/manipulation_de_fichier/fiches/132314.html)<br/>
>
>> ![English version](https://evrignaud.github.io/fim/images/icons/english.png) &nbsp; **Fim (File Integrity Manager)** is an open source tool which allows you to check the integrity of all your files after have handled them bulk.<br/>
>> [+ Read more for ![Linux](https://evrignaud.github.io/fim/images/icons/linux.png)](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fwww.01net.com%2Ftelecharger%2Flinux%2FUtilitaires%2Ffiches%2F132315.html) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [+ Read more for ![Windows](https://evrignaud.github.io/fim/images/icons/windows.png)](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Fwww.01net.com%2Ftelecharger%2Fwindows%2FUtilitaire%2Fmanipulation_de_fichier%2Ffiches%2F132314.html)

### &bull; ![linuxfr.org](https://evrignaud.github.io/fim/images/icons/linuxfr.org.png) &nbsp;&nbsp; LinuxFr.org

> &mdash; [Effacement des doublons et historique complet pour Fim 1.2.2](https://linuxfr.org/news/effacement-des-doublons-et-historique-complet-pour-fim-1-2-2)
>> [![English version](https://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Removing duplicates and full history for Fim 1.2.2](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=https%3A%2F%2Flinuxfr.org%2Fnews%2Feffacement-des-doublons-et-historique-complet-pour-fim-1-2-2&sandbox=1)

> &mdash; [Focus sur les performances avec Fim 1.2.0](https://linuxfr.org/news/focus-sur-les-performances-avec-fim-1-2-0)
>> [![English version](https://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Focus on performance with Fim 1.2.0](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=https%3A%2F%2Flinuxfr.org%2Fnews%2Ffocus-sur-les-performances-avec-fim-1-2-0&sandbox=1)

> &mdash; [Fim 1.1.0](https://linuxfr.org/news/fim-1-1-0)
>> [![English version](https://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Fim 1.1.0](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Flinuxfr.org%2Fnews%2Ffim-1-1-0)

> &mdash; [Sortie de Fim 1.0.2, qui vérifie l'intégrité de vos fichiers](https://linuxfr.org/news/sortie-de-fim-1-0-2-qui-verifie-l-integrite-de-vos-fichiers)
>> [![English version](https://evrignaud.github.io/fim/images/icons/english.png) &nbsp; Fim 1.0.2, which checks the integrity of your files](https://translate.google.com/translate?hl=en&sl=fr&tl=en&u=http%3A%2F%2Flinuxfr.org%2Fnews%2Fsortie-de-fim-1-0-2-qui-verifie-l-integrite-de-vos-fichiers)


## About

Created by Etienne Vrignaud &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [My blog](https://evrignaud.github.io/blog/) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Twitter icon](https://evrignaud.github.io/fim/images/icons/twitter.png) Follow @evrignaud](https://twitter.com/evrignaud)
