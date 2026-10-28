package com.richcomes.garage.vehicle.controller;

import com.richcomes.garage.vehicle.dto.VehicleResponse;
import com.richcomes.garage.vehicle.enums.VehicleStatus;
import com.richcomes.garage.vehicle.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService vehicleService;

    @Test
    void createVehicleReturnsCreatedVehicle() throws Exception {
        when(vehicleService.createVehicle(org.mockito.ArgumentMatchers.any()))
                .thenReturn(vehicle(1L, VehicleStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType("application/json")
                        .content("""
                                {
                                  "vehicleNumber": "ABC-1234",
                                  "customerId": 10,
                                  "vehicleType": "Car",
                                  "brand": "Toyota",
                                  "model": "Corolla",
                                  "currentMileage": 25000
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.vehicleNumber").value("ABC-1234"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createVehicleRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType("application/json")
                        .content("""
                                {
                                  "vehicleNumber": "",
                                  "customerId": 0,
                                  "vehicleType": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vehicleService);
    }

    @Test
    void getAllVehiclesReturnsVehicleList() throws Exception {
        when(vehicleService.getAllVehicles())
                .thenReturn(List.of(vehicle(1L, VehicleStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].vehicleNumber").value("ABC-1234"));
    }

    @Test
    void getVehicleByIdReturnsVehicle() throws Exception {
        when(vehicleService.getVehicleById(1L))
                .thenReturn(vehicle(1L, VehicleStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/vehicles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getVehicleByNumberReturnsVehicle() throws Exception {
        when(vehicleService.getVehicleByNumber("ABC-1234"))
                .thenReturn(vehicle(1L, VehicleStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/vehicles/number/ABC-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleNumber").value("ABC-1234"));
    }

    @Test
    void getVehiclesByCustomerReturnsVehicles() throws Exception {
        when(vehicleService.getVehiclesByCustomer(10L))
                .thenReturn(List.of(vehicle(1L, VehicleStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/vehicles/customer/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(10));
    }

    @Test
    void updateVehicleReturnsUpdatedVehicle() throws Exception {
        when(vehicleService.updateVehicle(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(vehicle(1L, VehicleStatus.ACTIVE));

        mockMvc.perform(put("/api/v1/vehicles/1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "vehicleType": "Car",
                                  "brand": "Toyota",
                                  "model": "Corolla"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateVehicleRejectsBlankVehicleType() throws Exception {
        mockMvc.perform(put("/api/v1/vehicles/1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "vehicleType": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vehicleService);
    }

    @Test
    void deactivateVehicleReturnsInactiveVehicle() throws Exception {
        when(vehicleService.deactivateVehicle(1L))
                .thenReturn(vehicle(1L, VehicleStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/vehicles/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void activateVehicleReturnsActiveVehicle() throws Exception {
        when(vehicleService.activateVehicle(1L))
                .thenReturn(vehicle(1L, VehicleStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/vehicles/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(vehicleService).activateVehicle(1L);
    }

    private VehicleResponse vehicle(Long id, VehicleStatus status) {
        VehicleResponse response = new VehicleResponse();
        response.setId(id);
        response.setVehicleNumber("ABC-1234");
        response.setCustomerId(10L);
        response.setVehicleType("Car");
        response.setBrand("Toyota");
        response.setModel("Corolla");
        response.setStatus(status);
        return response;
    }
}
