package io.vamp.core.cli.commands

import io.vamp.core.cli.backend.VampHostCalls
import io.vamp.core.cli.commandline.{ConsoleHelper, Parameters}
import io.vamp.core.model.artifact._
import io.vamp.core.model.serialization.CoreSerializationFormat
import org.json4s.native.Serialization._
import org.yaml.snakeyaml.DumperOptions.FlowStyle
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

import scala.io.Source

object PerformCommand extends Parameters {

  import ConsoleHelper._

  implicit val formats = CoreSerializationFormat.default

  def doCommand(command: CliCommand)(implicit options: OptionMap): Unit = {

    implicit val vampHost: String = if(command.requiresHostConnection) getParameter(host) else "Not needed"

    command.commandType match {
      case CommandType.Create => doCreateCommand(command)
      case CommandType.Delete => doDeleteCommand(command)
      case CommandType.Update => doUpdateCommand(command)
      case CommandType.Deploy => doDeployCommand(command)
      case CommandType.Inspect => doInspectCommand(command)
      case CommandType.List => doListCommand(command)
      case CommandType.Other => doOtherCommand(command)
    }
  }

  private def doListCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {
    case _: ListBreedsCommand =>
      println("NAME".padTo(25, ' ').bold.cyan + "DEPLOYABLE")
      VampHostCalls.getBreeds.foreach({ case b: DefaultBreed => println(s"${b.name.padTo(25, ' ')}${b.deployable.name}") })

    case _: ListBlueprintsCommand =>
      println("NAME".padTo(40, ' ').bold.cyan + "ENDPOINTS")
      VampHostCalls.getBlueprints.foreach(blueprint => println(s"${blueprint.name.padTo(40, ' ')}${blueprint.endpoints.map(e => s"${e.name} -> ${e.value.get}").mkString(", ")}"))

    case _: ListDeploymentsCommand =>
      println("NAME".padTo(40, ' ').bold.cyan + "CLUSTERS")
      VampHostCalls.getDeployments.foreach(deployment => println(s"${deployment.name.padTo(40, ' ')}${deployment.clusters.map(c => s"${c.name}").mkString(", ")}"))

    case _: ListEscalationsCommand =>
      println("NAME".padTo(25, ' ').bold.cyan + "TYPE".padTo(20, ' ') + "SETTINGS")
      VampHostCalls.getEscalations.foreach({
        case b: ScaleInstancesEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleCpuEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: ScaleMemoryEscalation => println(s"${b.name.padTo(25, ' ')}${b.`type`.padTo(20, ' ')}[${b.minimum}..${b.maximum}(${b.scaleBy})] => ${b.targetCluster.getOrElse("")}")
        case b: Escalation => println(s"${b.name.padTo(25, ' ')}")
        case x => println(x)
      })

    case _: ListFiltersCommand =>
      println("NAME".padTo(25, ' ').bold.cyan + "CONDITION")
      VampHostCalls.getFilters.foreach({ case b: DefaultFilter => println(s"${b.name.padTo(25, ' ')}${b.condition}") })

    case _: ListRoutingsCommand =>
      println("NAME".padTo(25, ' ').bold.cyan + "FILTERS")
      VampHostCalls.getRoutings.foreach({ case b: DefaultRouting => println(s"${b.name.padTo(25, ' ')}${b.filters.map({ case d: DefaultFilter => s"${d.condition}" }).mkString(", ")}") })

    case _: ListScalesCommand =>
      println("NAME".padTo(25, ' ').bold.cyan + "CPU".padTo(7, ' ') + "MEMORY".padTo(10, ' ') + "INSTANCES")
      VampHostCalls.getScales.foreach({ case b: DefaultScale => println(s"${b.name.padTo(25, ' ')}${b.cpu.toString.padTo(7, ' ')}${b.memory.toString.padTo(10, ' ')}${b.instances}") })

    case _: ListSlasCommand =>
      println("NAME".bold.cyan)
      VampHostCalls.getSlas.foreach(sla => println(s"${sla.name}"))

    case _ => unhandledCommand _

  }

