package molmed.utils

import java.io.File

import molmed.utils._
import org.broadinstitute.gatk.queue.QScript

import org.broadinstitute.gatk.queue.extensions.gatk._
import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.broadinstitute.gatk.queue.function.InProcessFunction
import org.broadinstitute.gatk.queue.function.ListWriterFunction
import molmed.queue.setup._
import molmed.queue.setup.InputSeqFileContainer
import molmed.queue.setup.SampleAPI
import molmed.utils.GeneralUtils.checkReferenceIsBwaIndexed
import scala.collection.JavaConversions._
import molmed.config.FileVersionUtilities._
import molmed.config.Constants

/**
 * Base class for alignment workflows.
 */
abstract class AligmentUtils(projectName: Option[String], uppmaxConfig: UppmaxConfig) extends UppmaxJob(uppmaxConfig)

/**
 * Holds classes and functions used for aligning with tophat
 */
class TophatAligmentUtils(tophatPath: String, tophatThreads: Int, projectName: Option[String],
                          uppmaxConfig: UppmaxConfig) extends AligmentUtils(projectName, uppmaxConfig) {

  /**
   * @param qscript						the qscript to in which the alignments should be used (usually "this")
   * @param libraryType				type of library that have been sequenced, fr-unstranded (default), fr-firststrand or fr-secondstrand
   * @param annotations				Annotations of known transcripts in GTF 2.2 or GFF 3 format.
   * @param outputDir					output it to this dir
   * @param sample						sample container that will be processed.
   * @param fusionSearch			perform fussion search using tophat
   * @return a bam file with aligned reads.
   */
  def align(qscript: QScript, libraryType: String, annotations: Option[File],
            outputDir: File, sampleName: String, sample: SampleAPI,
            fusionSearch: Boolean): File = {

    val fastqs: InputSeqFileContainer = sample.getInputSeqFiles()
    val sampleDir = new File(outputDir + "/" + sampleName)
    if(!sampleDir.exists()) {
      sampleDir.mkdirs()
    }
    @Output var alignedBamFile: File = new File(sampleDir + "/" + "accepted_hits.bam")
    val outputLog = new File(sampleDir + "/qscript_tophap.stdout.log")

    if(fastqs.hasPair)
    {
      qscript.add(this.tophat(fastqs.mate1, fastqs.mate2, alignedBamFile, sampleDir, sample.getReference, annotations, libraryType, outputLog, sample.getTophatStyleReadGroupInformationString(), fusionSearch))
    }
    else
    {
      qscript.add(this.singleReadTophat(fastqs.mate1, alignedBamFile, sampleDir, sample.getReference, annotations, libraryType, outputLog, sample.getTophatStyleReadGroupInformationString(), fusionSearch))
    }
    return alignedBamFile
  }

  /**
   * Base class for tophat. All general setting independent of wether it's single or double stranded alignment goes here.
   */
  abstract class tophatBase(sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String,
                            outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false)
    extends EightCoreJob {
    // Sometime this should be kept, sometimes it shouldn't
    this.isIntermediate = false
    analysisName = "tophatBase"
    @Input var dir = sampleOutputDir
    @Input var ref = reference

    var stdOut = outputFile

    override def jobRunnerJobName = projectName.get + "_tophat"
    this.jobName = projectName.get + "_tophat"

    // Only add --GTF option if this has been defined as an option on the command line
    def annotationString = if (annotations.isDefined && annotations.get != null)
      " --GTF " + annotations.get.getAbsolutePath() + " "
    else
      ""

    // Only do fussion search if it has been defined on the command line.
    // Since it requires a lot of ram, make sure it requests a fat node.
    def fusionSearchString = if (fusionSearch) {
      this.jobNativeArgs +:= "-p node -C fat -A " + uppmaxConfig.projId
      this.memoryLimit = Some(48)
      " --fusion-search --bowtie1 --no-coverage-search "
    } else ""
  }

  /**
   * Commandline wrapper for for single read alignment with tophat.
   */
  case class singleReadTophat(@Input fastqs1: File, @Output alignedBamFile: File, sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String,  outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false)
    extends tophatBase(sampleOutputDir, reference, annotations, libraryType, outputFile, readGroupInfo, fusionSearch) {
    analysisName = "singleReadTophat"
    @Input var files1 = fastqs1

    val file1String = files1.getAbsolutePath()

    def commandLine = tophatPath +
      " --library-type " + libraryType +
      annotationString +
      " -p " + tophatThreads +
      " --output-dir " + dir +
      " " + readGroupInfo +
      " --keep-fasta-order " +
      fusionSearchString +
      ref + " " + file1String +
      " 1> " + stdOut

  }

  /**
   * Commandline wrapper for for paired end alignment with tophat.
   */
  case class tophat(@Input fastqs1: File,@Input  fastqs2: File, @Output alignedBamFile: File, sampleOutputDir: File, reference: File, annotations: Option[File], libraryType: String, outputFile: File, readGroupInfo: String, fusionSearch: Boolean = false)
    extends tophatBase(sampleOutputDir, reference, annotations, libraryType, outputFile, readGroupInfo, fusionSearch) {
    analysisName = "Tophat"
    @Input var files1 = fastqs1
    @Input var files2 = fastqs2

    val file1String = files1.getAbsolutePath()
    val file2String = if (files2 != null) files2.getAbsolutePath() else ""

    def commandLine = tophatPath +
      " --library-type " + libraryType +
      annotationString +
      " -p " + tophatThreads +
      " --output-dir " + dir +
      " " + readGroupInfo +
      " --keep-fasta-order " +
      fusionSearchString +
      ref + " " + file1String + " " + file2String +
      " 1> " + stdOut
  }
}

