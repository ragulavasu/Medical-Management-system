package models

case class Bill(
  billId: String,
  medicineName: String,
  quantity: Int,
  totalPrice: Double,
  date: String // YYYY-MM-DD
)
