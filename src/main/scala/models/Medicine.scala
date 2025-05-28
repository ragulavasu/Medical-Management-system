package models

case class Medicine(
  name: String,
  company: String,
  quantity: Int,
  price: Double,
  expiryDate: String // YYYY-MM-DD
)
