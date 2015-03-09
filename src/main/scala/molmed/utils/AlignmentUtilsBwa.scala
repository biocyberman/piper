package molmed.utils

import java.io.File

import molmed.config.Constants
import molmed.config.FileVersionUtilities._
import molmed.queue.setup.{InputSeqFileContainer, SampleAPI}
import molmed.utils.GeneralUtils._
import molmed.utils.traits._
import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.slf4j.LoggerFactory

/**
 * Utility classes and functions for running bwa
 */
class AlignmentUtilsBwa(qscript: QScript, bwaPath: String, numThreads: Int, samtoolsPath: String,
                     projectName: Option[String], uppmaxConfig: UppmaxConfig,
                     otherResources: ResourceMap = Map()) extends AligmentUtils(projectName, uppmaxConfig) {

  val log = LoggerFactory.getLogger(classOf[AlignmentUtilsBwa])
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

    val saiFile1 = new File(outputDir + "/" + fastqs.sampleName.get + ".1.sai")
    val saiFile2 = new File(outputDir + "/" + fastqs.sampleName.get + ".2.sai")
    val alignedBamFile = new File(outputDir + "/" + fastqs.sampleName.get + ".bam")

    log.debug("otherArguments: {}", otherArguments)

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
      case otherAligner:Option[AlignerOption] => throw new Exception("Aligner not handled by this BWA-based util class, or No aligner supplied")
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
  def align(sample: SampleAPI,
            outputDir: File,
            asIntermidiate: Boolean,
            aligner: Option[AlignerOption] = Some(BwaMem),
            otherArguments:String = ""): File = {

    val sampleName = sample.getSampleName()
    val fastqs = sample.getInputSeqFiles()
    val readGroupInfo = sample.getBwaStyleReadGroupInformationString()
    val reference = sample.getReference()

    // Add uniq name for run
    fastqs.sampleName = Some(sampleName + "." + sample.getReadGroupInformation.platformUnitId)

    // Check that the reference is indexed
    checkReferenceIsBwaIndexed(reference)

    // Run the alignment
    performAlignment(qscript)(fastqs, readGroupInfo, reference, outputDir, asIntermidiate, aligner, otherArguments)
  }

  // Find suffix array coordinates of single end reads
  case class bwa_aln_se(fastq1: File, outSai: File, reference: File) extends EightCoreJob {
    @Input(doc = "fastq file to be aligned") var fastq = fastq1
    @Input(doc = "reference") var ref = reference
    @Output(doc = "output sai file") var sai = outSai

    this.isIntermediate = true

    def commandLine = bwaPath + " aln -t " + numThreads + " -q 5 " + ref + " " + fastq + " > " + sai
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

    def commandLine = bwaPath + " samse " + ref + " " + sai + " " + mate1 + " -r " + readGroupInfo +
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

    def commandLine = bwaPath + " sampe " + ref + " " + sai1 + " " + sai2 + " " + mate1 + " " + mate2 +
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

    // The output from this is a samfile, which can be removed later
    this.isIntermediate = intermediate

    // Setup paired end or single end case
    def mateString = if (fastq2.isDefined)
      " " + mate1 + " " + mate2.get + " "
    else
      " " + mate1 + " "

    def commandLine =
      bwaPath + " mem -M -t " + nbrOfThreads + " " +
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

    def commandLine = bwaPath + " bwasw -t " + numThreads + " " + ref + " " + fq +
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
    val unsorted_done= bam.getAbsolutePath.replace(".bam", "_unsorted.done")
    val alignLog: String = bam.getAbsolutePath.replace(".bam", ".log")

    val pairEndParams = if (isPairEnd) "" else "" // placeholder for now.

    novoOtherParams += pairEndParams

    // We actually use novosort here
    def sortAndIndex(inputBamPath: String): String =
      s"  $novosort -i -c  $numThreads -m 7G  -t ./.queue/tmp --rg $readGroupInfo -o $bam $inputBamPath"

    val captureBam = s" | $samtoolsPath view -Sb -F4 - > $unsorted_bam && touch $unsorted_done"

    def commandLine = if (new File(alignLog).exists) {
      sortAndIndex(unsorted_bam)

    } else {
      s"$novoalignCS -d $novoalignCSRef -f $input -F 'XSQ' $libName $novoOtherParams" +
        s" -c $numThreads  2> $alignLog $captureBam &&" +
        sortAndIndex(unsorted_bam)
    }
    override def jobRunnerJobName = projectName.get + "_nvoalnCS"
  }
}
