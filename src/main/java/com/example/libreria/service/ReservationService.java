package com.example.libreria.service;

import com.example.libreria.dto.BookResponseDTO;
import com.example.libreria.dto.ReservationRequestDTO;
import com.example.libreria.dto.ReservationResponseDTO;
import com.example.libreria.dto.ReturnBookRequestDTO;
import com.example.libreria.dto.UserResponseDTO;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import com.example.libreria.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día
    
    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    
    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {
        // TODO: Implementar la creación de una reserva

        try{
            // Validar que el usuario existe
            Long userId = requestDTO.getUserId();

            UserResponseDTO user = userService.getUserById(userId);

            if(user==null){
                throw new RuntimeException("Usuario no encontrado con ID: " + userId);
            }

            // Validar que el libro existe y está disponible
            BookResponseDTO book = bookService.getBookByExternalId(requestDTO.getBookExternalId());

            if(book==null){
                throw new RuntimeException("Libro no encontrado con ID externo: " + requestDTO.getBookExternalId());
            }

            if(book.getAvailableQuantity()<=0){
                throw new RuntimeException("No hay copias disponibles del libro con ID externo: " + requestDTO.getBookExternalId());
            }

            Reservation reservation = new Reservation();
            reservation.setUser(userRepository.findById(userId).get());
            reservation.setBook(bookRepository.findByExternalId(book.getExternalId()).get());
            reservation.setRentalDays(requestDTO.getRentalDays());
            reservation.setDailyRate(book.getPrice());

            BigDecimal totalFee = calculateTotalFee(
                    reservation.getDailyRate(),
                    reservation.getRentalDays()
            );
            reservation.setTotalFee(totalFee);

            reservation.setStartDate(requestDTO.getStartDate());
            reservation.setExpectedReturnDate(
                    requestDTO.getStartDate().plusDays(requestDTO.getRentalDays())
            );

            // Crear la reserva
            Reservation res = reservationRepository.save(reservation);

            // Reducir la cantidad disponible
            bookService.decreaseAvailableQuantity(book.getExternalId());

            return modelMapper.map(res, ReservationResponseDTO.class);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));

        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }

        LocalDate returnDate = returnRequest.getReturnDate();
        if (returnDate == null) {
            returnDate = LocalDate.now();
        }
        reservation.setActualReturnDate(returnDate);

        LocalDate expectedDate = reservation.getExpectedReturnDate();
        long daysLate = 0;
        if (expectedDate != null && returnDate.isAfter(expectedDate)) {
            daysLate = ChronoUnit.DAYS.between(expectedDate, returnDate);
        }

        // 15% del PRECIO DEL LIBRO por cada día de demora
        BigDecimal lateFee = calculateLateFee(reservation.getBook().getPrice(), daysLate);
        reservation.setLateFee(lateFee);

        if (daysLate > 0) {
            reservation.setStatus(Reservation.ReservationStatus.OVERDUE);
        } else {
            reservation.setStatus(Reservation.ReservationStatus.RETURNED);
        }

        Reservation saved = reservationRepository.save(reservation);

        // Aumentar la cantidad disponible
        bookService.increaseAvailableQuantity(reservation.getBook().getExternalId());

        return modelMapper.map(saved, ReservationResponseDTO.class);
    }
    
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        return reservationRepository.findOverdueReservations().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {
        if (dailyRate == null || rentalDays == null || rentalDays <= 0) {
            return BigDecimal.ZERO;
        }

        return dailyRate.multiply(BigDecimal.valueOf(rentalDays));
    }
    
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        // 15% del precio del libro por cada día de demora
        // TODO: Implementar el cálculo de la multa por demora

        if (bookPrice == null || daysLate <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal dailyLateFee = bookPrice.multiply(new BigDecimal("0.15"));
        return dailyLateFee.multiply(BigDecimal.valueOf(daysLate));
    }
    
    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}

