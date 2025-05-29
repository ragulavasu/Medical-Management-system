package services

import org.mongodb.scala._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object MongoConnection {
  // MongoDB connection URI and new database name
  private val mongoUri = "mongodb://localhost:27017"
  private val dbName = "medical-system-DB" // Change to your desired new DB name

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
        println(s"✓ Database '$dbName' is ready")
      case Failure(ex) =>
        println(s"✗ MongoDB connection failed: ${ex.getMessage}")
        println(s"Make sure MongoDB is running on $mongoUri")
    }
  }

  // Create collections by inserting dummy documents (MongoDB creates on first insert)
  def createInitialCollections(): Unit = {
    val dummyBill = Document("init" -> true)
    val dummyMedicine = Document("init" -> true)

    Try {
      Await.result(billsCollection.insertOne(dummyBill).toFuture(), 5.seconds)
      Await.result(medicinesCollection.insertOne(dummyMedicine).toFuture(), 5.seconds)
      println("✓ Collections 'bills' and 'medicines' created (if not already present).")
    } recover {
      case ex: Throwable =>
        println(s"⚠️ Failed to create collections: ${ex.getMessage}")
    }
  }

  // Initialize connection test and create collections
  testConnection()
  createInitialCollections()

  // Graceful shutdown
  def close(): Unit = {
    client.close()
    println("MongoDB connection closed")
  }
}
