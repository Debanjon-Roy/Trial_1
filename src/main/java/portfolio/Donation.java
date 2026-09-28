package portfolio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a bKash donation / transaction.
 */
public class Donation {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private int id;
    private String paymentId;
    private String trxId;
    private double amount;
    private String currency;
    private String donorName;
    private String customerMsisdn;
    private String status; // Initiated, Completed, Failed, Cancelled
    private LocalDateTime createdAt;

    public Donation(int id, String paymentId, String trxId, double amount, String currency,
                    String donorName, String customerMsisdn, String status, LocalDateTime createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.trxId = trxId;
        this.amount = amount;
        this.currency = currency == null || currency.isBlank() ? "BDT" : currency;
        this.donorName = donorName == null || donorName.isBlank() ? "Anonymous" : donorName;
        this.customerMsisdn = customerMsisdn == null ? "" : customerMsisdn;
        this.status = status == null ? "Initiated" : status;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public Donation(String paymentId, String trxId, double amount, String currency,
                    String donorName, String customerMsisdn, String status, LocalDateTime createdAt) {
        this(0, paymentId, trxId, amount, currency, donorName, customerMsisdn, status, createdAt);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getTrxId() {
        return trxId;
    }

    public void setTrxId(String trxId) {
        this.trxId = trxId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDonorName() {
        return donorName;
    }

    public void setDonorName(String donorName) {
        this.donorName = donorName;
    }

    public String getCustomerMsisdn() {
        return customerMsisdn;
    }

    public void setCustomerMsisdn(String customerMsisdn) {
        this.customerMsisdn = customerMsisdn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormattedDate() {
        return createdAt != null ? createdAt.format(FORMATTER) : "";
    }
}
