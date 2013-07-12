package net.tenthbit.protocol

trait Payload {
  val op: String
  val id: Option[String] = None
  val ts: Option[Long] = None
  val tp: Option[String] = None
  val sr: Option[String] = None
  val ex: Option[Map[String, Any]] = None
}
