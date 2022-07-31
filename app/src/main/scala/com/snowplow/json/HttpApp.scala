package com.snowplow.json

import cats.data.EitherT
import cats.effect._
import com.snowplow.json.JsonSchemaRegistry._
import com.snowplow.json.JsonSchemaValidation.{GeneralValidationError, JsonDoesNotMatchSchema, SchemaDoesNotExist}
import com.snowplow.json.JsonSchemaValidationApi._
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.effect.Log.Stdout.instance
import io.circe.parser
import org.http4s.{Headers, MediaType, Response, Status}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.ExecutionContext

object HttpApp extends IOApp {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val swaggerEndpoints =
    SwaggerInterpreter().fromEndpoints[IO](List(schemaPost, schemaGet, validate), "Json Schema Validation", "1.0")

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      redis        <- Redis[IO].utf8(sys.env.getOrElse("REDIS_URL", "redis://localhost"))
      persistSchema = persistImplementation(redis)
      loadSchema    = loadImplementation(redis)
      server       <- BlazeServerBuilder[IO]
                        .withExecutionContext(ec)
                        .bindHttp(8080, "0.0.0.0")
                        .withHttpApp(
                          Logger.httpApp[IO](logHeaders = true, logBody = true)(
                            Router(
                              "/" -> Http4sServerInterpreter[IO].toRoutes(
                                List(
                                  schemaPost.serverLogic(schemaPostImplementation(persistSchema, loadSchema)),
                                  schemaGet.serverLogic(schemaGetImplementation(loadSchema)),
                                  validate.serverLogic(validateImplementation(loadSchema))
                                ) ++ swaggerEndpoints
                              )
                            ).mapF(_.getOrElse(jsonNotFound))
                          )
                        )
                        .resource
    } yield server)
      .use(_ => IO.never)
      .as(ExitCode.Success)

  private def persistImplementation(redis: RedisCommands[IO, String, String]): PersistJsonSchema[IO] = schema =>
    EitherT(
      redis
        .setNx(schema.id, schema.body)
        .map(set => if (set) Right(JsonSchemaPersisted(schema.id)) else Left(SchemaAlreadyExists(schema.id)))
    )

  private def loadImplementation(redis: RedisCommands[IO, String, String]): LoadJsonSchema[IO] = schemaId =>
    EitherT(redis.get(schemaId).map {
      case Some(schema) => Right(JsonSchema(schemaId, schema))
      case None         => Left(SchemaNotFound(schemaId))
    })

  private val jsonNotFound: Response[IO] =
    Response(
      Status.NotFound,
      body    = fs2.Stream.emits("""{"error": "resource not found"}""".getBytes),
      headers = Headers(`Content-Type`(MediaType.application.json) :: Nil)
    )

  private def validateImplementation(
      loadSchema: LoadJsonSchema[IO]
  ): ((String, String)) => IO[Either[ErrorApiResponse, SuccessApiResponse]] = { case (schemaId, body) =>
    val action = "validateDocument"
    JsonSchemaValidation.validateJsonSchema(loadSchema)(schemaId, body).value.map {
      case Left(JsonSchemaValidation.InvalidJson)  => Left(UnsupportedMediaTypeResponse(action, schemaId))
      case Left(JsonDoesNotMatchSchema(_, errors)) =>
        Left(
          BadRequestResponse(
            action,
            schemaId,
            errors.map(parser.parse).collect { case Right(value) => value }
          )
        )
      case Left(SchemaDoesNotExist(_))             => Left(NotFoundResponse(action, schemaId))
      case Left(GeneralValidationError(_, _))      => Left(InternalServerErrorResponse(action, schemaId))
      case Right(_)                                => Right(SuccessApiResponse(action, schemaId))
    }
  }

  private def schemaGetImplementation(
      loadSchema: LoadJsonSchema[IO]
  ): String => IO[Either[ErrorApiResponse, String]] = schemaId => {
    val action = "getSchema"
    loadSchema(schemaId).value.map {
      case Left(SchemaNotFound(_)) => Left(NotFoundResponse(action, schemaId))
      case Left(_)                 => Left(InternalServerErrorResponse(action, schemaId))
      case Right(value)            => Right(value.body)
    }
  }

  private def schemaPostImplementation(
      persistSchema: PersistJsonSchema[IO],
      loadSchema: LoadJsonSchema[IO]
  ): ((String, String)) => IO[Either[ErrorApiResponse, SuccessApiResponse]] = { case (schemaId, body) =>
    val action = "uploadSchema"
    JsonSchemaRegistry
      .registerJsonSchema(persistSchema, loadSchema)(JsonSchema(schemaId, body))
      .value
      .map {
        case Left(JsonSchemaAlreadyExists(_))     => Left(ConflictResponse(action, schemaId, "already exists"))
        case Left(GeneralRegistrationError(_, _)) => Left(InternalServerErrorResponse(action, schemaId))
        case Left(ConcurrentWritesError(_, _))    => Left(ConflictResponse(action, schemaId, "was created in the meantime"))
        case Left(InvalidJson)                    => Left(UnsupportedMediaTypeResponse(action, schemaId))
        case Right(_)                             => Right(SuccessApiResponse(action, schemaId))
      }
  }

}
