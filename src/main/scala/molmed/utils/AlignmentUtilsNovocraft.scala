package molmed.utils

import java.io.File
import scala.collection.JavaConversions._

import molmed.config.Constants
import molmed.config.FileVersionUtilities._
import molmed.queue.setup.{InputSeqFileContainer, SampleAPI}
import molmed.utils.traits._
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.slf4j.LoggerFactory

/**
 * Utility classes and functions for running bwa
 * @param qscript	 the qscript to in which the alignments should be used (usually "this")
 * @param bwaPath  path to bwa aligner
 * @param numThreads
 * @param samtoolsPath
 * @param projectName
 * @param uppmaxConfig
 * @param otherResources
 * @return a bam file with aligned reads.
 */
class AlignmentUtilsNovocraft(qscript: QScript, bwaPath: String, numThreads: Int, samtoolsPath: String,
                              projectName: Option[String], uppmaxConfig: UppmaxConfig,
                              otherResources: ResourceMap = Map()) extends AligmentUtils(projectName, uppmaxConfig) {


  val log = LoggerFactory.getLogger(classOf[AlignmentUtilsNovocraft])
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
                                                 aligner: Option[AlignerOption],
                                                 otherArguments: String = ""): File = {

    val alignedBamFile = new File(outputDir + "/" + fastqs.sampleName.get + ".bam")

    log.debug("otherArguments: {}", otherArguments)

    aligner match {
      case Some(NvoAlnCS) => {
        val novoalgignCommand = novoalignCS(
          inputFile = fastqs.files.get(0),
          outBam = alignedBamFile,
          reference = reference,
          intermediate = isIntermediateAlignment,
          isPairEnd = fastqs.hasPair,
          libName = fastqs.sampleName.get.replaceFirst("\\..*$", ""),
          readGroupInfo = readGroupInfo)
        novoalgignCommand.novoOtherParams +=  " " + otherArguments
        log.debug("novoOtherParams: " + novoalgignCommand.novoOtherParams)
        qscript.add(novoalgignCommand)

      }
      case Some(NvoAln) => throw new Exception("Aligner not implemented yet")
      case Some(NvoAlnMpi) => throw new Exception("Aligner not implemented yet")
      case Some(NvoAlnCSMpi) => throw new Exception("Aligner not implemented yet")

      case otherAligner:Option[AlignerOption] =>
        throw new Exception("Aligner not handled by this Novoalign-based util class, or no aligner supplied")

    }

    alignedBamFile
  }

  /**
   * @param 	sample			sample to align
   * @param	outputDir		output dir to use
   * @param	asIntermidiate	should this be kept of not
   * @param aligner			Aligner to be used. Defaults to BwaMem.
   * @return a aligned bam file.
   */
  def align(sample: SampleAPI,
            outputDir: File,
            asIntermidiate: Boolean,
            aligner: Option[AlignerOption] = Some(BwaMem),
            otherArguments:String = ""): File = {

    val sampleName = sample.getSampleName()
    val inputSeqFiles = sample.getInputSeqFiles()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Add uniq name for run
    inputSeqFiles.sampleName = Some(sampleName + "." + sample.getReadGroupInformation.platformUnitId)

    // Run the alignment
    performAlignment(qscript)(inputSeqFiles, readGroupInfo, reference, outputDir, asIntermidiate, aligner, otherArguments)
  }




  // Perform aligment of color-space input files in BAM, XSQ format.
  // This expect to work with only one library at a time.
  abstract class novoalignCSBase(inputFile: File, outBam: File,
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
    val unsorted_done= bam.getAbsolutePath.replace(".bam", "_unsorted.done")
    val alignLog: String = bam.getAbsolutePath.replace(".bam", ".log")

    val pairEndParams = if (isPairEnd) "" else "" // placeholder for now.

    novoOtherParams += pairEndParams
    val captureBam = s" | $samtoolsPath view -Sb -F4 - > $unsorted_bam && touch $unsorted_done"

    // We actually use novosort here
    def sortAndIndex(inputBamPath: String): String =
      s"  $novosort -i -c  $numThreads -m 7G  -t ./.queue/tmp --rg $readGroupInfo -o $bam $inputBamPath"

    def commandLine = if (new File(alignLog).exists) {
      sortAndIndex(unsorted_bam)

    } else {
      s"$novoalignCS -d $novoalignCSRef -f $input -F 'XSQ' $libName $novoOtherParams" +
      s" -c $numThreads  2> $alignLog $captureBam &&" +
      sortAndIndex(unsorted_bam)
    }
  }
  case class novoalignCS(inputFile: File, outBam: File,
                         reference: File,
                         intermediate: Boolean = false,
                         isPairEnd: Boolean = false,
                         libName: String,
                         readGroupInfo: String = "") extends novoalignCSBase(
    inputFile, outBam, reference, intermediate, isPairEnd, libName, readGroupInfo) {

    override def jobRunnerJobName = projectName.get + "_nvoalnCS"
  }
}
