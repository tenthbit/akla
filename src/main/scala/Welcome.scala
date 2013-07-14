package net.tenthbit.protocol

case class Welcome(
  override val ex: Option[WelcomeExtras],
  op: String = "welcome") extends Payload

case class WelcomeExtras(
  server: String,
  software: String,
  auth: List[String],
  now: Long
) extends Extras
