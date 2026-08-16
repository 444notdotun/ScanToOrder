package com.scantoorder.scantoorder.data.model;

import com.scantoorder.scantoorder.utils.CodeGenerator;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String tableId;
    @Column(unique = true)
    private String tableNumber;
    private  int Capacity;
    @Enumerated(EnumType.STRING)
    private TableStatus status;
    private boolean isActive;



    @PrePersist
    public void prePersist(){
         isActive=true;
         status=TableStatus.AVAILABLE;
         tableNumber= CodeGenerator.generate(CodePrefix.TABLE);
     }
}
