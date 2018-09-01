package org.dwrs.consul.requests

import com.softwaremill.sttp.Method

import scala.collection.Map
import scala.concurrent.duration.FiniteDuration

object AgentModel {


  sealed trait Check

  final case class CheckRequest(name: Option[String] = None, id: Option[String] = None,
                          deregisterCriticalServiceAfter: Option[String] = None,
                          interval: Option[String] = None, notes: Option[String] = None,
                          http: Option[String] = None, method: Option[String] = None,
                          timeout: Option[String] = None, tcp: Option[String] = None,
                          ttl: Option[String] = None, serviceId: Option[String] = None,
                          status: Option[String] = None, tlsSkipVerify: Option[String] = None) extends Check

  final case class TtlCheckRequest(name: Option[String] = None,
                                   id: Option[String] = None,
                                   ttl: Option[String] = None,
                                   deregisterCriticalServiceAfter: Option[String] = None,
                                   serviceId: Option[String] = None,
                                   status: Option[String] = None,
                                   notes: Option[String] = None,
                                   tlsSkipVerify: Option[String] = None
                                  ) extends Check

  final case class HttpCheckRequest(name: String,
                                    id: Option[String] = None,
                                    http: String,
                                    method: Method,
                                    interval: FiniteDuration,
                                    deRegisterCriticalAfter: FiniteDuration,
                                    notes: String,
                                    serviceId: String,
                                    status: String) extends Check

  case class ServiceRegistration(name: String, id: Option[String],
                                 address: Option[String] = None,
                                 port: Int,
                                 checks: Option[List[Check]] = None,
                                 tags: Option[List[String]] = None,
                                 enableTagOverride: Boolean = false,
                                 meta: Option[Map[String,String]] = None)

  case class CheckUpdate(status: String, output: String)
}
