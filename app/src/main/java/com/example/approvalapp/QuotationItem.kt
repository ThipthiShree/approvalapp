package com.example.approvalapp



data class QuotationItem(
    val sno: Int,
    val product: String,
    val qty: Int,
    val unitprice: Double,
    val totalprice: Double
)