package core;

/**
 * Domain model representing an academic paper in the Citation Analysis System.
 */
public class Paper {
    private String id;
    private String title;
    private String author;
    private int year;
    private int citationCount;

    public Paper() {
        this.id = "";
        this.title = "";
        this.author = "";
        this.year = 0;
        this.citationCount = 0;
    }

    public Paper(String id, String title, String author, int year) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.year = year;
        this.citationCount = 0;
    }

    public Paper(String id, String title, String author, int year, int citationCount) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.year = year;
        this.citationCount = citationCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getCitationCount() {
        return citationCount;
    }

    public void setCitationCount(int citationCount) {
        this.citationCount = citationCount;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Paper other = (Paper) obj;
        if (this.id == null) {
            return other.id == null;
        }
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Paper{id='" + id + "', title='" + title + "', author='" + author + "', year=" + year + ", citationCount=" + citationCount + "}";
    }
}