  private def doInspectCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = {
    val artifact: Option[Artifact] = command match {
      case _: InspectBreedCommand => VampHostCalls.getBreed(getParameter(name))

      case _: InspectBlueprintCommand => VampHostCalls.getBlueprint(getParameter(name))

      case _: InspectDeploymentCommand => VampHostCalls.getDeployment(getParameter(name))

      case _: InspectEscalationCommand => VampHostCalls.getEscalation(getParameter(name))

      case _: InspectFilterCommand => VampHostCalls.getFilter(getParameter(name))

      case _: InspectRoutingCommand => VampHostCalls.getRouting(getParameter(name))

      case _: InspectScaleCommand => VampHostCalls.getScale(getParameter(name))

      case _: InspectSlaCommand => VampHostCalls.getSla(getParameter(name))

      case _ => unhandledCommand _
        None
    }
    printArtifact(artifact)
  }


  private def doDeployCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {
    case _: DeployBreedCommand =>
      val deploymentId: String = getParameter(deployment)
      VampHostCalls.getDeploymentAsBlueprint(deploymentId) match {
        case Some(bp: DefaultBlueprint) =>
          VampHostCalls.getBreed(getParameter(name)) match {
            case Some(deployableBreed: DefaultBreed) =>
              val mergedBlueprint = mergeBreedInCluster(
                blueprint = bp,
                clusterName = getParameter(cluster),
                breed = deployableBreed,
                routing = getOptionalParameter(routing).flatMap(VampHostCalls.getRouting),
                scale = getOptionalParameter(scale).flatMap(VampHostCalls.getScale)
              )
              VampHostCalls.updateDeployment(deploymentId, mergedBlueprint) match {
                case Some(dep) => println(dep.name)
                case None => terminateWithError("Updating deployment failed")
              }
            case undeployableBreed => terminateWithError(s"Breed '$undeployableBreed' not usable")
          }
        case _ => // Deployment not found

      }

    case _: DeployBlueprintCommand => println(NotImplemented)

    case _ => unhandledCommand _
  }

  private def doOtherCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _: InfoCommand => println(VampHostCalls.info.getOrElse(""))

    case _: HelpCommand => showHelp(HelpCommand())

    case _: VersionCommand => println(s"CLI version: " + s"${getClass.getPackage.getImplementationVersion}".yellow.bold)

    case x: UnknownCommand => terminateWithError(s"Unknown command '${x.name}'")

    case _ => unhandledCommand _
  }


  private def doCreateCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _: CloneBreedCommand =>
      VampHostCalls.getBreed(getParameter(name)) match {
        case Some(sourceBreed: DefaultBreed) =>
          val response = VampHostCalls.createBreed(getOptionalParameter(deployable) match {
            case Some(deployableName) => sourceBreed.copy(name = getParameter(destination), deployable = Deployable(deployableName))
            case None => sourceBreed.copy(name = getParameter(destination))
          })
          println(response)
        case _ => terminateWithError("Source breed not found")
      }

    case _: CreateBreedCommand =>
      val fileContents = getOptionalParameter('file) match {
        case Some(fileName) => Source.fromFile(fileName).getLines().mkString("\n")
        case None => Source.stdin.getLines().mkString("\n")
      }
      println(VampHostCalls.createBreed(fileContents))

    case _ => unhandledCommand _
  }


  private def doDeleteCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {
    case _: RemoveBreedCommand => VampHostCalls.deleteBreed(getParameter(name))

    case _: RemoveBlueprintCommand => println(NotImplemented)

    case _ => unhandledCommand _
  }

  private def doUpdateCommand(command: CliCommand)(implicit vampHost: String, options: OptionMap) = command match {

    case _ => unhandledCommand _
  }


  private def unhandledCommand(command: CliCommand) = terminateWithError(s"Unhandled command '${command.name}'")

  private def mergeBreedInCluster(blueprint: DefaultBlueprint, clusterName: String, breed: DefaultBreed, routing: Option[Routing], scale: Option[Scale]): DefaultBlueprint =
    blueprint.copy(clusters = blueprint.clusters.filter(_.name != clusterName) ++
      blueprint.clusters.filter(_.name == clusterName).map(c => c.copy(services = c.services ++ List(Service(breed = breed, scale = scale, routing = routing))))
    )


  private def printArtifact(artifact: Option[Artifact])(implicit options: OptionMap) = {
    getOptionalParameter(json) match {
      case None => artifact.foreach(a => println(artifactToYaml(a)))
      case _ => println(VampHostCalls.prettyJson(artifact))
    }
  }

  private def artifactToYaml(artifact: Artifact): String = {
    def toJson(any: Any) = {
      any match {
        case value: AnyRef => write(value)
        case value => write(value.toString)
      }
    }
    new Yaml().dumpAs(new Yaml().load(toJson(artifact)), Tag.MAP, FlowStyle.BLOCK)
  }


}