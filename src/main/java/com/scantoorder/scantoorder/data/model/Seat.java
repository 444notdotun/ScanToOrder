package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String seatId;
    @ManyToOne
    @JoinColumn(name = "tableId")
    private RestaurantTable tableId;
    private String seatNumber;
    @Enumerated(EnumType.STRING)
    private SeatStatus status;
    private boolean isActive;

    public Seat (){
        isActive=true;
        status=SeatStatus.VACANT;
    }
    @PrePersist
    public void prePersist(){
        seatNumber= CodeGenerator.generate(CodePrefix.SEAT);
    }

}
