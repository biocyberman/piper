<p align="center">
  <a href="https://github.com/NationalGenomicsInfrastructure/piper">
    <img width="512" height="206" src="artwork/logo.png"/>
  </a>
</p>
-----------------------------

[![Build Status](https://travis-ci.org/johandahlberg/piper.png?branch=master)](https://travis-ci.org/johandahlberg/piper)

A pipeline project started at the [SNP&SEQ Technology platform](http://www.molmed.medsci.uu.se/SNP+SEQ+Technology+Platform/) built on top of [GATK Queue](http://www.broadinstitute.org/gatk/guide/topic?name=intro#intro1306). Since then Piper has been adopted by the Swedish [National Genomics Infrastructure (NGI)](http://www.scilifelab.se/platforms/ngi/) for use in the the Swedish Genomes Program as well as for samples submitted through the Illumina Genome Network to the NGI platform.

Piper builds on the concept of standardized workflows for different next-generation sequencing applications. At the moment Piper supports the following workflows:

* WholeGenome: For human whole genome sequencing data. This goes through alignment, alignment quality control, data processing, variant calling, and variant filtration according to the [best practice recommended by the Broad Institute](http://www.broadinstitute.org/gatk/guide/topic?name=best-practices), using primarily the GATK.
* Exome: TruSeq and SureSelect human exome sequencing: These use basically the same pipeline as the whole genome pipeline, but with the modifications suggested in the [best practice document](http://www.broadinstitute.org/gatk/guide/topic?name=best-practices) for exome studies.
* Haloplex: Haloplex targeted sequencing analysis. Including alignment, data processing, and variant calling.
* RNACounts: Produces [FPKMs](http://cufflinks.cbcb.umd.edu/faq.html#fpkm) for transcripts of an existing reference annotation using Tophat for mapping and Cufflinks to produce the FPKMs.

All supported workflows are available in the `workflows` directory in the project root.

Prerequisites and installation
==============================

Piper has been tested on the Java(TM) SE Runtime Environment (build 1.7.0_25) on the [UPPMAX](http://www.uppmax.uu.se) cluster Milou. It might run in other environments, but this is untested. Besides the JVM, Piper depends on [Maven (version 3+)](http://maven.apache.org/) for building (the GATK), [Make](http://www.gnu.org/software/make/) to install, and [git](http://git-scm.com/) to checkout the source. To install piper, make sure that these programs are in you path and then clone this repository and run the setup script:

    git clone https://github.com/Molmed/piper.git
    cd piper
    ./setup.sh <path to install Piper to. Default is: $HOME/Bin/Piper>
    # Follow the instructions printed by the setup script.
    
As Piper acts as a wrapper for several standard bioinformatics programs, it requires that these are installed. At this point it requires that the following programs are installed (depending somewhat on the application):

* [bwa](http://bio-bwa.sourceforge.net/) 0.7.5a
* [samtools](http://samtools.sourceforge.net/) 0.1.19
* [tophat](http://tophat.cbcb.umd.edu/) 2.0.10
* [cutadapt](https://code.google.com/p/cutadapt/) 1.2.1
* [cufflinks](http://cufflinks.cbcb.umd.edu/) 2.1.1
* [qualimap](http://qualimap.bioinfo.cipf.es/) v1.0 

The paths for these programs are setup in the `globalConfig.sh` file. If you are running on UPPMAX these should already be pointing to the correct locations. If not, you need to change them there.

Resource files
==============

For the standard application of alignment, data processing, and variant calling in human data, Piper relies on data available in the GATK bundle from the Broad Institute. This is available for download from their [website](http://gatkforums.broadinstitute.org/discussion/1213/what-s-in-the-resource-bundle-and-how-can-i-get-it). If you are working on UPPMAX these resources are available at `/pica/data/uppnex/reference/biodata/GATK/ftp.broadinstitute.org/bundle/2.8/`, however you might want to create your own directory for these in which you soft link the files, as you will be required to create, for example, bwa indexes.

The path to the GATK bundle needs to be setup in the `globalConfig.sh`.

Running the pipeline
====================

There are a number of workflows currently supported by Piper (See below). For most users these should just be called accoring to the run examples below. If you want to make customizations, open the appropriate workflow bash script and make the changes there. If you need to know what parameters are available, run the script with `--help`, e.g:

    piper -S <path to Script> --help

Setup for run
-------------

All workflows start with an xml file, for example: `pipelineSetup.xml`. This contains information about the raw data (run folders) that you want to run in the project. This is created using the `setupFileCreator` command.

** Directory structures and report files**
Piper depends on one of two specifications for folder structure to be parse metadata about the samples. The first structure looks like this (and is the one used by projects at the SNP&SEQ technology platform in Uppsala):

    Top level
    |---Runfolder1
        |---report.xml/report.tsv
        |---Sample_1
            |--- 1_<index>_<lane>_<read1>_xxx.fastq.gz
            |--- 1_<index>_<lane>_<read2>_xxx.fastq.gz (optional)
        |---Sample_2
            |--- 2_<index>_<lane>_<read1>_xxx.fastq.gz
            |--- 2_<index>_<lane>_<read2>_xxx.fastq.gz (optional)
    |---Runfolder1
        |---report.xml/report.tsv
        |---Sample_1
            |--- 1_<index>_<lane>_<read1>_xxx.fastq.gz
            |--- 1_<index>_<lane>_<read2>_xxx.fastq.gz (optional)
        |---Sample_2
            |--- 2_<index>_<lane>_<read1>_xxx.fastq.gz
            |--- 2_<index>_<lane>_<read2>_xxx.fastq.gz (optional)

As evident from this, the structure of each runfolder needs to have a file named either `report.xml` or `report.tsv`. In the `report.xml` file case, this is a file that is generated by the software [Sisyphus](https://github.com/Molmed/sisyphus), which is used by the SNP&SEQ Technology Platform to process data. If your data is not delivered from the SNP&SEQ Technology Platform you are probably better off using the simple `report.tsv` format. This is a tab-separated file with the following format:

        #SampleName     Lane    ReadLibrary     FlowcellId
        MyFirstSample   1       FirstLib        9767892AVF
        MyFirstSample   2       SecondLib       9767892AVF
        MySecondSample  1       SomeOtherLib    9767892AVF


The other allowed format is the Illumina Genome Network (IGN) file structure as it has been defined by the NGI.

    Project top level
    |---Sample
        |---Library
            |---Runfolder
               |--- <project>_<sample_name>_<index>_<lane>_xxx.fastq.gz

**Running non-interactively**
Run `setupFileCreator` without any arguments. This will show you the list of parameters that you need to set. This will produce a setup file on the following format:

```
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<project xmlns="setup.xml.molmed">
    <metadata>
        <name>NA-001</name>
        <sequenceingcenter>NGI</sequenceingcenter>
        <platform>Illumina</platform>
        <uppmaxprojectid>a2009002</uppmaxprojectid>
        <uppmaxqos></uppmaxqos>
        <reference>/home/MOLMED/johda411/workspace/piper/src/test/resources/testdata/exampleFASTA.fasta</reference>
    </metadata>
    <inputs>
        <sample>
            <samplename>F15</samplename>
            <library>
                <libraryname>SX396_MA140710.1</libraryname>
                <platformunit>
                    <unitinfo>000000000-AA3LB.1</unitinfo>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/140812_M00485_0148_000000000-AA3LB/Projects/MD-0274/140812_M00485_0148_000000000-AA3LB/Sample_F15/F15_CCGAAGTA_L001_R1_001.fastq.gz</path>
                    </fastqfile>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/140812_M00485_0148_000000000-AA3LB/Projects/MD-0274/140812_M00485_0148_000000000-AA3LB/Sample_F15/F15_CCGAAGTA_L001_R2_001.fastq.gz</path>
                    </fastqfile>
                </platformunit>
            </library>
        </sample>
        <sample>
            <samplename>E14</samplename>
            <library>
                <libraryname>SX396_MA140710.1</libraryname>
                <platformunit>
                    <unitinfo>000000000-AA3LB.1</unitinfo>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/140812_M00485_0148_000000000-AA3LB/Projects/MD-0274/140812_M00485_0148_000000000-AA3LB/Sample_E14/E14_AGTCACTA_L001_R2_001.fastq.gz</path>
                    </fastqfile>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/140812_M00485_0148_000000000-AA3LB/Projects/MD-0274/140812_M00485_0148_000000000-AA3LB/Sample_E14/E14_AGTCACTA_L001_R1_001.fastq.gz</path>
                    </fastqfile>
                </platformunit>
            </library>
        </sample>
        <sample>
            <samplename>P1171_104</samplename>
            <library>
                <libraryname>A</libraryname>
                <platformunit>
                    <unitinfo>AC41A2ANXX.2</unitinfo>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L002_R1_001.fastq.gz</path>
                    </fastqfile>
                </platformunit>
                <platformunit>
                    <unitinfo>AC41A2ANXX.1</unitinfo>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L001_R1_001.fastq.gz</path>
                    </fastqfile>
                    <fastqfile>
                        <path>/home/MOLMED/johda411/workspace/piper/src/test/resources/testdata/Sthlm2UUTests/sthlm_runfolder_root/P1171_104/A/140702_AC41A2ANXX/P1171_104_ATTCAGAA-GGCTCTGA_L001_R2_001.fastq.gz</path>
                    </fastqfile>
                </platformunit>
            </library>
        </sample>
    </inputs>
</project>
```

Running
-------

Pick the workflow (they are located in the Piper directory under `workflows`) that you want to run, e.g. haloplex. Then initiate it (by simply running it, or if you do not have access to a node where you can continually run a JVM by using `sbatch` to send it to a node) accoding to the examples below.

Note that all workflows are by default setup to be run with the human_g1k_v37.fasta reference and associated annotations. This means that if you need some other reference, you will have to set it up manually by configuring the workflow script (and depending somewhat on the use case, make changes to the qscripts themself). 

It's also worth mentioning that all the scripts have a optional `alignments_only` flag which can be set if you are only interested in running the aligments. This is useful what you are working on a project where data is added over time, but you want to create the aligments as data comes in, and then join across the sequencing runs and continue with the downstream analysis.

Unless the `run` flag is added to the workflow commandline the pipeline will only dry run (i.e. it will not actually run any jobs), this is useful to make sure that all commandline have been setup up properly. Once you are happy with the setup add `run` to the commandline to run the pipeline.


**Haloplex**

    Haloplex.sh --xml_input <setup.xml> --intervals <regions file> --amplicons <amplicon file> [--alignments_only] [--run]

The files associated with the Haloplex design can be downloaded from Agilent's homepage. Please note that the design files will be converted to interval files to work with Picard. In this process the names in the files are converted to work with the "b37" genome reference, rather than "hg19", which is the reference used by agilent. This means that if you want to use "hg19" then you must specify the `--do_not_convert` flag in the qscript.

**RNACounts**

    RNACounts.sh --xml_input <setup.xml> --library_type <fr-secondstrand/fr-firststrand/fr-unstranded> [--alignments_only] [--run]

Library types depends on the protcol used. For example, for ScriptSeq libraries (EpiCentre) the library type should be set to `fr-secondstrand`.

**Exome**
    
    Exome.sh --xml_input <setup.xml> <--sureselect> || <--truseq> [--alignments_only] [--run]

Pick one of either `--sureselect` or `--truseq` to set which exome intervals should be used. If you wish to use another interval file - open up the workflow file and set the `INTERVALS` to the path of your interval file.

**WholeGenome**

    WholeGenome.sh --xml_input <setup.xml> [--alignments_only] [--run]


Monitoring progress
-------------------

To follow the progress of the run look in the `pipeline_output/logs` folder. There you will find the logs for the different scripts. By searching the log file for "Run", you can see how many jobs are currently running, how many have finished, and how many have failed. A recommendation is to use e.g. `less -S` to view the file with unwrapped lines, as it is quite difficult to read otherwise.


Development
===========

The heavy lifting in Piper is primarily done in Scala, with Bash glueing together the different scripts to into workflows. Some additional Java and the occasional Perl component is used, but the main body of the code is written in Scala.

Coding
------

For an introduction to Queue, on which Piper is built, see: http://gatkforums.broadinstitute.org/discussion/1306/overview-of-queue

To work on the Piper project I recommend using the [Scala IDE](http://scala-ide.org/). To start developing follow the installation procedure outlined above. When you have finised the installation you can set the project up for your IDE by running:

    sbt eclipse

This will create the necessary project file for you to be able to import the project into the Scala IDE and start developing.

Although the Scala IDE will compile the code as you type, you will probably also want to get the hang of a few basic SBT commands (which you can either run from the interactive sbt console which you start by typing `sbt` in the project root folder, or by typing `sbt <command>` to run it straight from the CLI):

    compile

Will compile your project.

    pack

Will produce the jars, start up scripts and a Make file (look under the `target/pack` to see the output)

    clean

If something looks strange it is probably a good idea to run this. It deletes all of your class files so that you can be sure you have a totally clean build.

    test

Run the tests (for more on testing, see the testing chapter) - note that by default this only dry runs the qscript integration tests, which basically makes sure that they compile, but giving you no guarantees for runtime functionality.

### Project organization

This is an (incomplete) overview of Pipers project organization, describing the most important parts of the setup.

<pre>
|-.travis.yml       # Travis setup file
|-.gitignore        # file which git should ignore
|-build.sbt         # The primary build definition file for sbt (there is additional build info under project)
|-globalConfig.sh   # Global setup with e.g. paths to programs etc.
|-README.md         # This readme
|----sbt            # The sbt compiler - included for the users convinience
|----lib            # Unmanaged dependecencies
|----project        # Build stuff for sbt
|----resources      # Unmanaged additional dependecencies which are manually downloaded by setup script, and a perl hack which is currently used to sync reads
|----src            # The source of piper
    |----main
        |----java
        |----resources
        |----scala
    |----test
        |----java
        |----resources
        |----scala
|----target         # Generated build files
|----workflows      # The workflow file which are used to actually run piper
</pre>


### Making Piper generate graph files
Queue includes functionallity to generate dot files to visualize the jobs graph. This is highly useful when debugging new qscripts as it lets you see how the jobs connect to one another. So, if you have made a mistake in the chaining of the dependencies it is easy to spot. ".dot" files can be opened with e.g. [xdot](https://github.com/jrfonseca/xdot.py).


### Using the XML binding compiler (xjc):
To generate the xml read classes I use xjc, which uses an xml schema in xsd format to generate a number of java classes, which can then be used to interact with the setup and report xml files. These classes are used by the SetupXMLReader and the SetupFileCreator. An example of how to generate the classes is seen below:

	 xjc -d src/main/java/ src/main/resources/PipelineSetupSchema.xsd

Testing
-------

### Running pipeline tests
Running the tests is done by `sbt test`. However, there are some things which need to be noted. As the pipeline tests take a long time and have dependencies on outside programs (such as bwa for alignment, etc.) these can only be run on machines that have all the required programs installed and that have all the correct resources. This means that by default the tests are setup to just compile the qscripts, but not run them. If you want to run the qscripts you need to go into `src/test/resources/testng.xml` and set the value of the runpipeline parameter to 'true'.

### Writing pipeline tests
Pipeline tests are setup to run a certain QScript and check the md5sums of the outputs. If md5sums do not match, it will show you what the differences between the files are so that you can decide if the changes to the output are reasonable concidering the changes to the code you have made. At the moment, pipeline tests are just setup to run (with all the necessary resources, etc) on my workstation. In furture versions I hope to be able to make this more portable.

### Continuous integration using Travis:
Piper uses [Travis](https://travis-ci.org/) for continious integration. For instruction on how to set this up with a github repository see: http://about.travis-ci.org/docs/user/getting-started/

Troubleshooting
===============

Old projects
------------
In projects where data was generated before the spring of 2013, the report.xml files do not fulfill the current specification. To fix this you need to find the following row in the `report.xml`:
    
    <SequencingReport>

and substitute it for:

    <SequencingReport  xmlns="illuminareport.xml.molmed">

Licence
=======

The MIT License (MIT)

Copyright (c) 2013  The SNP&SEQ Technology Platform, Uppsala

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
