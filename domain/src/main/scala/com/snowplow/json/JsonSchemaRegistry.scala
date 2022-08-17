package com.snowplow.json

import cats.Monad
import com.github.fge.jackson.JsonLoader

import scala.util.{Failure, Success, Try}
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps

object JsonSchemaRegistry {

  sealed trait RegistrationError
  final case class JsonSchemaAlreadyExists(id: String)                 extends RegistrationError
  final case class GeneralRegistrationError(id: String, error: String) extends RegistrationError
  final case class ConcurrentWritesError(id: String, error: String)    extends RegistrationError
  case object InvalidJson                                              extends RegistrationError

  final case class JsonSchemaRegistered(id: String)

  sealed trait PersistenceError
  final case class SchemaNotFound(id: String)             extends PersistenceError
  final case class SchemaAlreadyExists(id: String)        extends PersistenceError
  final case class GeneralPersistenceError(error: String) extends PersistenceError

  final case class JsonSchemaPersisted(id: String)

  type PersistJsonSchema[F[_]] = JsonSchema => F[PersistenceError | JsonSchemaPersisted]
  type LoadJsonSchema[F[_]]    = String => F[PersistenceError | JsonSchema]

  def registerJsonSchema[F[_]: Monad](persistJsonSchema: PersistJsonSchema[F], loadJsonSchema: LoadJsonSchema[F])(
      jsonSchema: JsonSchema
  ): F[RegistrationError | JsonSchemaRegistered] =
    Try(JsonLoader.fromString(jsonSchema.body)) match {
      case Failure(_) => Monad[F].pure(InvalidJson)
      case Success(_) =>
        loadJsonSchema(jsonSchema.id).flatMap {
          case GeneralPersistenceError(error) => Monad[F].pure(GeneralRegistrationError(jsonSchema.id, error))
          case SchemaAlreadyExists(id)        => Monad[F].pure(GeneralRegistrationError(id, "schema already exists"))
          case jsonSchema: JsonSchema         => Monad[F].pure(JsonSchemaAlreadyExists(jsonSchema.id))
          case SchemaNotFound(_)              =>
            persistJsonSchema(jsonSchema)
              .map {
                case GeneralPersistenceError(error) => GeneralRegistrationError(jsonSchema.id, error)
                case SchemaAlreadyExists(id)        => ConcurrentWritesError(id, "schema already exists, probably due to concurrent requests")
                case SchemaNotFound(id)             => JsonSchemaAlreadyExists(id)
                case JsonSchemaPersisted(id)        => JsonSchemaRegistered(id)
              }
        }
    }
}