// Possible alignment options.
sealed trait AlignerOption
case object BwaMem extends AlignerOption
case object BwaAln extends AlignerOption
case object NvoAln extends AlignerOption
case object NvoAlnCS extends AlignerOption

/**
 * Utility classes and functions for running bwa
 */
class AlignmentUtils(qscript: QScript, alignerPath: String, numThreads: Int, samtoolsPath: String,
                     projectName: Option[String], uppmaxConfig: UppmaxConfig,
                     otherResources: ResourceMap = Map()) extends AligmentUtils(projectName, uppmaxConfig) {

  /**
   * @param qscript						the qscript to in which the alignments should be used (usually "this")
   * @param fastqs						the read pair container with the fastq files
   * @param readGroupInfo				read group info to be added to bam file
   * @param	reference					reference to align to
   * @param outputDir					output it to this dir
   * @param isIntermediateAlignment		true if the files should be deleted when dependents have been run.
   * @param aligner						Aligner to use
   * @return a bam file with aligned reads.
   */
  private def performAlignment(qscript: QScript)(fastqs: InputSeqFileContainer,
                                                 readGroupInfo: String,
                                                 reference: File,
                                                 outputDir: File,
                                                 isIntermediateAlignment: Boolean = false,
                                                 aligner: Option[AlignerOption]): File = {

    val saiFile1 = new File(outputDir + "/" + fastqs.sampleName + ".1.sai")
    val saiFile2 = new File(outputDir + "/" + fastqs.sampleName + ".2.sai")
    val alignedBamFile = new File(outputDir + "/" + fastqs.sampleName + ".bam")

    aligner match {
      case Some(BwaAln) => {
        if (fastqs.hasPair) {
          // Add jobs to the qgraph
          qscript.add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
            bwa_aln_se(fastqs.mate2, saiFile2, reference),
            bwa_sam_pe(fastqs.mate1, fastqs.mate2, saiFile1, saiFile2, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
        } else {
          qscript.add(bwa_aln_se(fastqs.mate1, saiFile1, reference),
            bwa_sam_se(fastqs.mate1, saiFile1, alignedBamFile, readGroupInfo, reference, isIntermediateAlignment))
        }
      }
      case Some(BwaMem) => {
        if (fastqs.isMatePaired) {
          qscript.add(bwa_mem(fastqs.mate1, Some(fastqs.mate2), alignedBamFile, readGroupInfo, reference, numThreads, intermediate = isIntermediateAlignment))
        } else {
          qscript.add(bwa_mem(fastqs.mate1, None, alignedBamFile, readGroupInfo, reference, numThreads, intermediate = isIntermediateAlignment))
        }
      }
      case Some(NvoAlnCS) => {
        qscript.add(novoalignCS(
          inputFile = fastqs.files.get(0),
          outBam = alignedBamFile,
          reference = reference,
          intermediate = isIntermediateAlignment,
          isPairEnd = fastqs.hasPair,
          libName = fastqs.sampleName.replaceFirst("\\..*$", ""),
          readGroupInfo = readGroupInfo
        ))

      }
      case Some(NvoAln) => throw new Exception("Aligner not implemented yet")

      case None => throw new Exception("No Aligner was set in performAlignment(...)!")
    }

    alignedBamFile
  }

  /**
   * @param 	sample			sample to align
   * @param	outputDir		output dir to use
   * @param	asIntermidiate	should this be kept of not
   * @param aligner			Aligner to be used. Defaults to BwaMem.
   * @returns a aligned bam file.
   */
  def align(sample: SampleAPI, outputDir: File, asIntermidiate: Boolean, aligner: Option[AlignerOption] = Some(BwaMem)): File = {

    val sampleName = sample.getSampleName()
    val fastqs = sample.getInputSeqFiles()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Add uniq name for run
    fastqs.sampleName = sampleName + "." + sample.getReadGroupInformation.platformUnitId

    // Check that the reference is indexed
    checkReferenceIsBwaIndexed(reference)

    // Run the alignment
    performAlignment(qscript)(fastqs, readGroupInfo, reference, outputDir, asIntermidiate, aligner)
  }

  // Find suffix array coordinates of single end reads
  case class bwa_aln_se(fastq1: File, outSai: File, reference: File) extends EightCoreJob {
    @Input(doc = "fastq file to be aligned") var fastq = fastq1
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output sai file") var sai = outSai

    this.isIntermediate = true

    def commandLine = alignerPath + " aln -t " + numThreads + " -q 5 " + ref + " " + fastq + " > " + sai
    override def jobRunnerJobName = projectName.get + "_bwaAln"
  }

  // Help function to create samtools sorting and indexing paths
  def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " + samtoolsPath + " sort - " + alignedBam.getAbsolutePath().replace(".bam", "") + ";" +
    samtoolsPath + " index " + alignedBam.getAbsoluteFile()

  // Perform alignment of single end reads
  case class bwa_sam_se(fastq: File, inSai: File, outBam: File, readGroupInfo: String, reference: File, intermediate: Boolean = false) extends OneCoreJob {
    @Input(doc = "fastq file to be aligned") var mate1 = fastq
    @Input(doc = "bwa alignment index file") var sai = inSai
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output aligned bam file") var alignedBam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = alignerPath + " samse " + ref + " " + sai + " " + mate1 + " -r " + readGroupInfo +
      sortAndIndex(alignedBam)
    override def jobRunnerJobName = projectName.get + "_bwaSamSe"
  }

  // Perform alignment of paired end reads
  case class bwa_sam_pe(fastq1: File, fastq2: File, inSai1: File, inSai2: File, outBam: File, readGroupInfo: String, reference: File, intermediate: Boolean = false) extends OneCoreJob {
    @Input(doc = "fastq file with mate 1 to be aligned") var mate1 = fastq1
    @Input(doc = "fastq file with mate 2 file to be aligned") var mate2 = fastq2
    @Input(doc = "bwa alignment index file for 1st mating pair") var sai1 = inSai1
    @Input(doc = "bwa alignment index file for 2nd mating pair") var sai2 = inSai2
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output aligned bam file") var alignedBam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = alignerPath + " sampe " + ref + " " + sai1 + " " + sai2 + " " + mate1 + " " + mate2 +
      " -r " + readGroupInfo +
      sortAndIndex(alignedBam)
    override def jobRunnerJobName = projectName.get + "_bwaSamPe"
  }

  // Bwa mem alignment container.
  case class bwa_mem(fastq1: File, fastq2: Option[File],
                     outBam: File,
                     readGroupInfo: String,
                     reference: File,
                     nbrOfThreads: Int = 15,
                     intermediate: Boolean = false) extends SixteenCoreJob {

    def sortAndIndex(alignedBam: File): String = " | " + samtoolsPath + " view -Su - | " +
    samtoolsPath + " sort -@ " + nbrOfThreads + " -m 7G " +
        " - " + alignedBam.getAbsolutePath().replace(".bam", "") + ";" +
        samtoolsPath + " index " + alignedBam.getAbsoluteFile()

    @Input(doc = "fastq file with mate 1 to be aligned") var mate1 = fastq1
      @Input(doc = "fastq file with mate 2 file to be aligned") var mate2 = fastq2
      @Input(doc = "reference") var ref = reference
      @Output(doc = "output aligned bam file") var alignedBam = outBam
      @Output(doc = "output aligned bam index file") var bamIndex = GeneralUtils.swapExt(
        outBam.getParentFile, outBam, ".bam", ".bam.bai")

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    // Setup paired end or single end case
    def mateString = if (fastq2.isDefined)
      " " + mate1 + " " + mate2.get + " "
    else
      " " + mate1 + " "

    <<<<<<< variant A

    >>>>>>> variant B

    ======= end
    // Enabling pipefail should make sure that this gets a non-zero
    // exit status should any of the programs in the pipe fail.
    def commandLine =
      <<<<<<< variant A
    "set -o pipefail; " +
      alignerPath + " mem -M -t " + nbrOfThreads + " " +
      >>>>>>> variant B
    "set -o pipefail; " +
      bwaPath + " mem -M -t " + nbrOfThreads + " " +
      ======= end
    " -R " + readGroupInfo + " " +
      ref + mateString +
      sortAndIndex(alignedBam)

    override def jobRunnerJobName = projectName.get + "_bwaMem"
  }

  // Perform Smith-Watherman aligment of single end reads
  case class bwa_sw(inFastQ: File, outBam: File, reference: File, intermediate: Boolean = false) extends EightCoreJob {
    @Input(doc = "fastq file to be aligned") var fq = inFastQ
      @Input(doc = "reference") var ref = reference
      @Output(doc = "output bam file") var bam = outBam

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    def commandLine = alignerPath + " bwasw -t " + numThreads + " " + ref + " " + fq +
      sortAndIndex(bam)
    override def jobRunnerJobName = projectName.get + "_bwaSw"
  }

  // Perform aligment of color-space input files in BAM, XSQ format.
  // This expect to work with only one library at a time.
  // TODO: Check if need support for CSFASTQ format
  case class novoalignCS(inputFile: File, outBam: File,
                         reference: File,
                         intermediate: Boolean = false,
                         isPairEnd: Boolean = false,
                         libName: String,
                         readGroupInfo: String = "") extends CommandLineFunction {

    logger.info("resourceMap size: " + otherResources.size)

    @Input(doc = "input file(s) to be aligned") var input = inputFile
      @Input(doc = "reference") var ref = reference
      @Output(doc = "output bam file") var bam = outBam

    @Argument(doc = "path to novosort for sort and index bam files")
    var novosort = getPathFromKey(otherResources, Constants.NOVOSORT)

    @Argument(doc = "path to novoalignCS for aligning of Colospace data in xsq file")
    var novoalignCS = getPathFromKey(otherResources, Constants.NOVOALIGNCS)

    @Argument(doc = "Indexed reference file for use with novoalignCS")
    var novoalignCSRef = getPathFromKey(otherResources, Constants.NVOALNCSREF)

    @Argument(doc = "Other commandline argument for NovoalignCS")
    var novoOtherParams = " -o FullNW -o SAM -r Random -k -t 20,2.5 -p 5,15 0.35,10"

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    val unsorted_bam = bam.getAbsolutePath.replace(".bam", "_unsorted.bam")

    // We actually use novosort here
    def sortAndIndex(inputBamPath: String): String = " && " + novosort + " -i -c " + numThreads +
      " -m 7G  -t ./.queue/tmp --rg " + readGroupInfo + " -o " + bam + " " + inputBamPath

    val captureBam = " | " +  samtoolsPath + " view -Sb -F4 - >" + unsorted_bam

    def commandLine = if (isPairEnd) { "not implemented yet"} else {
      novoalignCS + " -d " + novoalignCSRef + " -f " + input + " -F 'XSQ' " + libName + novoOtherParams +
        " -c " + numThreads + " 2>" + bam.getAbsolutePath.replace(".bam", ".log") + captureBam +
        sortAndIndex(unsorted_bam)
    }
    override def jobRunnerJobName = projectName.get + "_nvoalnCS"
  }
}
