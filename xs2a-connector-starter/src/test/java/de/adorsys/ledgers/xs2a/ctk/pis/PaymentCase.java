package de.adorsys.ledgers.xs2a.ctk.pis;

import de.adorsys.psd2.client.model.PaymentInitiationSctJson;

public class PaymentCase {
	private String psuId;
	private PaymentInitiationSctJson payment;
	public String getPsuId() {
		return psuId;
	}
	public void setPsuId(String psuId) {
		this.psuId = psuId;
	}
	public PaymentInitiationSctJson getPayment() {
		return payment;
	}
	public void setPayment(PaymentInitiationSctJson payment) {
		this.payment = payment;
	}
}
