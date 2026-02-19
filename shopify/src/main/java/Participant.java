/**
 * Represents a participant in the holiday gift exchange
 */
public class Participant {
    private String name;
    private String email;
    
    public Participant(String name, String email) {
        this.name = name;
        this.email = email;
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
    
    @Override
    public String toString() {
        return name + " (" + email + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Participant that = (Participant) obj;
        return name.equals(that.name) && email.equals(that.email);
    }
    
    @Override
    public int hashCode() {
        return name.hashCode() + email.hashCode();
    }
}
