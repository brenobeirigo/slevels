package input;

public class ServiceRate {

    private String id;
    private Double rate;

    public ServiceRate(String id, Double rate) {
        this.id = id;
        this.rate = rate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
