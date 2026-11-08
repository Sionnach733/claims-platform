package com.example.claims.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "providers")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String npi;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String specialty;

    @Column(name = "in_network", nullable = false)
    private boolean inNetwork;

    protected Provider() {
        // for JPA
    }

    public Provider(String npi, String name, String specialty, boolean inNetwork) {
        this.npi = npi;
        this.name = name;
        this.specialty = specialty;
        this.inNetwork = inNetwork;
    }

    public Long getId() { return id; }
    public String getNpi() { return npi; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }
    public boolean isInNetwork() { return inNetwork; }
}
