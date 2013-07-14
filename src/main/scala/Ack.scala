package net.tenthbit.protocol

case class Ack(
  override val ex: Option[AckExtras],
  op: String = "ack") extends Payload

case class AckExtras(
  `for`: String
) extends Extras
