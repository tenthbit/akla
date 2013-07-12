package net.tenthbit.protocol

/** A client who is connecting/connected to the network. */
trait User {
  val username: String
  val ticket: Option[String]
}
