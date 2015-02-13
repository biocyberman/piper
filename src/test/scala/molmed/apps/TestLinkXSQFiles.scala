package molmed.apps

/**
 * Created by vql on 08/02/15.
 */
import java.io.File

import molmed.utils.xsq.MultiXSQFiles
import org.broadinstitute.gatk.engine.CommandLineGATK.getVersionNumber

object TestLinkXSQFiles extends App{

  val myXSQs = Seq(
  new File("src/test/resources/Test.Barcoded.PE.xsq"),
  new File("src/test/resources/Test.Barcoded.PE.xsq")
  )
// val linkXSQ = new MultiXSQFiles("linkTest.xsq", myXSQs)
//  linkXSQ.linkXSQFiles
//  linkXSQ.close
  val joinXsq = new MultiXSQFiles("joinTest.xsq", myXSQs)
  joinXsq.joinXSQFiles
  joinXsq.close
  println(org.broadinstitute.gatk.engine.CommandLineGATK.getVersionNumber)
}
