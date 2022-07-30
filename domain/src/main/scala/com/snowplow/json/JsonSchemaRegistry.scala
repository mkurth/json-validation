package com.snowplow.json

import cats.data.EitherT

object JsonSchemaRegistry {

  sealed trait RegistrationError
  final case class JsonSchemaAlreadyExists(id: String)                 extends RegistrationError
  final case class GeneralRegistrationError(id: String, error: String) extends RegistrationError
  case object InvalidJson                                              extends RegistrationError

  final case class JsonSchemaRegistered(id: String)

  trait PersistenceError
  final case class SchemaNotFound(id: String)             extends PersistenceError
  final case class SchemaAlreadyExists(id: String)        extends PersistenceError
  final case class GeneralPersistenceError(error: String) extends PersistenceError

  final case class JsonSchemaPersisted(id: String)

  type PersistJsonSchema[F[_]] = JsonSchema => EitherT[F, PersistenceError, JsonSchemaPersisted]
  type LoadJsonSchema[F[_]]    = String => EitherT[F, PersistenceError, JsonSchema]

  def registerJsonSchema[F[_]](persistJsonSchema: PersistJsonSchema[F], loadJsonSchema: LoadJsonSchema[F])(
      jsonSchema: JsonSchema
  ): EitherT[F, RegistrationError, JsonSchemaRegistered] = ???

}
