package com.snowplow.json

import io.circe.Json
import io.circe.generic.auto._
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

final case class SuccessApiResponse(action: String, id: String, status: String = "success")

sealed trait ErrorApiResponse
final case class BadRequestResponse(action: String, id: String, message: List[Json], status: String = "error") extends ErrorApiResponse
final case class InternalServerErrorResponse(action: String, id: String, status: String = "error")             extends ErrorApiResponse
final case class NotFoundResponse(action: String, id: String, status: String = "error")                        extends ErrorApiResponse
final case class UnsupportedMediaTypeResponse(action: String, id: String, status: String = "error")            extends ErrorApiResponse
final case class ConflictResponse(action: String, id: String, message: String, status: String = "error")       extends ErrorApiResponse

object JsonSchemaValidationApi {

  val schemaPost: Endpoint[Unit, (String, String), ErrorApiResponse, SuccessApiResponse, Any] =
    endpoint.post
      .in("schema")
      .in(path[String]("schemaId"))
      .in(stringBody)
      .out(statusCode(StatusCode.Created).and(jsonBody[SuccessApiResponse]))
      .errorOut(
        oneOf[ErrorApiResponse](
          oneOfVariant(
            statusCode(StatusCode.InternalServerError)
              .and(jsonBody[InternalServerErrorResponse].example(InternalServerErrorResponse("uploadSchema", "1")))
          ),
          oneOfVariant(
            statusCode(StatusCode.Conflict)
              .and(
                jsonBody[ConflictResponse]
                  .example(ConflictResponse("uploadSchema", "1", "was created in the meantime"))
                  .description("this happens if a schema with the same id was already stored")
              )
          ),
          oneOfVariant(
            statusCode(StatusCode.UnsupportedMediaType)
              .and(jsonBody[UnsupportedMediaTypeResponse].example(UnsupportedMediaTypeResponse("uploadSchema", "1")))
          )
        )
      )

  val schemaGet: Endpoint[Unit, String, ErrorApiResponse, String, Any] =
    endpoint.get
      .in("schema")
      .in(path[String]("schemaId"))
      .out(stringBody.and(header(Header.contentType(MediaType.ApplicationJson))))
      .errorOut(
        oneOf[ErrorApiResponse](
          oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFoundResponse].example(NotFoundResponse("getSchema", "1")))),
          oneOfVariant(
            statusCode(StatusCode.InternalServerError)
              .and(jsonBody[InternalServerErrorResponse].example(InternalServerErrorResponse("getSchema", "1")))
          )
        )
      )

  val validate: Endpoint[Unit, (String, String), ErrorApiResponse, SuccessApiResponse, Any] =
    endpoint.post
      .in("validate")
      .in(path[String]("schemaId"))
      .in(stringBody)
      .description("returns a 200 in case the provided json matches the schema")
      .out(jsonBody[SuccessApiResponse].example(SuccessApiResponse("validate", "1")))
      .errorOut(
        oneOf[ErrorApiResponse](
          oneOfVariant(
            statusCode(StatusCode.InternalServerError)
              .and(jsonBody[InternalServerErrorResponse].example(InternalServerErrorResponse("validate", "1")))
          ),
          oneOfVariant(
            statusCode(StatusCode.NotFound).and(
              jsonBody[NotFoundResponse]
                .example(NotFoundResponse("validate", "1"))
                .description("no schema was found with this id. try uploading it first")
            )
          ),
          oneOfVariant(
            statusCode(StatusCode.UnsupportedMediaType)
              .and(jsonBody[UnsupportedMediaTypeResponse].example(UnsupportedMediaTypeResponse("validate", "1")))
          ),
          oneOfVariant(
            statusCode(StatusCode.BadRequest)
              .and(
                jsonBody[BadRequestResponse]
                  .example(
                    BadRequestResponse(
                      "validate",
                      "1",
                      List(
                        io.circe.parser
                          .parse(
                            """{
                              |"level":"error",
                              |"schema":{"loadingURI":"#","pointer":"/properties/chunks"},
                              |"instance":{"pointer":"/chunks"},
                              |"domain":"validation",
                              |"keyword":"required",
                              |"message":"object has missing required properties ([\"size\"])",
                              |"required":["size"],
                              |"missing":["size"]}""".stripMargin
                          )
                          .getOrElse(Json.fromLong(1))
                      )
                    )
                  )
              )
          )
        )
      )

}
