# Snowplow Challenge

## How to run

you need:
* sbt
* docker
* docker-compose

```bash
sbt "project app" docker:publishLocal

docker-compose up
```

or ```./run.sh```

[find the API docs here](http://localhost:8080/docs)

## How to run tests

```sbt test```

# Design

I tried to split the actual logic and the way this logic is invoked into different projects. Inside the domain folder 
you will find what I considered the domain logic, which tries to be as free of frameworks and libraries as possible, 
following the clean architecture approach.

The actual http and database layer are implemented inside the app project.

## Possible further improvements

* the JSON validation is done in the domain project and should be abstracted and injected like the DB parts, though 
probably overkill for just these few invocations
* HttpApp.scala is a bit messy and has too many responsibilities. once more features are needed it should be split down,
so that we end up with the HTTP definition, initializing all the needed resources and the wiring between the logic and 
HTTP definition
* Redis was chosen because it's a simple key-value store that can be run as a cluster. but without more details on the
requirement it's hard to say what storage would be better
* the schemas are read on each validation request. given that they can't be overridden at the moment, it would be better 
to cache them locally and only load them once on demand. but again depends on the requirements
* currently I share the PersistenceError for reading and writing which leads to this weird error that can happen 
code-wise, that the load will return a SchemaAlreadyExists. this should be split up, so that reading and writing have 
their own error return types.
* no metrics or internal logging is implemented
* I would move `/schema` to `/schemas` and `/validate` to `/validations` to make it clearer, we're handling resources 
here and not use cases
* JsonSchemaRegistry looks a bit messy with the Try and the staircased EitherT
* no real integration tests
* no separate types for schemaId and the incoming data, should be more typesafe, but I didn't want to spend too much 
time on that 