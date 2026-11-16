package com.scantoorder.scantoorder.dtos.respond;

/**
 * How one payment is shared: the restaurant's subaccount, Plately's flat charge in kobo,
 * and who bears Paystack's own fee ("account" or "subaccount").
 */
public record PaystackSplit(String subaccountCode, long platelyChargeKobo, String bearer) {
}
