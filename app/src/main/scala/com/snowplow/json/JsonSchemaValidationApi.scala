package com.snowplow.json

import io.circe.generic.auto._
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

final case class SuccessApiResponse(action: String, id: String, status: String = "success")

sealed trait ErrorApiResponse
final case class BadRequestResponse(action: String, id: String, message: String, status: String = "error") extends ErrorApiResponse
final case class InternalServerErrorResponse(action: String, id: String, status: String = "error")         extends ErrorApiResponse
final case class NotFoundResponse(action: String, id: String, status: String = "error")                    extends ErrorApiResponse
final case class UnsupportedMediaTypeResponse(action: String, id: String, status: String = "error")        extends ErrorApiResponse
final case class ConflictResponse(action: String, id: String, message: String, status: String = "error")   extends ErrorApiResponse

object JsonSchemaValidationApi {

  val schemaPost: Endpoint[Unit, (String, String), ErrorApiResponse, SuccessApiResponse, Any] =
    endpoint.post
      .in("schema")
      .in(path[String]("schemaId"))
      .in(stringBody)
      .out(statusCode(StatusCode.Created).and(jsonBody[SuccessApiResponse]))
      .errorOut(
        oneOf[ErrorApiResponse](
          oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[InternalServerErrorResponse])),
          oneOfVariant(statusCode(StatusCode.Conflict).and(jsonBody[ConflictResponse])),
          oneOfVariant(statusCode(StatusCode.UnsupportedMediaType).and(jsonBody[UnsupportedMediaTypeResponse]))
        )
      )

  val schemaGet: Endpoint[Unit, String, ErrorApiResponse, String, Any] =
    endpoint.get
      .in("schema")
      .in(path[String]("schemaId"))
      .out(stringBody.and(header(Header.contentType(MediaType.ApplicationJson))))
      .errorOut(
        oneOf[ErrorApiResponse](
          oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFoundResponse])),
          oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[InternalServerErrorResponse]))
        )
      )

  val validate: Endpoint[Unit, (String, String), ErrorApiResponse, SuccessApiResponse, Any] =
    endpoint.post
      .in("validate")
      .in(path[String]("schemaId"))
      .in(stringBody)
      .out(jsonBody[SuccessApiResponse])
      .errorOut(
        oneOf[ErrorApiResponse](
          oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[InternalServerErrorResponse])),
          oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFoundResponse])),
          oneOfVariant(statusCode(StatusCode.UnsupportedMediaType).and(jsonBody[UnsupportedMediaTypeResponse]))
        )
      )

}
