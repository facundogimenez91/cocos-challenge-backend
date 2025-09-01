package ar.cocos.challenge.core.service.impl;

import ar.cocos.challenge.configuration.InstrumentProperty;
import ar.cocos.challenge.core.domain.Instrument;
import ar.cocos.challenge.core.exception.InstrumentNotFoundException;
import ar.cocos.challenge.core.repository.InstrumentRepository;
import ar.cocos.challenge.core.service.InstrumentService;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Service for searching instruments reactively by partial query (ticker or name, minimum 3 characters).
 * <p>
 * Caching strategy: Uses Caffeine AsyncCache to store materialized search results based on the query.
 * Cache key format is {@code <lower(query)>:<limit>}, with entries expiring after 3 minutes (TTL).
 * <p>
 * The cache is thread-safe and designed for use with Spring WebFlux, storing fully materialized {@code List<InstrumentEntity>} values
 * to avoid re-subscribing to database queries. This ensures compatibility with reactive, non-blocking request handling.
 * <p>
 * Front-end integration: This service is intended for use with debounced search UIs, and only triggers searches for queries
 * with 3 or more characters. The limit is fixed at 10 results per query.
 * <p>
 * Cache HIT/MISS events are logged for observability.
 */
@Service
@Log4j2
public class InstrumentServiceImpl implements InstrumentService {

    private final InstrumentRepository instrumentRepository;
    /**
     * Async cache storing materialized {@code List<InstrumentEntity>} results for each unique search query and limit.
     * Entries expire after X minutes (TTL), with a maximum of X entries.
     * Logs cache HIT/MISS events for observability.
     */
    private final AsyncCache<String, List<Instrument>> searchCache;
    private final Long limit;

    public InstrumentServiceImpl(InstrumentRepository instrumentRepository, InstrumentProperty instrumentProperty) {
        this.instrumentRepository = instrumentRepository;
        this.limit = instrumentProperty.getLimit();
        this.searchCache = Caffeine.newBuilder()
                .maximumSize(instrumentProperty.getMaxSize())
                .expireAfterWrite(Duration.ofMinutes(instrumentProperty.getTtlMin()))
                .buildAsync();
    }

    /**
     * Performs a partial search for instruments by ticker or name.
     *
     * @param rawQuery the user input string to match against ticker or name (minimum 3 characters required)
     * @return a {@code Flux<InstrumentEntity>} of matching instruments, up to 10 results, or empty if query is null/too short
     *
     * <p>
     * - Only queries with at least 3 characters are processed; others result in an empty Flux.
     * - Result limit is fixed at 10.
     * - Results are cached per normalized query (lowercase) and limit; cache HIT/MISS events are logged.
     * - Ordering is delegated to the underlying repository query.
     * - Returns an empty Flux for null or short queries; no errors are thrown for invalid input.
     */
    @Override
    public Flux<Instrument> search(String rawQuery) {
        // Normalize query and enforce minimal length
        var query = rawQuery == null ? Strings.EMPTY : rawQuery.trim();
        if (query.length() < 3) return Flux.empty();
        var key = query.toLowerCase(Locale.ROOT) + ":" + limit;
        var listMono = Mono.fromFuture(
                searchCache.get(key, (k, executor) -> {
                    log.info("Cache MISS for search key [{}]", k);
                    return instrumentRepository.searchPartial(query, limit)
                            .collectList()
                            .toFuture();
                })
        );
        return listMono
                .doOnNext(l -> log.info("Cache HIT for search key [{}]", key))
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Retrieves an instrument by its ticker.
     *
     * @param ticker the ticker symbol of the instrument to retrieve.
     * @return a {@link Mono} emitting the {@link Instrument} if found.
     * Emits {@link InstrumentNotFoundException} if no instrument exists with the given ticker.
     */
    @Override
    public Mono<Instrument> get(String ticker) {
        return instrumentRepository.getInstrumentByTicker(ticker)
                .switchIfEmpty(Mono.error(new InstrumentNotFoundException(ticker)));

    }


}