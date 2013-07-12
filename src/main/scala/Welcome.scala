package net.tenthbit.protocol

case class Welcome(
  override val ex: Option[Map[String, Any]] = None,
  op: String = "welcome") extends Payload {}
