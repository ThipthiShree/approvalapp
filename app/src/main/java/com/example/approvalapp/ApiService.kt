package com.example.approvalapp

import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {

    @GET("v1/requestapproval/quotation")
    suspend fun getQuotations(): List<Quotation>

    @GET("v1/requestapproval/salesorder")
    suspend fun getSalesOrders(): List<SalesOrder>

    @DELETE("v1/requestapproval/quotation/{sno}")
    suspend fun deleteQuotation(@Path("sno") sno: String): retrofit2.Response<Unit>

    @DELETE("v1/requestapproval/salesorder/{sno}")
    suspend fun deleteSalesOrder(@Path("sno") sno: String): retrofit2.Response<Unit>
}