package core;

/**
 * Domain model representing a directed citation relationship from a citing paper to a cited paper.
 */
public class Citation {
    private String citingPaperId;
    private String citedPaperId;

    public Citation() {
        this.citingPaperId = "";
        this.citedPaperId = "";
    }

    public Citation(String citingPaperId, String citedPaperId) {
        this.citingPaperId = citingPaperId;
        this.citedPaperId = citedPaperId;
    }

    public String getCitingPaperId() {
        return citingPaperId;
    }

    public void setCitingPaperId(String citingPaperId) {
        this.citingPaperId = citingPaperId;
    }

    public String getCitedPaperId() {
        return citedPaperId;
    }

    public void setCitedPaperId(String citedPaperId) {
        this.citedPaperId = citedPaperId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Citation other = (Citation) obj;
        boolean citingEqual = (this.citingPaperId == null) ? (other.citingPaperId == null) : this.citingPaperId.equals(other.citingPaperId);
        boolean citedEqual = (this.citedPaperId == null) ? (other.citedPaperId == null) : this.citedPaperId.equals(other.citedPaperId);
        return citingEqual && citedEqual;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + (citingPaperId != null ? citingPaperId.hashCode() : 0);
        hash = 31 * hash + (citedPaperId != null ? citedPaperId.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "Citation{citingPaperId='" + citingPaperId + "', citedPaperId='" + citedPaperId + "'}";
    }
}
