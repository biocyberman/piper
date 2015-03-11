package molmed.utils

import java.io.File
import molmed.qscripts.QPipe

import scala.collection.JavaConversions._

import molmed.config.Constants
import molmed.config.FileVersionUtilities._
import molmed.queue.setup.{InputSeqFileContainer, SampleAPI}
import molmed.utils.traits._
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.slf4j.LoggerFactory

case object DataNature extends Enumeration{
  type DataType = Value
  val RNASEQ, DNASEQ, METHYLATION, UNKNOWN = Value
}

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

  private val dn = qscript match {
    case q:QPipe => q.dataNature
    case q:QScript => "Unknown"
  }
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
      case Some(NvoAlnMpi) => {
        val novoalgignCommand = novoalignCSMpi(
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

  /**
   * This is a helper class.
   * In case of RNAseq, we will do a preprocessing to convert the alignment to normal BAM format before feeding
   * the output to sortAndIndex
   * @param inputBamPath A string containing the unsorted BAM file which is the raw ouput from alingment step
   */
   case class ConvertRnaAlignment(inputBamPath: String) extends CommandLineFunction{

    val transcriptomeParser = getPathFromKey(otherResources, Constants.TRANSCRIPTOMEPARSER)

    def rnaAlignmentProcessing(inputBamPath:String):String = {
      log.debug(s"Converting RNA alignment to normal BAM...")
      val tmpBam = inputBamPath.replace(".bam", ".tmp.bam")
         val tmpSam = inputBamPath.replace(".bam", ".fifo.sam")
         // pathTo/USeq/USeq_8.X.X/Apps/SamTranscriptomeParser -f my.sam  -a 50000 -n 100 -u -s my_Converted.sam
         s"mkfifo $tmpSam && sh -c '$samtoolsPath view -h $inputBamPath >$tmpSam&' && "  +
         s" $transcriptomeParser -f $tmpSam -a 900 -n 100 -u -s $tmpBam " +
         s"&& mv -f $tmpBam $inputBamPath && rm -f $tmpSam"
       }

    override def commandLine: String = rnaAlignmentProcessing(inputBamPath)
    override def jobRunnerJobName = projectName.get + "_RnaAlnConvert"
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
    var novoaligner = getPathFromKey(otherResources, Constants.NOVOALIGNCS)

    @Argument(doc = "Indexed reference file for use with novoaligner")
    var novoalignRef = getPathFromKey(otherResources, Constants.NVOALNCSREF)

    @Argument(doc = "Wether the sequence is RNAseq, DNAseq or Methylation purpose")
    var dataNature:String = dn

    val isRNASeq: Boolean = DataNature.withName(dataNature.toUpperCase) == DataNature.RNASEQ

    @Argument(doc = "Other commandline argument for NovoalignCS")
    var novoOtherParams =  if (isRNASeq) " -o SAM -r Random -k -t 20,2.5 -p 5,15 0.35,10" else
      " -o FullNW -o SAM -r Random -k -t 20,2.5 -p 5,15 0.35,10"

    log.debug(s"Processing RNAseq data?: $isRNASeq")

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    val unsorted_bam = bam.getAbsolutePath.replace(".bam", "_unsorted.bam")
    val unsorted_done= bam.getAbsolutePath.replace(".bam", "_unsorted.done")
    val convert_done= bam.getAbsolutePath.replace(".bam", "_convert.done")
    val alignLog: String = bam.getAbsolutePath.replace(".bam", ".log")

    val pairEndParams = if (isPairEnd) "" else "" // placeholder for now.

    novoOtherParams += pairEndParams

    val captureBam = s" | $samtoolsPath view -Sb -F4 - > $unsorted_bam && touch $unsorted_done"
    val rnaAlignmentConversionCompleted = new File(convert_done).exists()
    /**
     * Option wether to run MPI mode or mutlicore mode
     * @return an empty string if running multicore mode, some MPI compatible command prefix if MPI mode.
     */
    def runnerOption: String = ""

    val transcriptomeParser = getPathFromKey(otherResources, Constants.TRANSCRIPTOMEPARSER)

    def rnaAlignmentProcessing(inputBamPath:String):String = {
      if (isRNASeq && !rnaAlignmentConversionCompleted) {
        log.debug(s"Converting RNA alignment to normal BAM...")
        val tmpBam = inputBamPath.replace(".bam", ".tmp.bam")
        val tmpSam = inputBamPath.replace(".bam", ".fifo.sam")
        // pathTo/USeq/USeq_8.X.X/Apps/SamTranscriptomeParser -f my.sam  -a 50000 -n 100 -u -s my_Converted.sam
        s"mkfifo $tmpSam && sh -c '$samtoolsPath view -h $inputBamPath >$tmpSam&' && "  +
        s" $transcriptomeParser -f $tmpSam -a 900 -n 100 -u -s $tmpBam " +
        s"&& mv -f $tmpBam $inputBamPath && rm -f $tmpSam && touch $convert_done &&"
      }  else ""

    }

    // We actually use novosort here
    def sortAndIndex(inputBamPath: String): String ={
      rnaAlignmentProcessing(inputBamPath) +
      s" $novosort -i -c  $numThreads -m 7G  -t ./.queue/tmp --rg $readGroupInfo -o $bam $inputBamPath"

    }

    def commandLine = {
      val rawAlignmentCompleted: Boolean = new File(unsorted_done).exists
      if (rawAlignmentCompleted) {
        sortAndIndex(unsorted_bam)
      } else {
        s"$runnerOption $novoaligner -d $novoalignRef -f $input -F 'XSQ' $libName $novoOtherParams" +
        s" -c $numThreads  2> $alignLog $captureBam && " +
        sortAndIndex(unsorted_bam)
      }
    }
  }

  case class novoalignCS(inputFile: File, outBam: File,
                         reference: File,
                         intermediate: Boolean = false,
                         isPairEnd: Boolean = false,
                         libName: String,
                         readGroupInfo: String = "") extends novoalignCSBase(
    inputFile, outBam, reference, intermediate, isPairEnd, libName, readGroupInfo) {

    // Make sure no MPI runner is set in this case
    override def runnerOption = ""

    override def jobRunnerJobName = projectName.get + "_nvoalnCS"
  }

  case class novoalignCSMpi(inputFile: File, outBam: File,
                            reference: File,
                            intermediate: Boolean = false,
                            isPairEnd: Boolean = false,
                            libName: String,
                            readGroupInfo: String = "") extends  novoalignCSBase(
    inputFile, outBam, reference, intermediate, isPairEnd, libName, readGroupInfo) {

    @Argument(doc = "Path to MPICH2 runner")
    var mpiexecPath = getPathFromKey(otherResources, Constants.MPIEXECPATH)

    override def runnerOption:String = {
      s"$mpiexecPath -np $numThreads -hosts 'n0,n1,n2,n3' "
    }

    override def jobRunnerJobName = projectName.get + "_nvoalnCSMpi"
  }
}
