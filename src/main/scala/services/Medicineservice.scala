package services

import models.Medicine
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import java.util.concurrent.TimeUnit

object MedicineService {
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  // Custom codec registry for Medicine case class
  val codecRegistry = fromRegistries(
    fromProviders(classOf[Medicine]),
    DEFAULT_CODEC_REGISTRY
  )
  
  val database = MongoConnection.database
  val collection: MongoCollection[Medicine] = database
    .getCollection[Medicine]("medicines")
    .withCodecRegistry(codecRegistry)

  def addMedicine(med: Medicine): Future[Unit] = {
    collection.insertOne(med).toFuture().map { _ =>
      println(s"Added: ${med.name}")
    }.recover {
      case ex => println(s"Error adding medicine: ${ex.getMessage}")
    }
  }

  def getAllMedicines(): Future[Seq[Medicine]] = {
    collection.find().toFuture()
  }

  def displayMedicines(): Unit = {
    Try {
      val medicines = collection.find().toFuture()
      medicines.onComplete {
        case Success(meds) =>
          println("Medicine Inventory:")
          meds.foreach(println)
        case Failure(ex) =>
          println(s"Error fetching medicines: ${ex.getMessage}")
      }
    }
  }

  def searchMedicine(name: String): Future[Seq[Medicine]] = {
    collection.find(regex("name", s"(?i).*$name.*")).toFuture()
  }

  def searchMedicineSync(name: String): Unit = {
    val future = searchMedicine(name)
    future.onComplete {
      case Success(found) =>
        if (found.nonEmpty) found.foreach(println)
        else println("No medicine found.")
      case Failure(ex) =>
        println(s"Error searching medicine: ${ex.getMessage}")
    }
  }

  def getCompanies(): Future[Seq[String]] = {
    collection.distinct[String]("company").toFuture()
  }

  def displayCompanies(): Unit = {
    val future = getCompanies()
    future.onComplete {
      case Success(companies) =>
        println("Companies:")
        companies.foreach(println)
      case Failure(ex) =>
        println(s"Error fetching companies: ${ex.getMessage}")
    }
  }

  def getExpiredMedicines(): Future[Seq[Medicine]] = {
    collection.find(lt("expiryDate", "2025-12-31")).toFuture()
  }

  def checkExpiryStock(): Unit = {
    val future = getExpiredMedicines()
    future.onComplete {
      case Success(expired) =>
        println("Expiry Stock Check:")
        expired.foreach(println)
      case Failure(ex) =>
        println(s"Error checking expiry stock: ${ex.getMessage}")
    }
  }

  def deleteMedicine(name: String): Future[Boolean] = {
  println(s"🧪 Attempting to delete medicine: '$name'")
  
  collection.deleteMany(equal("name", name)).toFuture().map { result =>
    val deleted = result.getDeletedCount > 0
    if (deleted) println("✅ Medicine deleted.")
    else println("⚠️ Medicine not found.")
    deleted
  }.recover {
    case ex: Throwable =>
      println(s"❌ Error deleting medicine: ${ex.getMessage}")
      ex.printStackTrace()
      false
  }
}


  def updateMedicineQuantity(name: String, newQuantity: Int): Future[Unit] = {
  collection.updateOne(
    equal("name", name),
    set("quantity", newQuantity)
  ).toFuture().map(_ => ())
}

  def findMedicineByName(name: String): Future[Option[Medicine]] = {
    collection.find(equal("name", name)).first().toFutureOption()
  }
}