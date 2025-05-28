package services

import org.mongodb.scala._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object MongoConnection {
  val client: MongoClient = MongoClient("mongodb://localhost:27017")
  val database: MongoDatabase = client.getDatabase("medicalDB")
  
  // Test connection on startup
  def testConnection(): Unit = {
    Try {
      println("Testing MongoDB connection...")
      val result = Await.result(
        database.runCommand(Document("ping" -> 1)).toFuture(),
        10.seconds
      )
      println("✓ MongoDB connection successful")
    } match {
      case Success(_) => 
        println("✓ Database 'medicalDB' is ready")
      case Failure(ex) => 
        println(s"✗ MongoDB connection failed: ${ex.getMessage}")
        println("Make sure MongoDB is running on localhost:27017")
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