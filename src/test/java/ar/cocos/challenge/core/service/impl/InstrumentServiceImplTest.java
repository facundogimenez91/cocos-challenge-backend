package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.configuration.InstrumentProperty;
import ar.cocos.challenge.core.domain.Instrument;
import ar.cocos.challenge.core.domain.InstrumentType;
import ar.cocos.challenge.core.repository.InstrumentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceImplTest {

    @Mock
    private InstrumentRepository instrumentRepository;
    @Mock
    private InstrumentProperty instrumentProperty;
    @InjectMocks
    private InstrumentServiceImpl service;
    private Instrument pamp;
    private Instrument metr;


    @BeforeEach
    void setUp() {
        pamp = instrument(47, "PAMP", "Pampa Energía");
        metr = instrument(54, "METR", "Metrogas");
    }

    @Test
    @DisplayName("search: returns empty when query is null, blank, or shorter than 3")
    void search_emptyOnInvalidQueries() {
        StepVerifier.create(service.search(null)).verifyComplete();
        StepVerifier.create(service.search("   ")).verifyComplete();
        StepVerifier.create(service.search("ab")).verifyComplete();
        verifyNoInteractions(instrumentRepository);
    }

    @Test
    @DisplayName("search: emits results for a valid query")
    void search_emitsResults() {
        when(instrumentRepository.searchPartial(eq("pam"), anyLong()))
                .thenReturn(Flux.just(pamp));

        StepVerifier.create(service.search("pam"))
                .expectNext(pamp)
                .verifyComplete();

        verify(instrumentRepository, times(1)).searchPartial(eq("pam"), anyLong());
    }

    @Test
    @DisplayName("search: trims input; repo receives trimmed query (case preserved)")
    void search_trimsAndCasePreservedToRepo() {
        when(instrumentRepository.searchPartial(anyString(), anyLong()))
                .thenReturn(Flux.just(metr));

        StepVerifier.create(service.search("  PaM  "))
                .expectNext(metr)
                .verifyComplete();

        // Captura y valida que el repo recibió "PaM" (trim aplicado por el servicio)
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(instrumentRepository, times(1)).searchPartial(queryCaptor.capture(), anyLong());
        Assertions.assertEquals("PaM", queryCaptor.getValue());
    }

    // ===== helpers =====
    private static Instrument instrument(int id, String ticker, String name) {
        var e = new Instrument();
        e.setId(id);
        e.setTicker(ticker);
        e.setName(name);
        e.setType(InstrumentType.ACCIONES);
        return e;
    }

}