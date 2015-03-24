package io.vamp.core.model.notification

import io.vamp.common.notification.Notification
import io.vamp.core.model.artifact.{AbstractCluster, Breed, Trait}

case class UnresolvedEndpointPortError(name: Trait.Name, value: Any) extends Notification

case class UnresolvedParameterError(name: Trait.Name, value: Any) extends Notification

case class NonUniqueBlueprintBreedReferenceError(name: String) extends Notification

case class UnresolvedBreedDependencyError(breed: Breed, dependency: (String, Breed)) extends Notification

case class RoutingWeightError(cluster: AbstractCluster) extends Notification

case class UnresolvedScaleEscalationTargetCluster(cluster: AbstractCluster, target: String) extends Notification