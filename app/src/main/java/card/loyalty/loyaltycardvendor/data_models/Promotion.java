package card.loyalty.loyaltycardvendor.data_models;

/**
 * Created by samclough on 23/05/17.
 */

public class Promotion {

    public String title;
    public String description;
    public String expiryDate;
    public String vendorId;

    public Promotion(){}

    public Promotion(String title, String description, String expiryDate, String vendorId) {
        this.title = title;
        this.description = description;
        this.expiryDate = expiryDate;
        this.vendorId = vendorId;
    }
}
