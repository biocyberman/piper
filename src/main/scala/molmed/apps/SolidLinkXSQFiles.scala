package molmed.apps

import java.io.File

import molmed.apps.setupcreator.ConsoleInputParser.packOptionalValue
import molmed.utils.xsq.MultiXSQFiles
import scopt.OptionParser

/**
 * An simple application to link XSQ files into one XSQ files that contains soft links of data to other XSQ files.
 * This allows us to run the pipeline from this linking XSQ file.
 */
object SolidLinkXSQFiles extends App {

  case class Config(
                     outputFile: Option[String] = None,
                     action: Option[String] = "link",
                     seqFiles: Option[Seq[File]] = Some(Seq()))

  val parser = new OptionParser[Config]("SolidLinkXSQFiles") {
    head("SolidLinkXSQFiles", " - A utility program to link XSQ files into one XSQ file that contains soft links of data to other XSQ files.")

    opt[String]('o', "output") required () valueName ("Name of linking XSQ file.") action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text ("This is a required argument.")

    opt[String]('a', "action") required () valueName ("Link or merge files. Put 'link' for linking and 'merge' for merging") action { (x, c) =>
      c.copy(action = Some(x))
    } text ("This is a required argument.")

    opt[File]('i', "input_xsqfiles") unbounded () optional () valueName ("Input paths to XSQ files to link.") action { (x, c) =>
      c.copy(seqFiles = c.seqFiles.getOrElse(Seq()) :+ x)
    } text ("This is a required argument. Can be specified multiple times.")
    
  }

  // Start up the app!


  parser.parse(args, new Config()) map { config =>

    val allFieldsAreSet =
      config.getClass().getDeclaredFields.
        forall(p => p.isDefined)

    if (allFieldsAreSet)
      processXSQFiles(config)
    else
      parser.showUsage

  } getOrElse {
    // arguments are bad, usage message will have been displayed
  }

  def processXSQFiles(config: Config): Unit = {

    val handler = new MultiXSQFiles(config.outputFile.get, config.seqFiles.get)

    if (config.action.get.toLowerCase == "link") handler.linkXSQFiles else handler.joinXSQFiles

    handler.close
    println("Successfully created: " + config.outputFile.get + ".")
  }
}