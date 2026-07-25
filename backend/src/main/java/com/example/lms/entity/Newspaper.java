package com.example.lms.entity;
import jakarta.persistence.*; import java.time.LocalDate;
@Entity @Table(name="newspapers")
public class Newspaper extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=200) private String title;
    @Column(length=200) private String publisher;
    @Column(name="publication_date") private LocalDate publicationDate;
    @Column(length=500) private String topHeadlines;
    @Column(nullable=false) private boolean available=true;

    public Long getId(){return id;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getPublisher(){return publisher;} public void setPublisher(String v){publisher=v;}
    public LocalDate getPublicationDate(){return publicationDate;} public void setPublicationDate(LocalDate v){publicationDate=v;}
    public String getTopHeadlines(){return topHeadlines;} public void setTopHeadlines(String v){topHeadlines=v;}
    public boolean isAvailable(){return available;} public void setAvailable(boolean v){available=v;}
}
