package io.vamp.core.persistence.slick.components

import io.strongtyped.active.slick.Profile
import io.vamp.core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.vamp.core.persistence.slick.model.DeploymentModel

import scala.language.implicitConversions
import scala.slick.util.Logging


trait VampSchema extends Logging {
  this: VampTables with VampTableQueries with Profile =>

  import jdbcDriver.simple._

  val Deployments = NameableEntityTableQuery[DeploymentModel, DeploymentTable](tag => new DeploymentTable(tag))

  class DeploymentTable(tag: Tag) extends NameableEntityTable[DeploymentModel](tag, "deployments") {
    def * = (id.?, name) <>(DeploymentModel.tupled, DeploymentModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def idx = index("idx_deployments", name, unique = true)

    def name = column[String]("name")
  }

}
