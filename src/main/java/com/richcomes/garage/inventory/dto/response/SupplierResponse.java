
package com.richcomes.garage.inventory.dto.response;

public class SupplierResponse {

    private Long id;
    private String supplierName;
    private String tinNumber;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private boolean active;

    public SupplierResponse() {
    }

    public SupplierResponse(
            Long id,
            String supplierName,
            String tinNumber,
            String contactPerson,
            String phone,
            String email,
            String address,
            boolean active) {

        this.id = id;
        this.supplierName = supplierName;
        this.tinNumber = tinNumber;
        this.contactPerson = contactPerson;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getTinNumber() {
        return tinNumber;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }
}
