package com.snowplow.json

import cats.Monad
import cats.data.EitherT

object JsonSchemaRegistry {

  sealed trait RegistrationError
  final case class JsonSchemaAlreadyExists(id: String)                 extends RegistrationError
  final case class GeneralRegistrationError(id: String, error: String) extends RegistrationError
  final case class ConcurrentWritesError(id: String, error: String)    extends RegistrationError
  case object InvalidJson                                              extends RegistrationError

  final case class JsonSchemaRegistered(id: String)

  trait PersistenceError
  final case class SchemaNotFound(id: String)             extends PersistenceError
  final case class SchemaAlreadyExists(id: String)        extends PersistenceError
  final case class GeneralPersistenceError(error: String) extends PersistenceError

  final case class JsonSchemaPersisted(id: String)

  type PersistJsonSchema[F[_]] = JsonSchema => EitherT[F, PersistenceError, JsonSchemaPersisted]
  type LoadJsonSchema[F[_]]    = String => EitherT[F, PersistenceError, JsonSchema]

  def registerJsonSchema[F[_]: Monad](persistJsonSchema: PersistJsonSchema[F], loadJsonSchema: LoadJsonSchema[F])(
      jsonSchema: JsonSchema
  ): EitherT[F, RegistrationError, JsonSchemaRegistered] =
    loadJsonSchema(jsonSchema.id)
      .biflatMap(
        {
          case GeneralPersistenceError(error) => EitherT.leftT(GeneralRegistrationError(jsonSchema.id, error))
          case SchemaAlreadyExists(id)        => EitherT.leftT(GeneralRegistrationError(id, "schema already exists"))
          case SchemaNotFound(_)              =>
            persistJsonSchema(jsonSchema)
              .leftMap[RegistrationError] {
                case GeneralPersistenceError(error) => GeneralRegistrationError(jsonSchema.id, error)
                case SchemaAlreadyExists(id)        =>
                  ConcurrentWritesError(id, "schema already exists, probably due to concurrent requests")
                case SchemaNotFound(id)             => JsonSchemaAlreadyExists(id)
              }
              .map(persisted => JsonSchemaRegistered(persisted.id))
        },
        _ => EitherT.leftT(JsonSchemaAlreadyExists(jsonSchema.id))
      )

}
