error id: file:///C:/Users/vasur/OneDrive/Desktop/BDA/src/main/scala/services/MongoConnection.scala:`<none>`.
file:///C:/Users/vasur/OneDrive/Desktop/BDA/src/main/scala/services/MongoConnection.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1073
uri: file:///C:/Users/vasur/OneDrive/Desktop/BDA/src/main/scala/services/MongoConnection.scala
text:
```scala
package services

import org.mongodb.scala._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object MongoConnection {
  // MongoDB connection URI and database name
  private val mongoUri = "mongodb://localhost:27017"
  private val dbName = "medicalDB"

  // MongoDB client and database
  val client: MongoClient = MongoClient(mongoUri)
  val database: MongoDatabase = client.getDatabase(dbName)

  // Collections
  val billsCollection: MongoCollection[Document] = database.getCollection("bills")
  val medicinesCollection: MongoCollection[Document] = database.getCollection("medicines")

  // Test connection on startup
  def testConnection(): Unit = {
    Try {
      println(s"Testing MongoDB connection to '$dbName'...")
      val result = Await.result(
        database.runCommand(Document("ping" -> 1)).toFuture(),
        10.seconds
      )
      println("✓ MongoDB connection successful")
    } match {
      case Success(_) =>
        println(s"✓ Database '$dbName' is ready@@")
      case Failure(ex) =>
        println(s"✗ MongoDB connection failed: ${ex.getMessage}")
        println(s"Make sure MongoDB is running on $mongoUri")
    }
  }

  // Initialize connection test
  testConnection()

  // Graceful shutdown
  def close(): Unit = {
    client.close()
    println("MongoDB connection closed")
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.