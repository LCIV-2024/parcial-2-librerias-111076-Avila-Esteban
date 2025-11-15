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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    
    @Mock
    private ReservationRepository reservationRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private BookService bookService;
    
    @Mock
    private UserService userService;

    @Mock
    private ModelMapper modelMapper;
    
    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private UserRepository userRepository;   // 👈 agregalo al test
    
    private User testUser;
    private Book testBook;
    private Reservation testReservation;
    
    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setName("Juan Pérez");
        testUser.setEmail("juan@example.com");
        
        testBook = new Book();
        testBook.setExternalId(258027L);
        testBook.setTitle("The Lord of the Rings");
        testBook.setPrice(new BigDecimal("15.99"));
        testBook.setStockQuantity(10);
        testBook.setAvailableQuantity(5);
        
        testReservation = new Reservation();
        testReservation.setId(1L);
        testReservation.setUser(testUser);
        testReservation.setBook(testBook);
        testReservation.setRentalDays(7);
        testReservation.setStartDate(LocalDate.now());
        testReservation.setExpectedReturnDate(LocalDate.now().plusDays(7));
        testReservation.setDailyRate(new BigDecimal("15.99"));
        testReservation.setTotalFee(new BigDecimal("111.93"));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setCreatedAt(LocalDateTime.now());
    }
    
    @Test
    void testCreateReservation_Success() {
        // TODO: Implementar el test de creación de reserva exitosa

        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(testUser.getId());                     // 1L
        requestDTO.setBookExternalId(testBook.getExternalId());     // 258027L
        requestDTO.setRentalDays(2);
        requestDTO.setStartDate(LocalDate.now());

        // ---- mock UserService: el usuario EXISTE ----
        UserResponseDTO userDto = new UserResponseDTO();
        userDto.setId(testUser.getId());
        userDto.setName(testUser.getName());
        userDto.setEmail(testUser.getEmail());
        when(userService.getUserById(testUser.getId()))
                .thenReturn(userDto);

        // ---- mock BookService: el libro EXISTE y tiene stock ----
        BookResponseDTO bookDto = new BookResponseDTO();
        bookDto.setExternalId(testBook.getExternalId());
        bookDto.setTitle(testBook.getTitle());
        bookDto.setPrice(testBook.getPrice());
        bookDto.setAvailableQuantity(5); // > 0 para que pase la validación
        when(bookService.getBookByExternalId(testBook.getExternalId()))
                .thenReturn(bookDto);

        // ---- mock repositorios: devolver entidades reales ----
        when(userRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testUser));

        when(bookRepository.findByExternalId(testBook.getExternalId()))
                .thenReturn(Optional.of(testBook));

        // ---- mock save de Reservation ----
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation r = invocation.getArgument(0);
                    // simulamos que la BD le asigna ID
                    r.setId(1L);
                    return r;
                });

        // ---- mock ModelMapper -> ReservationResponseDTO ----
        when(modelMapper.map(any(Reservation.class), eq(ReservationResponseDTO.class)))
                .thenAnswer(invocation -> {
                    Reservation r = invocation.getArgument(0);
                    ReservationResponseDTO dto = new ReservationResponseDTO();
                    dto.setId(r.getId());
                    dto.setUserId(r.getUser().getId());
                    dto.setBookExternalId(r.getBook().getExternalId());
                    dto.setRentalDays(r.getRentalDays());
                    dto.setStartDate(r.getStartDate());
                    dto.setExpectedReturnDate(r.getExpectedReturnDate());
                    dto.setDailyRate(r.getDailyRate());
                    dto.setTotalFee(r.getTotalFee());
                    dto.setStatus(r.getStatus());
                    return dto;
                });

        // Act
        ReservationResponseDTO result = reservationService.createReservation(requestDTO);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(testUser.getId(), result.getUserId());
        assertEquals(testBook.getExternalId(), result.getBookExternalId());
        assertEquals(2, result.getRentalDays());
        assertEquals(testBook.getPrice(), result.getDailyRate());

        // opcional: verificar interacciones
        verify(bookService).decreaseAvailableQuantity(testBook.getExternalId());
        verify(reservationRepository).save(any(Reservation.class));

    }

    @Test
    void testCreateReservation_BookNotAvailable() {
        // Arrange
        ReservationRequestDTO requestDTO = new ReservationRequestDTO();
        requestDTO.setUserId(1L);
        requestDTO.setBookExternalId(258027L);
        requestDTO.setRentalDays(2);
        requestDTO.setStartDate(LocalDate.now());

        // Usuario EXISTE (solo importa que no sea null)
        UserResponseDTO userDto = mock(UserResponseDTO.class);
        lenient().when(userService.getUserById(1L)).thenReturn(userDto);

        // Libro EXISTE pero sin copias disponibles
        BookResponseDTO bookDto = mock(BookResponseDTO.class);
        lenient().when(bookDto.getExternalId()).thenReturn(258027L);
        lenient().when(bookDto.getAvailableQuantity()).thenReturn(0); // 👈 sin stock
        lenient().when(bookService.getBookByExternalId(258027L)).thenReturn(bookDto);

        // Act
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reservationService.createReservation(requestDTO));

        // Assert
        assertTrue(
                ex.getMessage().contains("No hay copias disponibles del libro con ID externo"),
                "El mensaje debe indicar que no hay copias disponibles"
        );

        // No debe guardar nada ni decrementar stock
        verify(reservationRepository, never()).save(any());
        verify(bookService, never()).decreaseAvailableQuantity(anyLong());
    }

    @Test
    void testReturnBook_OnTime() {
        // Arrange: la reserva vence hoy y está activa
        testReservation.setExpectedReturnDate(LocalDate.now());
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        testReservation.setLateFee(BigDecimal.ZERO);

        // Mock: encuentra la reserva
        when(reservationRepository.findById(1L))
                .thenReturn(Optional.of(testReservation));

        // Mock: save devuelve la misma reserva
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock ModelMapper: convertir Reservation -> ReservationResponseDTO
        when(modelMapper.map(any(Reservation.class), eq(ReservationResponseDTO.class)))
                .thenAnswer(invocation -> {
                    Reservation r = invocation.getArgument(0);
                    ReservationResponseDTO dto = new ReservationResponseDTO();
                    dto.setId(r.getId());
                    dto.setStatus(r.getStatus());
                    dto.setLateFee(r.getLateFee());
                    dto.setActualReturnDate(r.getActualReturnDate());
                    return dto;
                });

        // Request de devolución en fecha
        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now());

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.RETURNED, result.getStatus());
        assertEquals(LocalDate.now(), result.getActualReturnDate());
        assertEquals(0, result.getLateFee().compareTo(BigDecimal.ZERO));

        // Debe haberse incrementado la cantidad disponible del libro
        verify(bookService).increaseAvailableQuantity(testBook.getExternalId());
    }

    @Test
    void testReturnBook_Overdue() {
        // Arrange: reserva venció hace 3 días
        testBook.setPrice(new BigDecimal("100.00")); // para que el cálculo sea sencillo
        testReservation.setExpectedReturnDate(LocalDate.now().minusDays(3));
        testReservation.setStatus(Reservation.ReservationStatus.ACTIVE);

        when(reservationRepository.findById(1L))
                .thenReturn(Optional.of(testReservation));

        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(modelMapper.map(any(Reservation.class), eq(ReservationResponseDTO.class)))
                .thenAnswer(invocation -> {
                    Reservation r = invocation.getArgument(0);
                    ReservationResponseDTO dto = new ReservationResponseDTO();
                    dto.setId(r.getId());
                    dto.setStatus(r.getStatus());
                    dto.setLateFee(r.getLateFee());
                    dto.setActualReturnDate(r.getActualReturnDate());
                    return dto;
                });

        ReturnBookRequestDTO returnRequest = new ReturnBookRequestDTO();
        returnRequest.setReturnDate(LocalDate.now()); // devuelve hoy (3 días tarde)

        // Act
        ReservationResponseDTO result = reservationService.returnBook(1L, returnRequest);

        // Assert
        assertNotNull(result);
        assertEquals(Reservation.ReservationStatus.OVERDUE, result.getStatus());
        assertEquals(LocalDate.now(), result.getActualReturnDate());

        BigDecimal expectedLateFee = new BigDecimal("100.00")
                .multiply(new BigDecimal("0.15"))
                .multiply(BigDecimal.valueOf(3)); // 3 días tarde

        assertEquals(0, expectedLateFee.compareTo(result.getLateFee()));

        verify(bookService).increaseAvailableQuantity(testBook.getExternalId());
    }
    
    @Test
    void testGetReservationById_Success() {
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(testReservation));
        
        ReservationResponseDTO result = reservationService.getReservationById(1L);
        
        assertNotNull(result);
        assertEquals(testReservation.getId(), result.getId());
    }
    
//    @Test
//    void testGetAllReservations() {
//        Reservation reservation2 = new Reservation();
//        reservation2.setId(2L);
//
//        when(reservationRepository.findAll()).thenReturn(Arrays.asList(testReservation, reservation2));
//
//        List<ReservationResponseDTO> result = reservationService.getAllReservations();
//
//        assertNotNull(result);
//        assertEquals(2, result.size());
//    }
    
    @Test
    void testGetReservationsByUserId() {
        when(reservationRepository.findByUserId(1L)).thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getReservationsByUserId(1L);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetActiveReservations() {
        when(reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testReservation));
        
        List<ReservationResponseDTO> result = reservationService.getActiveReservations();
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}

