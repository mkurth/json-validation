package com.snowplow.json

import cats.Monad
import cats.data.EitherT
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
    (for {
      _                <- validateJson(jsonSchema)
      _                <- checkForIdCollision(loadJsonSchema, jsonSchema)
      schemaRegistered <- persistSchema(persistJsonSchema, jsonSchema)
    } yield schemaRegistered).value.map(_.merge)

  private def persistSchema[F[_]: Monad](persistJsonSchema: PersistJsonSchema[F], jsonSchema: JsonSchema) =
    EitherT(persistJsonSchema(jsonSchema).map {
      case GeneralPersistenceError(error) => Left(GeneralRegistrationError(jsonSchema.id, error))
      case SchemaAlreadyExists(id)        => Left(ConcurrentWritesError(id, "schema already exists, probably due to concurrent requests"))
      case SchemaNotFound(id)             => Left(JsonSchemaAlreadyExists(id))
      case JsonSchemaPersisted(id)        => Right(JsonSchemaRegistered(id))
    })

  private def checkForIdCollision[F[_]: Monad](loadJsonSchema: LoadJsonSchema[F], jsonSchema: JsonSchema) =
    EitherT(loadJsonSchema(jsonSchema.id).map {
      case SchemaNotFound(id)             => Right(id)
      case GeneralPersistenceError(error) => Left(GeneralRegistrationError(jsonSchema.id, error))
      case SchemaAlreadyExists(id)        => Left(GeneralRegistrationError(id, "schema already exists"))
      case jsonSchema: JsonSchema         => Left(JsonSchemaAlreadyExists(jsonSchema.id))
    })

  private def validateJson[F[_]: Monad](jsonSchema: JsonSchema) =
    EitherT.fromEither(Try(JsonLoader.fromString(jsonSchema.body)) match
      case Failure(_)     => Left(InvalidJson)
      case Success(value) => Right(value)
    )
}
