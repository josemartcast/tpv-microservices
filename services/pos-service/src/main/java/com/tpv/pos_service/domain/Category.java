
package com.tpv.pos_service.domain;

import jakarta.persistence.*;

@Entity
@Table (name = "categories")
public class Category {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    
    @Column(nullable = false, unique = true, length = 80)
    private String name;
    
    @Column(nullable= false)
    private boolean active = true;
    
    protected Category(){}
  
    public Category (String name){
        this.name = name;
    }
    public long getId(){
        return id;
    }
    
    public String getName(){
        return name;
    }
    
    public boolean isActive(){
        return active;
    }
    
    public void rename(String name){
        this.name = name;
    }
    
    public void desactive (){
        this.active = false;
    }
    
}
