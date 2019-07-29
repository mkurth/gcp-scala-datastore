`gcp-scala-datastore` is a simple wrapper library for [Google Cloud Datastore](http://googlecloudplatform.github.io/google-cloud-java/). 


The library provides asynchronous API and supports the following types of fields in model classes:
* `Byte`
* `Int`
* `Long`
* `Float`
* `Double`
* `String`
* `Boolean`
* `java.util.Date`
* `com.google.cloud.datastore.DateTime`
* `com.google.cloud.datastore.LatLng`
* `com.google.cloud.datastore.Blob`
* `java.time.LocalDateTime`*
* `java.time.ZonedDateTime`*
* `java.time.OffsetDateTime`*
* `scala.Option[T]` where `T` is one of types listed above
* any other type that inherits `scala.Serializable`. In this case, such fields are converted into Blob in Google Cloud Console.


`*` `java.time.*` classes are converted into String at the moment. Thus, they do not work with comparison operators, i.e. `|<|`, `|>|`, `|==|` etc.

### Usage of the `gcp-scala-datastore`
To be stored in Cloud Datastore a model class must inherit `io.applicative.datastore.BaseEntity` and must have `id` field of type `Long` or `String`.
```
import io.applicative.datastore._
import io.applicative.datastore.util.reflection._
import io.applicative.datastore.query._
import cats.effect.IO
import scala.concurrent.Future

// Sample model class
case class Item(id: Long, name: String, price: Double, size: Int, brand: Option[String]) extends BaseEntity

val item = Item(1, "foo", 20.0, 2, None)

implicit val ds: DatastoreService[IO] = DatastoreService.default
implicit val dsf: DatastoreService[Future] = DatastoreService.defaultFuture
// Save
DatastoreService.default.add[Item](item)
// Save as Future
DatastoreService.defaultFuture.add[Item](item)

// Save with autogenerated id
for {
  key <- DatastoreService.default.newKey[Item]()
  user <- DatastoreService.default.add[Item](Item(key.id.get, "foo", 20.0, 2, None))
} yield user

// Update
DatastoreService.default.update[Item](item.copy(brand = Some("bar")))

// Delete
DatastoreService.default.delete[Item](List(1L))

// Get one by id
DatastoreService.default.get[Item](1)
// or
select[Item, IO] asSingle
// or as Future
select[Item, Future] asSingle

// Select
val items: IO[List[Item]] = select[Item, IO] where "size" |>| 23 and "price" |<=| 200.2 ascOrderBy "size" descOrderBy "price" asList
val futureItems: Future[List[Item]] = select[Item, Future] where "size" |>| 23 and "price" |<=| 200.2 ascOrderBy "size" descOrderBy "price" asList
val items2: IO[List[Item]] = select[Item, IO]
  .where("size" |>| 23)
  .and("price" |<| 200.2)
  .ascOrderBy("size")
  .descOrderBy("price")
  .asList
val singleItem: IO[Option[Item]] = select[Item, IO] where "name" |==| "foo" asSingle
```

### Indexes
By default, Cloud Datastore automatically predefines an index for each property of each entity kind(see https://cloud.google.com/datastore/docs/concepts/indexes for more details). <br>
If you want to exclude any of your properties from the indexes, just add the annotation `@excludeFromIndexes`.

```
import io.applicative.datastore.BaseEntity
import io.applicative.datastore.util.reflection.excludeFromIndexes

case class Item(id: Long, name: String, price: Double, size: Int, brand: Option[String], @excludeFromIndexes description: String) extends BaseEntity
```

### Custom Datastore Kind
By default, each model class has kind that consists of class' package and class' name. Let's consider a simple case class `Foo`
```
package com.foo.bar

import io.applicative.datastore.BaseEntity

case class Foo(id: Long) extends BaseEntity
```
In this case, the kind for class `Foo` will be `com.foo.bar.Foo`.

It is allowed to set up a custom kind by annotating a class with `io.applicative.datastore.reflection.Kind` annotation:

```
package com.foo.bar

import io.applicative.datastore.BaseEntity
import io.applicative.datastore.reflection.Kind

@Kind(value = "JustFoo")
case class Foo(id: Long) extends BaseEntity
```
In this case, the kind for the class `Foo` will be `JustFoo`

### Installation using sbt

In order to install this package you will need to add this to your `build.sbt`:

```
lazy val datastore4s   = RootProject(uri("git://github.com/mkurth/gcp-scala-datastore.git"))
dependsOn(datastore4s)
```

