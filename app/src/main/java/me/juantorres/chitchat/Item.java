package me.juantorres.chitchat;

public class Item {

    private String title;
    private String author;
    private int votes;
    private String id;


    public Item() {
    }

    public Item(String id, String author, String title) {
        this.title = title;
        this.author = author;
        this.votes = 0;
        this.id = id;
    }

    public Item(String id, String author, String title, int votes) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.votes = votes;
    }

    public String getTitle() {
        return title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setVotes(int count) {
        this.votes = count;
    }

    public int getVotes() {
        return votes;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int upVote() {
        return ++this.votes;
    }

    public int downVote() {
        return --this.votes;
    }

}
