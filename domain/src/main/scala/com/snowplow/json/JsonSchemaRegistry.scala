package com.snowplow.json

import cats.data.EitherT

object JsonSchemaRegistry {

  sealed trait RegistrationError
  final case class JsonSchemaAlreadyExists(id: String) extends RegistrationError
  case object InvalidJson                              extends RegistrationError

  final case class JsonSchemaRegistered(id: String)

  final case class JsonSchemaPersisted(id: String)

  trait PersistenceError
  final case class SchemaNotFound(id: String)             extends PersistenceError
  final case class SchemaAlreadyExists(id: String)        extends PersistenceError
  final case class GeneralPersistenceError(error: String) extends PersistenceError

  type PersistJsonSchema[F[_]] = JsonSchema => EitherT[F, PersistenceError, JsonSchemaPersisted]

  def registerJsonSchema[F[_]](persistJsonSchema: PersistJsonSchema[F])(
      jsonSchema: JsonSchema
  ): EitherT[F, RegistrationError, JsonSchemaRegistered] = ???

  type LoadJsonSchema[F[_]] = String => EitherT[F, PersistenceError, JsonSchema]
  def getJsonSchemaById[F[_]]: LoadJsonSchema[F] = ???

}
