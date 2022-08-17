package com.snowplow.json

import cats.Monad
import cats.syntax.functor.toFunctorOps
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.snowplow.json.JsonSchemaRegistry.*
import io.circe.parser.*

import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Try
object JsonSchemaValidation {

  sealed trait ValidationError
  case object InvalidJson                                                   extends ValidationError
  final case class JsonDoesNotMatchSchema(id: String, errors: List[String]) extends ValidationError
  final case class GeneralValidationError(id: String, error: String)        extends ValidationError
  final case class SchemaDoesNotExist(id: String)                           extends ValidationError

  final case class ValidJsonSchema(id: String)

  private lazy val validator = JsonSchemaFactory.byDefault().getValidator

  def validateJsonSchema[F[_]: Monad](
      loadJsonSchema: LoadJsonSchema[F]
  )(id: String, jsonToValidate: String): F[ValidationError | ValidJsonSchema] =
    loadJsonSchema(id)
      .map {
        case GeneralPersistenceError(_) => GeneralValidationError(id, "internal error, try again later")
        case SchemaAlreadyExists(id)    => GeneralValidationError(id, "internal error")
        case SchemaNotFound(id)         => SchemaDoesNotExist(id)
        case jsonSchema: JsonSchema     =>
          (for {
            schema           <- loadSchemaAsJson(jsonSchema)
            cleaned          <- cleanEmptyKeys(jsonToValidate)
            toValidateAsJson <- loadAsJson(cleaned)
            validationResult <- validateJsonWithSchema(id, schema, toValidateAsJson)
            result           <- handleValidationResult(id, validationResult)
          } yield result) match
            case Left(value)  => value
            case Right(value) => value
      }

  private def loadSchemaAsJson(jsonSchema: JsonSchema) =
    tryOrEither(
      () => JsonLoader.fromString(jsonSchema.body),
      _ => GeneralValidationError(jsonSchema.id, "persisted schema is corrupt")
    )

  private def loadAsJson(jsonToValidate: String) =
    tryOrEither(f = () => JsonLoader.fromString(jsonToValidate), onError = _ => InvalidJson)

  private def cleanEmptyKeys(json: String) = parse(json).map(_.deepDropNullValues.toString()).left.map(_ => InvalidJson)

  private def validateJsonWithSchema(id: String, schema: JsonNode, toValidateAsJson: JsonNode) =
    tryOrEither(
      () => validator.validate(schema, toValidateAsJson),
      e => GeneralValidationError(id, e.getMessage)
    )

  private def handleValidationResult(id: String, validationResult: ProcessingReport) =
    if (validationResult.isSuccess) Right(ValidJsonSchema(id))
    else Left(JsonDoesNotMatchSchema(id, validationResult.iterator().asScala.map(_.asJson().toString).toList))

  private def tryOrEither[A](f: () => A, onError: Throwable => ValidationError): Either[ValidationError, A] =
    Try(f()).toEither.left.map(onError)
}
