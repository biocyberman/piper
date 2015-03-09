package molmed.utils

import java.io.File

import molmed.queue.setup.{InputSeqFileContainer, SampleAPI}
import molmed.utils.traits._
import org.broadinstitute.gatk.queue.QScript

/**
 * Holds classes and functions used for aligning with tophat
 */
class AlignmentUtilsTophat(tophatPath: String, tophatThreads: Int, projectName: Option[String],
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
