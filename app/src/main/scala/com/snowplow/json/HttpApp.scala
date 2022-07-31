package com.snowplow.json

import cats.data.EitherT
import cats.effect._
import com.snowplow.json.JsonSchemaRegistry._
import com.snowplow.json.JsonSchemaValidation.{GeneralValidationError, JsonDoesNotMatchSchema, SchemaDoesNotExist}
import com.snowplow.json.JsonSchemaValidationApi._
import org.http4s.{Headers, MediaType, Response, Status}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.server.Router
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.ExecutionContext

object HttpApp extends IOApp {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  override def run(args: List[String]): IO[ExitCode] = {
    val persistJsonSchema: PersistJsonSchema[IO] = schema => EitherT.rightT(JsonSchemaPersisted(schema.id))
    val loadJsonSchema: LoadJsonSchema[IO]       = _ => EitherT.rightT(JsonSchema("", "{}"))
    (for {
      persistSchema <- Resource.make[IO, PersistJsonSchema[IO]](IO(persistJsonSchema))(_ => IO.unit)
      loadSchema    <- Resource.make[IO, LoadJsonSchema[IO]](IO(loadJsonSchema))(_ => IO.unit)
      server        <- BlazeServerBuilder[IO]
                         .withExecutionContext(ec)
                         .bindHttp(8080, "0.0.0.0")
                         .withHttpApp(
                           Router(
                             "/" -> Http4sServerInterpreter[IO].toRoutes(
                               List(
                                 schemaPost.serverLogic(schemaPostImplementation(persistSchema, loadSchema)),
                                 schemaGet.serverLogic(schemaGetImplementation(loadSchema)),
                                 validate.serverLogic(validateImplementation(loadSchema))
                               )
                             )
                           ).mapF(_.getOrElse(jsonNotFound))
                         )
                         .resource
    } yield server)
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

  private val jsonNotFound: Response[IO] =
    Response(
      Status.NotFound,
      body    = fs2.Stream.emits("""{"error": "resource not found"}""".getBytes),
      headers = Headers(`Content-Type`(MediaType.application.json) :: Nil)
    )

  private def validateImplementation(
      loadSchema: LoadJsonSchema[IO]
  ): ((String, String)) => IO[Either[ErrorApiResponse, SuccessApiResponse]] = { case (schemaId, body) =>
    JsonSchemaValidation.validateJsonSchema(loadSchema)(schemaId, body).value.map {
      case Left(JsonSchemaValidation.InvalidJson)  => Left(UnsupportedMediaTypeResponse("validateDocument", schemaId))
      case Left(JsonDoesNotMatchSchema(_, errors)) =>
        Left(BadRequestResponse("validateDocument", schemaId, errors.mkString("\n")))
      case Left(SchemaDoesNotExist(_))             => Left(NotFoundResponse("validateDocument", schemaId))
      case Left(GeneralValidationError(_, _))      => Left(InternalServerErrorResponse("validateDocument", schemaId))
      case Right(_)                                => Right(SuccessApiResponse("validateDocument", schemaId))
    }
  }

  private def schemaGetImplementation(
      loadSchema: LoadJsonSchema[IO]
  ): String => IO[Either[ErrorApiResponse, String]] = schemaId =>
    loadSchema(schemaId).value.map {
      case Left(SchemaNotFound(_)) => Left(NotFoundResponse("getSchema", schemaId))
      case Left(_)                 => Left(InternalServerErrorResponse("getSchema", schemaId))
      case Right(value)            => Right(value.body)
    }

  private def schemaPostImplementation(
      persistSchema: PersistJsonSchema[IO],
      loadSchema: LoadJsonSchema[IO]
  ): ((String, String)) => IO[Either[ErrorApiResponse, SuccessApiResponse]] = { case (schemaId, body) =>
    JsonSchemaRegistry
      .registerJsonSchema(persistSchema, loadSchema)(JsonSchema(schemaId, body))
      .value
      .map {
        case Left(JsonSchemaAlreadyExists(_))     => Left(ConflictResponse("uploadSchema", schemaId))
        case Left(GeneralRegistrationError(_, _)) => Left(InternalServerErrorResponse("uploadSchema", schemaId))
        case Left(ConcurrentWritesError(_, _))    => Left(ConflictResponse("uploadSchema", schemaId))
        case Left(InvalidJson)                    => Left(UnsupportedMediaTypeResponse("uploadSchema", schemaId))
        case Right(_)                             => Right(SuccessApiResponse("uploadSchema", schemaId))
      }
  }

}
