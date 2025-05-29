import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods._
import scala.io.StdIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.{Success, Failure}
import java.util.UUID
import scala.concurrent.Future

// Import models and services
import models.{Medicine, Bill}
import services.{MedicineService, BillService}

// Case classes for API compatibility
final case class ApiMedicine(name: String, company: String, quantity: Int, price: Double, expiryDate: String)
final case class ApiBill(medicines: List[ApiBillMedicine], total: Double, date: String, customerName: String)
final case class ApiBillMedicine(medicineName: String, quantity: Int, unitPrice: Double)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val apiMedicineFormat: RootJsonFormat[ApiMedicine] = jsonFormat5(ApiMedicine)
  implicit val apiMedicinesFormat: RootJsonFormat[List[ApiMedicine]] = listFormat[ApiMedicine]
  implicit val apiBillMedicineFormat: RootJsonFormat[ApiBillMedicine] = jsonFormat3(ApiBillMedicine)
  implicit val apiBillFormat: RootJsonFormat[ApiBill] = jsonFormat4(ApiBill)
  implicit val apiBillsFormat: RootJsonFormat[List[ApiBill]] = listFormat[ApiBill]
  implicit val stringListFormat: RootJsonFormat[List[String]] = listFormat[String]
}

object Main extends JsonSupport {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("medical-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    // CORS headers for browser compatibility
    val corsHeaders = List(
      `Access-Control-Allow-Origin`.*,
      `Access-Control-Allow-Methods`(GET, POST, PUT, DELETE, OPTIONS),
      `Access-Control-Allow-Headers`("Content-Type", "Authorization")
    )

    def medicineToApi(medicine: Medicine): ApiMedicine = {
      ApiMedicine(medicine.name, medicine.company, medicine.quantity, medicine.price, medicine.expiryDate)
    }

    def billToApi(bill: Bill): ApiBill = {
      ApiBill(
        bill.medicines.map(m => ApiBillMedicine(m.medicineName, m.quantity, m.unitPrice)),
        bill.total,      // <-- use .total, not .totalPrice
        bill.date,
        bill.customerName
      )
    }

    val route: Route =
      respondWithHeaders(corsHeaders) {
        concat(
          options {
            complete(StatusCodes.OK)
          },
          pathPrefix("medicines") {
            concat(
              get {
                parameters('name.?, 'expiryDate.?) {
                  case (Some(name), _) =>
                    onComplete(MedicineService.searchMedicine(name)) {
                      case Success(medicines) =>
                        complete(medicines.map(medicineToApi).toList)
                      case Failure(ex) =>
                        complete(StatusCodes.InternalServerError, s"Error: ${ex.getMessage}")
                    }
                  case (None, Some(_)) =>
                    onComplete(MedicineService.getExpiredMedicines()) {
                      case Success(medicines) =>
                        complete(medicines.map(medicineToApi).toList)
                      case Failure(ex) =>
                        complete(StatusCodes.InternalServerError, s"Error: ${ex.getMessage}")
                    }
                  case (None, None) =>
                    onComplete(MedicineService.getAllMedicines()) {
                      case Success(medicines) =>
                        complete(medicines.map(medicineToApi).toList)
                      case Failure(ex) =>
                        complete(StatusCodes.InternalServerError, s"Error: ${ex.getMessage}")
                    }
                }
              } ~
              post {
                entity(as[ApiMedicine]) { apiMed =>
                  try {
                    LocalDate.parse(apiMed.expiryDate)
                    val medicine = Medicine(apiMed.name, apiMed.company, apiMed.quantity, apiMed.price, apiMed.expiryDate)
                    onComplete(MedicineService.addMedicine(medicine)) {
                      case Success(_) =>
                        complete(StatusCodes.Created, apiMed)
                      case Failure(ex) =>
                        complete(StatusCodes.InternalServerError, s"Error: ${ex.getMessage}")
                    }
                  } catch {
                    case _: Exception =>
                      complete(StatusCodes.BadRequest, "Invalid expiry date format. Use YYYY-MM-DD")
                  }
                }
              } ~
              delete {
                path(Segment) { medicineName =>
                  onComplete(MedicineService.deleteMedicine(medicineName)) {
                    case Success(true) =>
                      complete(StatusCodes.OK, s"Medicine '$medicineName' deleted successfully")
                    case Success(false) =>
                      complete(StatusCodes.NotFound, s"Medicine '$medicineName' not found")
                    case Failure(ex) =>
                      complete(StatusCodes.InternalServerError, s"Error deleting medicine: ${ex.getMessage}")
                  }
                }
              }
            )
          } ~
          pathPrefix("bills") {
            concat(
              get {
                onComplete(BillService.getAllBills()) {
                  case Success(bills) =>
                    complete(bills.map(billToApi).toList)
                  case Failure(ex) =>
                    complete(StatusCodes.InternalServerError, JsObject("error" -> JsString(ex.getMessage)))
                }
              } ~
              post {
                entity(as[ApiBill]) { apiBill =>
                  // For each medicine, check stock and update
                  val medicineUpdates = apiBill.medicines.map { med =>
                    MedicineService.findMedicineByName(med.medicineName).flatMap {
                      case Some(medicine) if medicine.quantity >= med.quantity =>
                        MedicineService.updateMedicineQuantity(med.medicineName, medicine.quantity - med.quantity)
                      case Some(_) =>
                        Future.failed(new Exception(s"Insufficient stock for ${med.medicineName}"))
                      case None =>
                        Future.failed(new Exception(s"Medicine not found: ${med.medicineName}"))
                    }
                  }
                  val allUpdates = Future.sequence(medicineUpdates)
                  onComplete(allUpdates) {
                    case Success(_) =>
                      val bill = Bill(
                        billId = UUID.randomUUID().toString,
                        medicines = apiBill.medicines.map(m => models.BillMedicine(m.medicineName, m.quantity, m.unitPrice)),
                        total = apiBill.total,           // <-- use total, not totalPrice
                        date = apiBill.date,
                        customerName = apiBill.customerName
                      )
                      onComplete(BillService.addBill(bill)) {
                        case Success(_) =>
                          complete(StatusCodes.Created, billToApi(bill))
                        case Failure(ex) =>
                          complete(StatusCodes.InternalServerError, JsObject("error" -> JsString(ex.getMessage)))
                      }
                    case Failure(ex) =>
                      complete(StatusCodes.BadRequest, JsObject("error" -> JsString(ex.getMessage)))
                  }
                }
              }
            )
          } ~
          pathPrefix("companies") {
            get {
              onComplete(MedicineService.getCompanies()) {
                case Success(companies) =>
                  complete(companies.toList.sorted)
                case Failure(ex) =>
                  complete(StatusCodes.InternalServerError, s"Error: ${ex.getMessage}")
              }
            }
          } ~
          pathPrefix("public") {
            getFromDirectory("public")
          } ~
          pathEndOrSingleSlash {
            getFromFile("public/index.html")
          } ~
          path("health") {
            complete("Server is running with MongoDB")
          }
        )
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server online at http://localhost:8080/")
    println("CORS enabled for browser access")
    println("MongoDB integration enabled")
    println("Press RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}