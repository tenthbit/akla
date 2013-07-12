# Scala-Tenthbit

An implementation of the Tenthbit protocol in Scala.

This implements two packages `net.tenthbit.protocol` for classes which
represent messages that get sent across the wire, and `net.tenthbit.server`
which provides a server using Akka's IO layer and Lift JSON, along with
Slick for storing account information.
